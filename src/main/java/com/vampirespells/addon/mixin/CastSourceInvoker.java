package com.vampirespells.addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Preserves the parent method's exact result without a compile-time parent dependency. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.CastSource", remap = false)
interface CastSourceInvoker {

    @Invoker("consumesMana")
    boolean vampireSpellsAddon$consumesMana();
}
