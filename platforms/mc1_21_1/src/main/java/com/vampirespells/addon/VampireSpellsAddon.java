package com.vampirespells.addon;

import com.vampirespells.addon.config.AddonConfig;
import com.vampirespells.addon.event.SpellEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VampireSpellsAddon.MOD_ID)
public class VampireSpellsAddon {
    public static final String MOD_ID = "vampire_spells_addon";
    public static final Logger LOGGER = LoggerFactory.getLogger(VampireSpellsAddon.class);

    public VampireSpellsAddon(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Vampire Spells Addon for Minecraft 1.21.1...");

        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                (ModConfigSpec) AddonConfig.SPEC,
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
