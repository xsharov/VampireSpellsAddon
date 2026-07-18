package com.vampirespells.addon;

import com.vampirespells.addon.config.AddonConfig;
import com.vampirespells.addon.event.SpellEventHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VampireSpellsAddon.MOD_ID)
public class VampireSpellsAddon {
    public static final String MOD_ID = "vampire_spells_addon";
    public static final Logger LOGGER = LoggerFactory.getLogger(VampireSpellsAddon.class);

    @SuppressWarnings("removal")
    public VampireSpellsAddon() {
        LOGGER.info("Initializing Vampire Spells Addon for Minecraft 1.20.1...");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                (ForgeConfigSpec) AddonConfig.SPEC,
                MOD_ID + "-server.toml"
        );
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(VampireSpellsAddon::registerIntegration);
    }

    private static void registerIntegration() {
        if (SpellEventHandler.register()) {
            LOGGER.info("Vampirism and Iron's Spells integration contracts validated successfully");
        } else {
            LOGGER.error("Required parent-mod contracts are unavailable; addon mechanics are disabled");
        }
    }
}
