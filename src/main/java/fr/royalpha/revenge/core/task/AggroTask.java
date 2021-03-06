package fr.royalpha.revenge.core.task;

import fr.royalpha.revenge.core.RevengePlugin;
import fr.royalpha.revenge.core.handler.Mob;
import fr.royalpha.revenge.core.handler.Particles;
import fr.royalpha.revenge.core.hook.base.HookManager;
import fr.royalpha.revenge.core.hook.base.Hooks;
import fr.royalpha.revenge.core.legacy.LegacyUtils;
import fr.royalpha.revenge.core.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import fr.royalpha.revenge.core.util.MathUtils;

public class AggroTask extends BukkitRunnable {

	private Entity killer;
	private Mob mob;
	private Entity victim;
	private Integer stopTicks, intervalTicks, walkUpdateLimitTicks;
	private Double bloodTicks;
	private Boolean countStopTicks, countIntervalTicks, enableParticles;
	private Location lastAskedLocation;
	private RevengePlugin instance;
	private ArmorStand trackedInfo;

	public AggroTask(Entity ent, Mob m, Entity victim, RevengePlugin instance) {
		if (m == null || ent == victim || ent.isDead())
			return;
		this.killer = ent;
		this.mob = m;
		this.victim = victim;
		this.stopTicks = 0;
		this.intervalTicks = 0;
		this.walkUpdateLimitTicks = 0;
		this.bloodTicks = 0.0D;
		this.countStopTicks = this.mob.getStopTimeTicks() != 0;
		this.countIntervalTicks = this.mob.getDamageIntervalTicks() != 0.0D;
		this.enableParticles = instance.revengeParticle != null;
		this.instance = instance;
		if ((instance.isHooked(Hooks.PVPManager)) && (victim instanceof Player))
			if (instance.getHook(Hooks.PVPManager).get(HookManager.Getter.BOOLEAN_PLAYER_HAS_PVP_DISABLED, victim))
				return;
		if ((instance.isHooked(Hooks.VanishNoPacket)) && (victim instanceof Player))
			if (instance.getHook(Hooks.VanishNoPacket).get(HookManager.Getter.BOOLEAN_PLAYER_IS_VANISHED, victim)) {
				if (!instance.globalRevenge)
					victim.sendMessage(ChatColor.DARK_AQUA + "You're vanished. This entity can't see you.");
				return;
			}
		this.killer.setMetadata(this.instance.revengeMobMetadata, new FixedMetadataValue(this.instance, true));
		m.getList().add(this);
		RevengePlugin.getVersionManager().getParticleFactory().playParticles(Particles.VILLAGER_ANGRY, ent.getLocation().add(0, 1, 0), 0.0F, 0.0F, 0.0F, 1, 0.1F);
		runTaskTimer(instance, 0L, 0L);
	}

	public void run() {
		if (this.countIntervalTicks)
			this.intervalTicks += 1;
		if (this.countStopTicks)
			this.stopTicks += 1;
		if (this.instance.randomBehavior)
			this.walkUpdateLimitTicks += 1;
		if (this.instance.angryMood)
			RevengePlugin.getVersionManager().getNMSUtils().playAnimation(this.killer, 1);
		if (this.instance.animalsBlood && this.bloodTicks > 0) {
			this.bloodTicks -= 1;
			this.instance.revengeParticle.playBloodParticles(((LivingEntity) this.killer).getEyeLocation());
		}
		if (!this.killer.isDead() && !this.victim.isDead() && this.stopTicks <= this.mob.getStopTimeTicks()
				&& this.victim.getWorld() == this.killer.getWorld()
				&& this.victim.getLocation().distance(this.killer.getLocation()) < this.mob.getStopBlocks()
				&& this.victim.getWorld() == this.killer.getWorld()) {
			if (this.victim.getLocation().distance(this.killer.getLocation()) < this.mob.getHitRadius()) {
				if (this.countIntervalTicks && this.intervalTicks < this.mob.getDamageIntervalTicks())
					return;
				damage();
			}
			if (this.instance.trackedInfoEnabled) {
				if (this.trackedInfo == null) {
					this.trackedInfo = (ArmorStand) this.killer.getWorld().spawnEntity(MathUtils.getLeftBackSide(this.killer.getLocation(), 1.5D), EntityType.valueOf("ARMOR_STAND"));
					this.trackedInfo.setVisible(false);
					RevengePlugin.getVersionManager().getNMSUtils().setGravity(this.trackedInfo, false);
					this.trackedInfo.setCustomNameVisible(false);
					this.trackedInfo.setMetadata(instance.revengeTrackedInfoMetadata, new FixedMetadataValue(instance, true));
					String trackedInfoDescription = instance.trackedDescription;
					if (this.victim instanceof Player) {
						trackedInfoDescription = trackedInfoDescription.replaceAll("%PLAYER%", this.victim.getName());
						this.trackedInfo.setHelmet(new ItemBuilder().setSkullOwner(this.victim.getName()).toItemStack());
					}
					this.trackedInfo.setCustomName(trackedInfoDescription);
					this.trackedInfo.setCustomNameVisible(true);
				}
				this.trackedInfo.teleport(MathUtils.getLeftBackSide(this.killer.getLocation(), 1.5D));
			}
			Vector v = this.victim.getLocation().subtract(this.killer.getLocation()).toVector().normalize();
			this.killer.getLocation().setDirection(v);
			if (LegacyUtils.isMarineAnimal(this.killer.getType())) {
				Material mat = this.killer.getLocation().getBlock().getType();
				if (LegacyUtils.isWater(mat) && this.victim.getLocation().distance(this.killer.getLocation()) > this.mob.getHitRadius()) {
					RevengePlugin.getVersionManager().getParticleFactory().playParticles(Particles.WATER_BUBBLE, this.killer.getLocation(),
							0.3F, 0.3F, 0.3F, 3, 0.1F);
					this.killer.setVelocity(v.multiply(this.mob.getSpeed()));
				}
			} else if (this.killer.getType() == EntityType.BAT) {
				Material mat = this.killer.getLocation().getBlock().getType();
				if ((mat == Material.AIR)
						&& this.victim.getLocation().distance(this.killer.getLocation()) > this.mob.getHitRadius())
					this.killer.setVelocity(v.multiply(this.mob.getSpeed()));
			} else {
				this.walkToLocation(this.victim.getLocation(), this.mob.getSpeed());
			}
			return;
		}
		down();
	}

	private void damage() {
		this.victim.setMetadata(this.instance.lastDamagerMetadata, new FixedMetadataValue(this.instance, this.mob.getEntity().name()));
		RevengePlugin.getVersionManager().getNMSUtils().damage(this.victim, this.killer, this.mob.getDamage());
		if (enableParticles)
			this.instance.revengeParticle.playParticles(this.victim.getLocation());
		if (this.countIntervalTicks)
			this.intervalTicks = 0;
	}

	public void down() {
		cancel();
		if (this.instance.trackedInfoEnabled && this.trackedInfo != null)
			this.trackedInfo.remove();
		this.killer.removeMetadata(this.instance.revengeMobMetadata, this.instance);
		RevengePlugin.getVersionManager().getNMSUtils().walkTo(this.killer, this.killer.getLocation(), this.mob.getSpeed());
		if (this.mob.getList().contains(this)) {
			this.mob.getList().remove(this);
		}
	}
	
	public void walkToLocation(Location location, Double speed) {
		if (this.instance.randomBehavior) {
			if (this.lastAskedLocation != null) {
				Location pos1 = this.killer.getLocation();
				pos1.setY(0.0D);
				Location pos2 = this.lastAskedLocation;
				pos2.setY(0.0D);
				Location pos3 = this.victim.getLocation();
				pos3.setY(0.0D);
				if (pos1.distance(pos2) > 1 && this.walkUpdateLimitTicks < 160 && pos1.distance(pos2) < pos1.distance(pos3))
					return;
			}
			this.walkUpdateLimitTicks = 0;
			double distance = location.distance(this.killer.getLocation());
			if (distance <= 50.0D) {
				// Speed Modifier
				speed += MathUtils.random(1.5f);

				// Accuracy Modifier
				float multiplier = (float) (distance / 50);
				float range = 10 * multiplier;
				float randX = MathUtils.random(range * 2) - range;
				float randZ = MathUtils.random(range * 2) - range;
				location = location.add(randX, 0, randZ);
			}
		}
		this.lastAskedLocation = location;
		RevengePlugin.getVersionManager().getNMSUtils().walkTo(this.killer, location, speed);
	}

	public Entity getKiller() {
		return this.killer;
	}

	public Entity getVictim() {
		return this.victim;
	}
	
	public void resetBloodAnimation(Double damage)
	{
		this.bloodTicks = damage*20;
	}
}