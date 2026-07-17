package com.vampirespells.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.vampirespells.addon.event.BloodCastHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell", remap = false)
abstract class AbstractSpellMixin {

    @ModifyExpressionValue(
            method = "canBeCastedBy",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/CastSource;consumesMana()Z",
                    remap = false
            ),
            remap = false
    )
    private boolean vampireSpellsAddon$replaceManaRequirement(
            boolean consumesMana,
            @Local(argsOnly = true) int spellLevel,
            @Local(argsOnly = true) Player player
    ) {
        return consumesMana
                && !BloodCastHooks.shouldBypassManaRequirement(this, spellLevel, player);
    }

    @Inject(
            method = "castSpell",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            cancellable = true,
            remap = false
    )
    private void vampireSpellsAddon$stopUnpaidBloodCast(
            CallbackInfo callback,
            @Local(argsOnly = true) ServerPlayer player
    ) {
        if (BloodCastHooks.consumeDeniedCast(player, this)) {
            callback.cancel();
        }
    }
}
