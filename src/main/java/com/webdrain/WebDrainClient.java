package com.example.webdrain;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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

public class WebDrainClient implements ClientModInitializer {

    // --- Tweakables ---
    private static final double MAX_REACH = 4.0;          // how far you can place from your eyes
    private static final double PLAYER_RADIUS = 2.0;      // water this close (horizontally) to another player counts as theirs
    private static final int PLAYER_Y_BELOW = 3;          // blocks below their feet to scan
    private static final int PLAYER_Y_ABOVE = 1;          // blocks above their feet to scan
    private static final int MAX_PLACEMENTS_PER_TICK = 4; // safety cap

    private static boolean enabled = true;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.webdrain.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyBinding.Category.create(Identifier.of("webdrain", "main"))
        ));

        // START tick so the placement goes out in the very same tick the water shows up
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

        int webSlot = findCobwebHotbarSlot(self);
        if (webSlot == -1) return;

        Vec3d eye = self.getEyePos();
        int placed = 0;
        int originalSlot = self.getInventory().getSelectedSlot();
        boolean swapped = false;

        for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
            if (other == self || other.isSpectator()) continue;
            if (other.squaredDistanceTo(self) > 12 * 12) continue;

            BlockPos base = other.getBlockPos();
            int r = (int) Math.ceil(PLAYER_RADIUS);

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -PLAYER_Y_BELOW; dy <= PLAYER_Y_ABOVE; dy++) {
                        if (placed >= MAX_PLACEMENTS_PER_TICK) break;

                        BlockPos pos = base.add(dx, dy, dz);
                        FluidState fluid = client.world.getFluidState(pos);

                        // Only water source blocks. Removing the source makes the rest drain away.
                        if (!fluid.isIn(FluidTags.WATER) || !fluid.isStill()) continue;

                        // Don't web our own water
                        if (self.getBlockPos().equals(pos)) continue;

                        Vec3d center = Vec3d.ofCenter(pos);
                        if (eye.squaredDistanceTo(center) > MAX_REACH * MAX_REACH) continue;

                        if (!swapped) {
                            self.getInventory().setSelectedSlot(webSlot);
                            swapped = true;
                        }

                        // Click the water block itself; water is replaceable, so the cobweb takes its place.
                        BlockHitResult hit = new BlockHitResult(center, Direction.UP, pos, false);
                        client.interactionManager.interactBlock(self, Hand.MAIN_HAND, hit);
                        placed++;
                    }
                }
            }
        }

        // Swap straight back, all within the same tick
        if (swapped) {
            self.getInventory().setSelectedSlot(originalSlot);
            client.interactionManager.interactItem(self, Hand.OFF_HAND); // no-op safeguard removed below
        }
    }

    private static int findCobwebHotbarSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.COBWEB)) return i;
        }
        return -1;
    }
}
