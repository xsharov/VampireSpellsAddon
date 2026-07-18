package com.vampirespells.addon.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vampirespells.addon.integration.VampirismBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Reverses the animated flow of Ray of Siphoning for vampire casters.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.render.SpellRenderingHelper", remap = false)
public abstract class SpellRenderingHelperMixin {

    @ModifyVariable(
            method = "renderRayOfSiphoning",
            at = @At("STORE"),
            ordinal = 4,
            require = 1
    )
    private static float vampireSpellsAddon$reverseVampireRayFlow(
            float deltaUv,
            LivingEntity entity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float partialTicks
    ) {
        if (entity instanceof Player player && VampirismBridge.vampire(player).isVampire()) {
            return -deltaUv;
        }
        return deltaUv;
    }
}
