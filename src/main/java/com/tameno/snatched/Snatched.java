package com.tameno.snatched;

import com.tameno.snatched.entity.ModEntities;
import com.tameno.snatched.entity.custom.HandSeatEntity;
import com.tameno.snatched.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Snatched implements ModInitializer {
	public static final String MOD_ID = "snatched";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		ModItems.registerModItems();
		ModEntities.registerModEntities();
		SnatcherSettings.loadSettings();

		UseEntityCallback.EVENT.register((PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) -> {

			Snatcher snatcherPlayer = (Snatcher) player;

			boolean willSnatch =
					hand == Hand.OFF_HAND &&
					player.getStackInHand(Hand.OFF_HAND).isEmpty() &&
					canSnatch(player, entity) &&
					snatcherPlayer.snatched$getCurrentHandSeat(world) == null;

			if (world.isClient()) {
				return willSnatch ? ActionResult.SUCCESS : ActionResult.PASS;
			}

			if(!willSnatch) {
				return ActionResult.PASS;
			}

			HandSeatEntity newHandSeat = new HandSeatEntity(ModEntities.HAND_SEAT, world);
			newHandSeat.setHandOwner(player);
			newHandSeat.setPosition(player.getPos());
			world.spawnEntity(newHandSeat);
			entity.startRiding(newHandSeat, true);
			snatcherPlayer.snatched$setCurrentHandSeat(newHandSeat);

			return ActionResult.SUCCESS;

		});

		UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) -> {

			if (world.isClient()) {
				return ActionResult.PASS;
			}

			Snatcher snatcherPlayer = (Snatcher) player;
			HandSeatEntity handSeat = snatcherPlayer.snatched$getCurrentHandSeat(world);

			boolean willUnSnatch = hand == Hand.OFF_HAND && handSeat != null;

			if (!willUnSnatch) {
				return ActionResult.PASS;
			}

			if (!handSeat.hasPassengers()) {
				return ActionResult.PASS; // avoid potential crash (probably)
			}

			BlockPos releasePosBlock = hitResult.getBlockPos().offset(hitResult.getSide());
			BlockState blockState = world.getBlockState(releasePosBlock);
			if (!blockState.getCollisionShape(world, releasePosBlock).isEmpty()) {
				return ActionResult.PASS;
			}
			Vec3d releasePos = releasePosBlock.toCenterPos();
			releasePos = releasePos.add(0.0, -0.5, 0.0);
			Entity snatchedEntity = handSeat.getFirstPassenger();
			snatchedEntity.dismountVehicle();
			snatchedEntity.setPosition(releasePos);

			snatcherPlayer.snatched$setCurrentHandSeat(null);

			return ActionResult.SUCCESS;

		});

		/*
		ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
			Path savePath = server.getSavePath(WorldSavePath.ROOT).resolve(Snatched.MOD_ID).normalize();
		});
		*/
	}

	private static boolean canSnatch(PlayerEntity snatcher, Entity entity) {
		if (!(entity instanceof LivingEntity)) {
			return false;
		}
		if (entity instanceof ShulkerEntity) {
			return false;
		}
		return getSize(snatcher) / getSize(entity) >= 2.0;
	}

	public static double getSize(Entity entity) {
		if (entity instanceof PlayerEntity) {
			PlayerEntity player = (PlayerEntity) entity;
			double baseHeight = player.getHeight();
			if (player.isSneaking()) {
				return baseHeight * 1.2;
			}
			if (player.isInSwimmingPose()) {
				return baseHeight * 3.0;
			}
			if (player.isFallFlying()) {
				return baseHeight * 3.0;
			}
			return baseHeight;
		}
		return entity.getHeight();
	}
}
