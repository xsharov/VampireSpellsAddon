package com.vampirespells.addon.mixin;

import com.vampirespells.addon.event.BloodCastHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell", remap = false)
abstract class AbstractSpellMixin {

    @Redirect(
            method = "canBeCastedBy",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/CastSource;consumesMana()Z",
                    remap = false
            ),
            remap = false
    )
    private boolean vampireSpellsAddon$replaceManaRequirement(
            @Coerce Object invokedCastSource,
            int spellLevel,
            @Coerce Object castSource,
            @Coerce Object playerMagicData,
            Player player
    ) {
        return ((CastSourceInvoker) invokedCastSource).vampireSpellsAddon$consumesMana()
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
            Level world,
            int spellLevel,
            ServerPlayer player,
            @Coerce Object castSource,
            boolean triggerCooldown,
            CallbackInfo callback
    ) {
        if (BloodCastHooks.consumeDeniedCast(player, this)) {
            callback.cancel();
        }
    }
}
