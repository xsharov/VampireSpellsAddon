package com.vampirespells.addon;

import com.vampirespells.addon.config.AddonConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("vampire_spells_addon")
public class VampireSpellsAddon {
    public static final String MOD_ID = "vampire_spells_addon";
    public static final Logger LOGGER = LoggerFactory.getLogger(VampireSpellsAddon.class);

    public VampireSpellsAddon(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Vampire Spells Addon...");

        modContainer.registerConfig(ModConfig.Type.SERVER, AddonConfig.SPEC, MOD_ID + "-server.toml");

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Vampire Spells Addon common setup complete!");

        event.enqueueWork(() -> {
            try {
                Class.forName("de.teamlapen.vampirism.api.VampirismAPI");
                LOGGER.info("Vampirism API detected successfully");
            } catch (ClassNotFoundException e) {
                LOGGER.error("Vampirism API not found! This addon requires Vampirism to be installed.");
            }

            try {
                Class.forName("io.redspace.ironsspellbooks.api.spells.ISpell");
                LOGGER.info("Iron's Spells API detected successfully");
            } catch (ClassNotFoundException e) {
                LOGGER.error("Iron's Spells API not found! This addon requires Iron's Spells 'n Spellbooks to be installed.");
            }
        });
    }
}
