# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Vampire Spells Addon** is a completed Minecraft mod that integrates Iron's Spells 'n Spellbooks magic system with Vampirism faction mechanics. The mod modifies spell behavior for vampire players, specifically making the Ray of Siphoning spell restore blood instead of dealing damage.

- **Status**: ✅ **COMPLETED AND FULLY FUNCTIONAL**
- **Package**: `com.vampirespells.addon`
- **Version**: 0.0.1a
- **Architecture**: Reflection-based runtime integration (no compile-time dependencies)
- **Final JAR**: `VampireSpellsAddon.jar` (6,129 bytes)

## Development Commands

```bash
# Build the addon
./gradlew build

# Run client with addon for testing
./gradlew runClient

# Generate data files
./gradlew runData

# Run game tests
./gradlew runGameTestServer

# Clean build
./gradlew clean build --no-daemon

# Copy final JAR to root (production build)
cp build/libs/vampire_spells_addon-0.0.1a.jar ./VampireSpellsAddon.jar
```

## Code Architecture

### Core Implementation
- **Main Class**: `VampireSpellsAddon.java` - NeoForge mod entry point with dependency verification
- **Event Handler**: `SpellEventHandler.java` - Intercepts damage events and modifies vampire spell behavior
- **Reflection-Based**: All parent mod API access uses Java reflection to avoid package conflicts

### Key Features
- **Ray of Siphoning Modification**: Converts damage to blood restoration for vampire casters
- **Runtime Detection**: Uses reflection to identify vampire players and spell types
- **Universal Compatibility**: Works with any target type (vampire or non-vampire)
- **Comprehensive Logging**: SLF4J logging for debugging and monitoring

### Technical Approach
The addon uses Java reflection exclusively to access parent mod APIs:
- **Vampirism API**: Dynamic access to vampire player detection and blood system
- **Iron's Spells API**: Dynamic spell identification via ResourceLocation
- **NeoForge Events**: Subscribes to `LivingDamageEvent.Pre` for damage interception

## Build Configuration

### Critical Dependencies Rule
**NEVER add compile-time dependencies to parent mods in build.gradle**

Current minimal dependencies (correct approach):
```gradle
dependencies {
    implementation "net.neoforged:neoforge:${neo_version}"
    // No compile-time parent mod dependencies
}
```

### Runtime Dependencies
- Vampirism 1.10.7+
- Iron's Spells 'n Spellbooks 1.21.1-3.14.3+
- Minecraft 1.21.1
- NeoForge 21.1.125+
- Java 21+

## Development Guidelines

### Package Conflict Prevention
1. **NO Compile-Time Dependencies**: Never add parent mod dependencies to build.gradle
2. **Use Reflection Only**: All parent mod API access must use Java reflection
3. **Avoid Package Conflicts**: Never create classes in `io.redspace.*`, `de.teamlapen.*`, or `net.neoforged.*` packages
4. **Verify JAR Contents**: Always check final JAR contains only addon classes using `jar -tf VampireSpellsAddon.jar`

### Common Pitfalls to Avoid
- **Module Conflicts**: Adding stub classes causes "Modules export package" errors
- **Java Version Issues**: Must use Java 21 for NeoForge 1.21.1 compatibility
- **Event Handler Errors**: Ensure proper event type and registration
- **Missing Runtime Checks**: Always verify parent mods are present before using reflection

### Testing Checklist
- [ ] JAR builds without errors
- [ ] JAR contains only addon classes (no parent mod packages)
- [ ] Game launches without module conflicts
- [ ] Ray of Siphoning works for vampire players (restores blood)
- [ ] Ray of Siphoning works normally for non-vampire players
- [ ] Console shows appropriate logging messages

## Debugging

### JAR Content Verification
```bash
jar -tf VampireSpellsAddon.jar
# Should only show:
# META-INF/MANIFEST.MF
# META-INF/neoforge.mods.toml
# com/vampirespells/addon/VampireSpellsAddon.class
# com/vampirespells/addon/event/SpellEventHandler.class
```

### Common Build Issues
1. **UnsupportedClassVersionError**: Change `java.toolchain.languageVersion` to 21
2. **Module Resolution Errors**: Remove any parent mod stub classes from src/
3. **Event Registration Errors**: Ensure event handler methods have @SubscribeEvent annotations

### Log Monitoring
The addon logs key events at INFO level:
- Vampire player spell casting detection
- Blood restoration amount
- Reflection method success/failure
- Dependency verification results