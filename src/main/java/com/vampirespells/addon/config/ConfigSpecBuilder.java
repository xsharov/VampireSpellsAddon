package com.vampirespells.addon.config;

import java.util.function.Supplier;

/** Loader-neutral surface used to define the server config once for both targets. */
interface ConfigSpecBuilder {

    ConfigSpecBuilder comment(String... lines);

    ConfigSpecBuilder push(String section);

    Supplier<Integer> defineInRange(String key, int defaultValue, int minimum, int maximum);

    Supplier<Double> defineInRange(String key, double defaultValue, double minimum, double maximum);

    Supplier<Boolean> define(String key, boolean defaultValue);

    ConfigSpecBuilder pop();

    Object build();
}
