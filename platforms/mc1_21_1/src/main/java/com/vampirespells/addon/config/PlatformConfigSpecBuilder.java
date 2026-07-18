package com.vampirespells.addon.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

final class PlatformConfigSpecBuilder implements ConfigSpecBuilder {

    private final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    private PlatformConfigSpecBuilder() {
    }

    static ConfigSpecBuilder create() {
        return new PlatformConfigSpecBuilder();
    }

    @Override
    public ConfigSpecBuilder comment(String... lines) {
        builder.comment(lines);
        return this;
    }

    @Override
    public ConfigSpecBuilder push(String section) {
        builder.push(section);
        return this;
    }

    @Override
    public Supplier<Integer> defineInRange(String key, int defaultValue, int minimum, int maximum) {
        return builder.defineInRange(key, defaultValue, minimum, maximum);
    }

    @Override
    public Supplier<Double> defineInRange(String key, double defaultValue, double minimum, double maximum) {
        return builder.defineInRange(key, defaultValue, minimum, maximum);
    }

    @Override
    public Supplier<Boolean> define(String key, boolean defaultValue) {
        return builder.define(key, defaultValue);
    }

    @Override
    public ConfigSpecBuilder pop() {
        builder.pop();
        return this;
    }

    @Override
    public Object build() {
        return builder.build();
    }
}
