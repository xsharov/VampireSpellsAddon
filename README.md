# Vampire Spells Addon

NeoForge addon that bridges **Iron's Spells 'n Spellbooks** and **Vampirism**, reshaping spell behaviour for vampires via reflection-only hooks.

## Key Features

- **Blood-Magic Integration** – Eight blood spells drain Vampirism blood instead of mana when vampires are above the configurable threshold, with dynamic cooldown scaling when blood is low.
- **Ray & Devour Tweaks** – Ray of Siphoning keeps its damage while restoring blood; Devour restores configurable bonus blood and consumes extra mana.
- **Holy Backlash** – Holy damage reflects onto vampire casters and deals double damage to Vampirism NPC vampires. Holy heals injure vampire casters/targets (healing suppressed), while holy utility spells (`angel_wing`, `sunbeam`, `fortify`, `wisp`, `haste`, `cleanse`) simply deal 5 damage and fizzle.
- **Reflection-Based Compatibility** – No compile-time links to either parent mod; everything is discovered at runtime, keeping jars lightweight and conflict-free.
- **Server Config** – `vampire_spells_addon-server.toml` exposes blood ratios, thresholds, multipliers, and holy backlash tuning.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- [Vampirism 1.10.7+](https://www.curseforge.com/minecraft/mc-mods/vampirism-become-a-vampire)
- [Iron's Spells 'n Spellbooks 1.21.1-3.14.3+](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)

## Build & Run

```bash
./gradlew build              # produces build/libs/vampire_spells_addon-neoforge-<mc>-<mod>.jar
./gradlew runClient          # launches dev client
./gradlew runData            # runs data generators
./gradlew runGameTestServer  # executes game tests (if present)
```

## Project Layout

```
vampire-spells-addon/
├── src/main/java/com/vampirespells/addon/
│   ├── VampireSpellsAddon.java          # Mod entry point + config registration
│   └── event/SpellEventHandler.java     # Reflection hooks into spell logic
├── src/main/resources/META-INF/neoforge.mods.toml
├── src/generated/resources/             # Optional data generators
├── build.gradle                         # NeoForge / Gradle setup
├── gradle.properties                    # Version + metadata (author: xsharov)
└── README.md                            # You are here
```

## License

MIT. Respect the original licenses for Iron's Spells 'n Spellbooks and Vampirism when bundling or redistributing assets.
