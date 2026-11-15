package com.webdrain;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class WebDrainConfigScreen extends Screen {
    private final Screen parent;

    public WebDrainConfigScreen(Screen parent) {
        super(Text.literal("Auto Web Drain"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        WebDrainConfig cfg = WebDrainConfig.INSTANCE;
        int x = this.width / 2 - 100;
        int y = this.height / 4;

        addDrawableChild(ButtonWidget.builder(flowingText(cfg), b -> {
            cfg.webFlowingWater = !cfg.webFlowingWater;
            b.setMessage(flowingText(cfg));
        }).dimensions(x, y, 200, 20).build());

        addDrawableChild(new IntSlider(x, y + 28, 200, 20, "Webs per round", 1, 16,
                cfg.placementsPerTick, v -> cfg.placementsPerTick = v));

        addDrawableChild(new IntSlider(x, y + 56, 200, 20, "Delay (ticks)", 0, 20,
                cfg.tickDelay, v -> cfg.tickDelay = v));

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x, y + 96, 200, 20).build());
    }

    private static Text flowingText(WebDrainConfig cfg) {
        return Text.literal("Web flowing water: " + (cfg.webFlowingWater ? "ON" : "OFF"));
    }

    @Override
    public void close() {
        WebDrainConfig.save();
        this.client.setScreen(parent);
    }

    private static class IntSlider extends SliderWidget {
        private final String label;
        private final int min, max;
        private final Consumer<Integer> setter;

        IntSlider(int x, int y, int w, int h, String label, int min, int max,
                  int initial, Consumer<Integer> setter) {
            super(x, y, w, h, Text.empty(), (double) (initial - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
