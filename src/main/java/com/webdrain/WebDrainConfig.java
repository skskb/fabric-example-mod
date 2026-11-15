package com.example.webdrain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class WebDrainConfig {
    public boolean webFlowingWater = false;
    public int placementsPerTick = 4; // 1-16: webs placed each round
    public int tickDelay = 0;         // 0-20: ticks between rounds (0 = every tick, fastest)

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("webdrain.json");
    public static final WebDrainConfig INSTANCE = load();

    private static WebDrainConfig load() {
        try {
            if (Files.exists(PATH)) {
                WebDrainConfig c = GSON.fromJson(Files.readString(PATH), WebDrainConfig.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) {}
        return new WebDrainConfig();
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(INSTANCE));
        } catch (Exception ignored) {}
    }
}
