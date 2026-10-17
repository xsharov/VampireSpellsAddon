package com.vampirespells.addon.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlatformConfigSpecBuilder implements ConfigSpecBuilder {

    private final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

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
    public Supplier<List<? extends String>> defineStringList(
            String key,
            List<String> defaultValue,
            Predicate<Object> elementValidator
    ) {
        return builder.defineListAllowEmpty(key, defaultValue, elementValidator);
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
