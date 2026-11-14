package com.example.webdrain;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebDrainClient implements ClientModInitializer {

    // --- Tweakables ---
    private static final double MAX_REACH = 4.0;           // max distance from your eyes
    private static final int PLAYER_RADIUS = 4;            // horizontal scan radius around other players
    private static final int PLAYER_Y_BELOW = 4;           // blocks below their feet to scan
    private static final int PLAYER_Y_ABOVE = 2;           // blocks above their feet to scan
    private static final int MAX_PLACEMENTS_PER_TICK = 16; // raised so multiple buckets get handled at once
    private static final double MAX_PLAYER_DISTANCE = 12.0;

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

                        // Only plain water blocks. Skips kelp, seagrass and waterlogged blocks,
                        // which a cobweb can't replace and which would waste placements.
                        if (!client.world.getBlockState(pos).isOf(Blocks.WATER)) continue;

                        FluidState fluid = client.world.getFluidState(pos);
                        if (!fluid.isIn(FluidTags.WATER)) continue;

                        if (self.getBlockPos().equals(pos)) continue; // not our own water

                        if (eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_REACH * MAX_REACH) continue;

                        if (fluid.isStill()) sources.add(pos);
                        else flowing.add(pos);
                    }
                }
            }
        }

        if (sources.isEmpty() && flowing.isEmpty()) return;

        // Sources first, then flowing water that keeps refilling the area
        List<BlockPos> targets = new ArrayList<>(sources);
        targets.addAll(flowing);

        int originalSlot = self.getInventory().getSelectedSlot();
        boolean swapped = false;
        int placed = 0;

        for (BlockPos pos : targets) {
            if (placed >= MAX_PLACEMENTS_PER_TICK) break;

            // Re-pick a stack each time in case one ran out mid-tick
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
            client.interactionManager.tick(); // syncs the selected slot to the server right away
        }
    }

    private static int findCobwebHotbarSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.COBWEB)) return i;
        }
        return -1;
    }
}
