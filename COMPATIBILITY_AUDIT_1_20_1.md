# Compatibility Audit: Minecraft 1.20.1

This document records the source-level review behind the Forge 1.20.1 target.
The ignored clones under `dependency-source/` and the exact upstream release
tags were used to verify signatures, event emission sites, loader requirements,
and mixin targets. This is not a substitute for gameplay testing.

## Platform decision

Minecraft 1.20.1 uses Forge, not NeoForge. Iron's Spells releases supported by
this addon declare Forge `47.4.0` as their minimum:

- `v1.20.1-3.15.0`, commit `86f6cc7365ae42e6fccea615506f7bb8bcc83d64`.
- `v1.20.1-3.16.2`, commit `a136b3ad66f066ca0951dd37b92926e90c81c70d`.

Both tags declare `forge_version=47.4.0` and
`forge_version_range=[47.4.0,)` in their `gradle.properties`. The published
NeoForge 1.20.1 line stopped before the 47.4 loader baseline, so a NeoForge
artifact could not load the supported Iron's Spells stack. The addon therefore
uses Forge `47.4.10` for development and advertises `[47.4.0,48)`.

## Audited versions

| Component | Compatibility floor | Development reference |
| --- | --- | --- |
| Minecraft | `1.20.1` | `1.20.1` |
| Java | 17 | 17 |
| Forge | `47.4.0` | `47.4.10` |
| Vampirism | `1.20.1-1.10.7` | `1.20.1-1.10.16` |
| Iron's Spells | `1.20.1-3.15.0` | `1.20.1-3.16.2` |
| Iron's Lib | not required by the floor | `1.20.1-2.1.0-SNAPSHOT` |

The current Vampirism 1.20.1 reference branch is
`version/1.20/1.20.1/latest` at commit
`2ad80f8c215354db8f76f799d858f4884d0515a1`. Runtime Maven coordinates and
all transitive development-library versions are centralized in
`gradle/targets/mc1_20_1.properties`; this audit must not become a second
configuration source.

## Build architecture

The 1.20.1 artifact is a platform project, not a fork of the addon:

- shared gameplay behavior, state, reflection bridges, tests, resources, and
  the client Ray mixin remain under `src/`;
- Forge entry-point, event/config adapters, metadata, mixin descriptors, and
  the loader-specific `AbstractSpellMixin` live under `platforms/mc1_20_1`;
- parent mods are Forge runtime dependencies only and do not extend the shared
  compile classpath;
- build plugins are pinned to ForgeGradle `6.0.54`, MixinGradle `0.7.38`, and
  Librarian `1.2.0` so CI does not silently select a newer toolchain;
- the JAR must use Java class-file major 61 and contain `META-INF/mods.toml`,
  never `neoforge.mods.toml` or bundled parent/loader classes.

The canonical GitHub release tag remains `1.21.1-X.Y.Z`. The 1.20.1 artifact
uses the neutral suffix and is named
`vampire_spells_addon-forge-1.20.1-X.Y.Z.jar`. It is published beside the
matching NeoForge 1.21.1 JAR; neither target may publish alone.

## Verified parent contracts

The Iron's Spells 3.15.0 floor and 3.16.2 reference preserve the parent events
used by shared code:

- `SpellPreCastEvent`, `SpellOnCastEvent`, `SpellCooldownAddedEvent.Pre`,
  `SpellDamageEvent`, and `SpellHealEvent`;
- spell, school, mana, cooldown, cast-source, damage-source, recast, and
  cancellation accessors listed in `AGENTS.md`;
- the single `CastSource#consumesMana()` call in
  `AbstractSpell#canBeCastedBy`;
- the `MinecraftForge.EVENT_BUS.post(SpellOnCastEvent)` call in
  `AbstractSpell#castSpell`, before mana debit and spell effect;
- the client Ray-of-Siphoning `deltaUV` local used by the animation mixin.

Vampirism 1.20.1 differs from the 1.21.1 API at the player lookup boundary.
`VampirismAPI#getVampirePlayer(Player)` returns Forge
`LazyOptional<IVampirePlayer>`. The shared bridge resolves and unwraps that
type reflectively, while retaining the direct
`VampirismAPI#vampirePlayer(Player)` path used on 1.21.1. An empty capability
must behave as a non-vampire and must not produce repeated warnings.

Forge event registration uses the same four-argument listener contract needed
for reflective parent events. Loader lifecycle listeners remain in the Forge
platform adapter, so no `net.minecraftforge.*` import is introduced into shared
gameplay sources.

## Delivered-damage semantic difference

NeoForge 1.21.1 provides paired `LivingDamageEvent.Pre` and `.Post` events.
Forge 1.20.1 exposes one `LivingDamageEvent` after armor, enchantment, and
absorption processing but before the entity health field is changed. The Forge
adapter observes it at `LOWEST` priority and passes its final visible amount to
shared spell handling, capped to the target's current health for lethal
overkill.

This is the closest Forge 1.20.1 equivalent to the NeoForge Post contract, but
it is not identical. A third-party handler ordered after the addon can still
rewrite or cancel the event. Runtime coverage must include armor, absorption,
event cancellation, nested damage, lethal overkill, and both Ray and Devour.

## Verification matrix

Required automated checks for this platform are:

```bash
./gradlew :platforms:mc1_20_1:test \
  :platforms:mc1_20_1:compileJava \
  :platforms:mc1_20_1:processResources \
  :platforms:mc1_20_1:verifyJarContents
./gradlew :platforms:mc1_20_1:resolveParentRuntime
./gradlew :platforms:mc1_20_1:runGameTestServer
./gradlew :platforms:mc1_20_1:runGameTestServer \
  -Pforge_version=47.4.0 \
  -Pvampirism_runtime_version=1.20.1-1.10.7 \
  -Pirons_spells_runtime_version=1.20.1-3.15.0
```

The log-marker finalizer must confirm successful reflective contract
registration. A Gradle success exit alone is insufficient because an early
mod-loading failure may otherwise look successful. The platform-local
`runs/gameTestServer` directory is reset before every launch so the floor run
cannot inherit the current profile's world or config. Manual release coverage
is the gameplay matrix in `AGENTS.md`, performed on Forge 1.20.1 in addition to
the NeoForge 1.21.1 run.

## Residual risks

- Forge's single damage event cannot provide exactly the same after-health
  observation as NeoForge `.Post`.
- Vampirism's `LazyOptional` lookup is loader-specific and must be re-audited if
  its API method or capability lifecycle changes.
- Iron's Lib is a mutable snapshot in the current development stack; record the
  resolved artifact when producing a release.
- Mana fallback and Ray animation still rely on narrow Iron's Spells mixin
  locations. Recheck both floor and current artifacts after every upstream
  update.
- Automated tests validate deterministic logic and class loading, not gameplay.
  Client and dedicated-server smoke tests remain required.
