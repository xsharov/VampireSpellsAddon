# AGENTS.md

## Maintenance Scope

- Project: Vampire Spells Addon (`com.vampirespells.addon`).
- Active target: Minecraft 1.21.1, NeoForge, Java 21.
- Package/mod id: `com.vampirespells.addon.*` / `vampire_spells_addon`.
- The 1.20.1 port is explicitly out of scope until the 1.21.1 line is stable.
- Treat this as an actively maintained compatibility mod, not as a finished or
  fully verified project. A successful compile does not prove runtime API or
  gameplay compatibility.

## Sources of Truth

- `gradle.properties`: build baseline, metadata floors, and development runtime
  dependency versions.
- `src/main/resources/META-INF/neoforge.mods.toml`: required runtime mods and
  accepted version ranges after resource expansion.
- `src/main/java/com/vampirespells/addon/`: implemented behavior.
- `dependency-source/`: ignored, read-only upstream reference clones.
- `AGENTS.md`: maintenance workflow, verified contracts, and known risks.
- `README.md`: concise user-facing behavior and build instructions.

Do not use the legacy `CLAUDE.md` text as an independent source of project
facts. It should only direct tools to this file.

## Current Baseline

- NeoForge compile baseline: `21.1.200`.
- Runtime compatibility floors:
  - Vampirism `1.10.7`.
  - Iron's Spells 'n Spellbooks `1.21.1-3.14.3`.
- Current development runtime releases:
  - Vampirism Maven artifact `1.21-1.10.12`.
  - Iron's Spells `1.21.1-3.16.2`.
- Upstream source snapshots used for the July 2026 audit:
  - Iron's Spells branch `1.21`, version `3.16.2`, commit `e57a7dcb`.
  - Vampirism branch `version/1.21/latest`, source version `1.10.13`, commit
    `c3224867`. This source version was newer than the published `1.21` Maven
    artifact at audit time.
  - NeoForge branch `1.21.1`, commit `73ab9150`.

Keep compatibility floors separate from current tested/reference versions.
Only raise a floor when the addon intentionally stops supporting older parent
versions and the new range has been tested.

## Implemented Behavior

- Ray of Siphoning keeps its damage and restores vampire blood from the
  `LivingDamageEvent.Pre` damage value using configurable multiplier and
  saturation values.
- Devour restores configurable vampire blood and multiplies its mana cost.
- Eight configured blood spells make a high/low-blood decision:
  `wither_skull`, `sacrifice`, `raise_dead`, `heartstop`, `blood_step`,
  `blood_slash`, `blood_needles`, and `acupuncture`.
  - At high blood they spend additional blood and use the high-blood cooldown
    multiplier.
  - At low blood they skip blood spending and use the low-blood cooldown
    multiplier.
  - The current implementation does not remove the normal Iron's Spells mana
    cost; blood is an additional cost.
- Holy damage reflects onto vampire player casters. Vampirism NPC vampires take
  doubled holy damage in the currently handled path.
- Holy heals damage vampire casters/targets and queue suppression of the
  corresponding `LivingHealEvent` amount.
- Holy utility spells (`angel_wing`, `fortify`, `wisp`, `haste`, `cleanse`, and
  `sunbeam`) deal 5 damage to a vampire caster and cancel the cast.

The server config is generated as
`config/vampire_spells_addon-server.toml`. Configuration definitions live in
`AddonConfig`; do not duplicate defaults in code or documentation.

## Integration Architecture

The addon compiles only against Minecraft and NeoForge. Parent-mod access is
runtime-only and reflective so their classes are never bundled into this JAR.

Verified Iron's Spells reflection targets for the current source snapshot:

- Events: `SpellPreCastEvent`, `SpellOnCastEvent`,
  `SpellCooldownAddedEvent.Pre`, `SpellDamageEvent`, and `SpellHealEvent`.
- `SpellDamageSource#spell()` and inherited `DamageSource#getEntity()`.
- `SpellRegistry#getSpell(ResourceLocation)`.
- `AbstractSpell#getSpellResource`, `getSchoolType`, and `getManaCost`.
- `SchoolType#getId`.

Verified Vampirism reflection targets:

- `VampirismAPI#vampirePlayer(Player)`.
- `IVampirePlayer#getLevel`, `getBloodLevel`, `getBloodStats`, and `useBlood`.
- `IBloodStats#getMaxBlood`.
- Both observed `IVampire#drinkBlood` overloads.
- `IDrinkBloodContext` entity/stack/block-state/block-position accessors.
- `de.teamlapen.vampirism.api.entity.vampire.IVampire` as the NPC marker.

Verified NeoForge contracts:

- Four-argument event-bus `addListener(EventPriority, boolean, Class,
  Consumer)` for reflective parent events.
- `LivingDamageEvent.Pre#getNewDamage`.
- `LivingHealEvent#getAmount/setAmount`.

Expected event order matters:

```text
SpellPreCastEvent
-> channel/cast start
-> SpellOnCastEvent
-> Iron's Spells mana debit
-> spell implementation
-> SpellCooldownAddedEvent.Pre

SpellDamageEvent
-> school resistance / friendly-fire checks
-> LivingEntity.hurt
-> LivingDamageEvent.Pre
-> absorption and health change
-> LivingDamageEvent.Post

SpellHealEvent
-> LivingEntity.heal
-> LivingHealEvent
```

## Reflection and Packaging Guardrails

1. Direct imports are allowed only from this addon, Java, Minecraft, and
   NeoForge.
2. Never add parent-mod compile dependencies, copied API classes, or stubs under
   `io.redspace.*` or `de.teamlapen.*`.
3. Development runtime dependencies are allowed through `localRuntime`; they
   must not extend the compile classpath or be shaded into the addon JAR.
4. Keep parent events dynamically registered on `NeoForge.EVENT_BUS`.
5. Reuse cached reflection resolution, handle missing members explicitly, and
   avoid per-tick/per-hit warning spam for a permanently missing contract.
6. Preserve normal behavior for non-vampires and unrelated spells.
7. Never copy classes or resources from `dependency-source/` into the addon.
8. The main JAR may contain only addon classes/resources and NeoForge metadata.

`SpellEventHandler.java` currently exceeds the 600-line project limit. Before
adding another mechanic, split reflection access, cast state, blood mechanics,
and holy mechanics into focused classes without changing the reflection-only
boundary.

## Upstream Update Workflow

When updating a parent mod or NeoForge:

1. Refresh the appropriate ignored clone under `dependency-source/` without
   adding it to this repository.
2. Record branch, commit, declared version, and whether that version is actually
   published to Maven.
3. Use `rg` to locate every reflected class, nested event class, and method
   signature in the upstream source.
4. Check event emission sites, cancellation rules, and ordering; matching names
   alone are insufficient.
5. Update development runtime versions independently from metadata floors.
6. Run the build and JAR checks below.
7. Smoke-test both the compatibility floor and the current release when a
   claimed version range spans both.
8. Update the snapshot section and durable MemPalace knowledge after verification.

## Build and Dependency Workflow

Use the committed Gradle wrapper and Java 21. Never commit a machine-specific
`org.gradle.java.home` value.

```bash
./gradlew compileJava processResources
./gradlew --no-daemon clean build
./gradlew resolveParentRuntime
./gradlew runClient
./gradlew runServer
./gradlew runData
./gradlew runGameTestServer
```

The first build downloads Gradle, a Java 21 toolchain when needed, NeoForge,
mappings, and other build dependencies. Parent mods and their transitive mods
belong on `localRuntime` for development launches. `dependency-source/` is not a
Gradle input.

`runClient`, `runServer`, and `runGameTestServer` only become meaningful
integration checks when Vampirism, Iron's Spells, Iron's Lib, GeckoLib, Player
Animator, Curios, and Vampirism's runtime dependencies are all resolved.

There are currently no automated unit tests or GameTests. A successful
`runGameTestServer` with zero tests is not gameplay evidence.

## GitHub Actions and Versioning

`.github/workflows/build.yml` runs on pushes, pull requests, and manual
dispatches with Java 21 and the committed wrapper. It builds, runs the Gradle
verification tasks, and uploads only the main JAR.

`mod_version` in `gradle.properties` is the version baseline. CI adds
`github.run_number` to its numeric patch component and passes the result through
`-Pmod_version`; the generated `neoforge.mods.toml`, manifest, JAR filename, and
artifact name therefore share one automatically increasing version. Do not add
routine version-bump commits for CI builds. Change the baseline only for an
intentional version-line reset or release policy change.

## Verification Pipeline

Quick feedback:

```bash
./gradlew compileJava processResources
```

Required before committing build or code changes:

```bash
./gradlew --no-daemon clean build
jar --list --file build/libs/vampire_spells_addon-neoforge-*.jar
```

Verify all of the following:

- No unexpanded `${...}` placeholders remain in `META-INF/neoforge.mods.toml`.
- No `io/redspace/`, `de/teamlapen/`, or bundled `net/neoforged/` classes exist
  in the main JAR.
- The main JAR, not `*-sources.jar`, is selected for distribution.
- The JAR version, manifest version, and expanded mod version agree.
- Reflection listeners register without warnings in a real development launch.
- Client and dedicated-server smoke tests include both parent mods and all
  required runtime dependencies.

Manual gameplay coverage for integration changes:

- vampire and non-vampire player paths;
- Vampirism NPC and ordinary living targets;
- high/low/insufficient blood, cast cancellation, channeled casts, and recasts;
- Ray and Devour against armor, absorption, lethal/overkill, and invalid targets;
- each blood-cost spell at multiple levels;
- holy damage, self-heal, targeted heal, area heal, utility, and friendly fire;
- logout/death/dimension transition while cast state is pending.

## Known Risks Requiring Runtime Validation

- Ray and Devour restore blood from `LivingDamageEvent.Pre`, before absorption,
  overkill, and final health loss are known. Iron's Spells lifesteal uses the
  post-damage stage.
- Devour's mana multiplier currently applies to non-vampires and is changed only
  after the original affordability check.
- Holy damage reflection uses the early `SpellDamageEvent` amount. Later
  resistance, friendly-fire, or cancellation can make reflected and delivered
  damage diverge.
- Holy-heal suppression stores `UUID -> pending amount`; an altered or missing
  heal event can leave residue that suppresses a later unrelated heal.
- Blood cast decisions can become stale between pre-cast, cast, cooldown, and
  logout/cancellation. The current maps have no explicit lifecycle cleanup.
- The high-blood threshold uses integer rounding rather than an exact fractional
  comparison.
- `HOLY_HEAL_SPELLS` is currently declared but unused.

Do not silently change these mechanics during unrelated maintenance. Reproduce
the behavior, define the intended rule, add focused validation, and then change
it in a separate commit.

## Release Checklist

1. Build from a clean checkout with Java 21 and the committed wrapper.
2. Confirm parent mod versions, Maven availability, and metadata floors.
3. Run JAR hygiene verification and inspect the expanded mod metadata.
4. Complete client and dedicated-server smoke tests with the runtime mod set.
5. Complete the manual gameplay matrix relevant to the release.
6. Tag/release the exact commit and attach the main JAR produced by CI.

<!-- LOCAL_MEMPALACE_MODULE_START -->
## MemPalace Local Memory

This repository uses project-local MemPalace storage. Memory for this project
belongs under `.mempalace/`; do not use the global `~/.mempalace` palace for
project work.

Local layout:

```text
.mempalace/
  config.json
  known_entities.json
  mempalace.bat
  mempalace.ps1
  mempalace.sh
  palace/
```

Initialize or refresh local memory:

macOS:

```bash
bash "${CODEX_HOME:-$HOME/.codex}/scripts/init-local-mempalace.sh" --project-path "$PWD"
```

Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.codex\scripts\init-local-mempalace.ps1" -ProjectPath "$PWD"
```

Mine the repository after initialization:

macOS:

```bash
bash "${CODEX_HOME:-$HOME/.codex}/scripts/init-local-mempalace.sh" --project-path "$PWD" --mine
```

Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.codex\scripts\init-local-mempalace.ps1" -ProjectPath "$PWD" -Mine
```

Run manual commands only through the project launcher:

```powershell
.\.mempalace\mempalace.ps1 status
```

Codex MCP should use the stable wrapper in Codex home. If the palace changes
outside a running MCP session, reconnect before relying on reads.
<!-- LOCAL_MEMPALACE_MODULE_END -->
