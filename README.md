# Vampire Spells Addon

An addon mod that combines the magic system from **Iron's Spells 'n Spellbooks** with the faction system from **Vampirism**, creating faction-specific spellcasting mechanics.

## Features

### Implemented
- **Ray of Siphoning Modification**: When cast by vampire players, this spell restores blood instead of dealing damage
- **Automatic Vampire Detection**: Uses reflection to detect vampire players at runtime
- **Universal Compatibility**: Works with any target type while maintaining normal behavior for non-vampires

### Planned
- **Blood Spell Branch Support**: Expanding blood magic integration to support other spells from the blood spell branch
- **Hunter Magic**: Holy and protective spells exclusive to hunters
- **Faction Integration**: Spell access based on Vampirism faction progression
- **Blood-Powered Casting**: Alternative mana system using Vampirism's blood mechanics
- **Progression Synergy**: Vampire/Hunter levels unlock new magical abilities

## Dependencies

This addon requires both parent mods to function:

- [Vampirism](https://www.curseforge.com/minecraft/mc-mods/vampirism-become-a-vampire) - Provides faction system and blood mechanics
- [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) - Provides spellcasting foundation

## Development

### Building
```bash
./gradlew build
```

### Running Client
```bash
./gradlew runClient
```

### Data Generation
```bash
./gradlew runData
```

## Project Structure

```
vampire-spells-addon/
├── src/main/java/com/vampirespells/addon/
│   └── VampireSpellsAddon.java          # Main mod class
├── src/main/resources/
│   └── META-INF/neoforge.mods.toml      # Mod metadata
├── src/generated/resources/             # Generated data files
├── build.gradle                         # Build configuration
├── gradle.properties                    # Mod properties and versions
└── settings.gradle                      # Gradle settings
```

## License

MIT License - See parent mod licenses for additional restrictions on their respective assets.