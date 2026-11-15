package com.webdrain;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebDrainClient implements ClientModInitializer {

    // --- Tweakables ---
    private static final double MAX_REACH = 4.0;
    private static final int PLAYER_RADIUS = 4;
    private static final int PLAYER_Y_BELOW = 4;
    private static final int PLAYER_Y_ABOVE = 2;
    private static final double MAX_PLAYER_DISTANCE = 12.0;
    private static final double SAFE_RADIUS = 2.0; // water this close to you is protected (if enabled)

    private static boolean enabled = true;
    private static int cooldown = 0;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.webdrain.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyBinding.Category.create(Identifier.of("webdrain", "main"))
        ));

        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("Auto Web Drain: " + (enabled ? "ON" : "OFF")), true);
            }
        }

        if (!enabled || client.world == null || client.player == null
                || client.interactionManager == null || client.currentScreen != null) {
            return;
        }

        ClientPlayerEntity self = client.player;
        if (findCobwebHotbarSlot(self) == -1) return;

        WebDrainConfig cfg = WebDrainConfig.INSTANCE;
        if (cooldown > 0) { cooldown--; return; }

        Vec3d eye = self.getEyePos();
        List<BlockPos> sources = new ArrayList<>();
        List<BlockPos> flowing = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();

        for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
            if (other == self || other.isSpectator()) continue;
            if (other.squaredDistanceTo(self) > MAX_PLAYER_DISTANCE * MAX_PLAYER_DISTANCE) continue;

            BlockPos base = other.getBlockPos();

            for (int dx = -PLAYER_RADIUS; dx <= PLAYER_RADIUS; dx++) {
                for (int dz = -PLAYER_RADIUS; dz <= PLAYER_RADIUS; dz++) {
                    for (int dy = -PLAYER_Y_BELOW; dy <= PLAYER_Y_ABOVE; dy++) {
                        BlockPos pos = base.add(dx, dy, dz);
                        if (!seen.add(pos)) continue;

                        // Plain water only (skips kelp, seagrass, waterlogged blocks)
                        if (!client.world.getBlockState(pos).isOf(Blocks.WATER)) continue;

                        FluidState fluid = client.world.getFluidState(pos);
                        if (!fluid.isIn(FluidTags.WATER)) continue;

                        Vec3d center = Vec3d.ofCenter(pos);

                        // Optional safe zone around you
                        double selfDist = distToPlayer(center, self);
                        if (cfg.safeRadiusEnabled && selfDist <= SAFE_RADIUS) continue;

                        // Only web water that is closer to another player than to you
                        double nearestOther = Double.MAX_VALUE;
                        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
                            if (p == self || p.isSpectator()) continue;
                            nearestOther = Math.min(nearestOther, distToPlayer(center, p));
                        }
                        if (selfDist <= nearestOther) continue;

                        if (eye.squaredDistanceTo(center) > MAX_REACH * MAX_REACH) continue;

                        if (fluid.isStill()) sources.add(pos);
                        else if (cfg.webFlowingWater) flowing.add(pos);
                    }
                }
            }
        }

        if (sources.isEmpty() && flowing.isEmpty()) return;

        List<BlockPos> targets = new ArrayList<>(sources);
        targets.addAll(flowing);

        int originalSlot = self.getInventory().getSelectedSlot();
        boolean swapped = false;
        int placed = 0;

        for (BlockPos pos : targets) {
            if (placed >= cfg.placementsPerTick) break;

            int webSlot = findCobwebHotbarSlot(self);
            if (webSlot == -1) break;

            if (self.getInventory().getSelectedSlot() != webSlot) {
                self.getInventory().setSelectedSlot(webSlot);
            }
            swapped = true;

            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            client.interactionManager.interactBlock(self, Hand.MAIN_HAND, hit);
            placed++;
        }

        if (swapped) {
            self.getInventory().setSelectedSlot(originalSlot);
            client.interactionManager.tick();
            cooldown = cfg.tickDelay;
        }
    }

    private static int findCobwebHotbarSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.COBWEB)) return i;
        }
        return -1;
    }

    // Distance from a point to the player's feet or eyes, whichever is closer
    private static double distToPlayer(Vec3d point, PlayerEntity p) {
        Vec3d feet = new Vec3d(p.getX(), p.getY(), p.getZ());
        return Math.min(point.distanceTo(feet), point.distanceTo(p.getEyePos()));
    }
}
