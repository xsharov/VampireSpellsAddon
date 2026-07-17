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

## Git Permissions

- Codex may create and switch branches in this repository without requesting
  additional user permission.
- Codex may stage changes and create commits in this repository without
  requesting additional user permission.
- Keep commits focused and use commit messages that briefly summarize the
  completed work and any relevant verification notes.

## Current Baseline

- NeoForge compile baseline: `21.1.200`.
- NeoForge runtime compatibility floor: `21.1.200`.
- Runtime compatibility floors:
  - Vampirism `1.10.7`.
  - Iron's Spells 'n Spellbooks `1.21.1-3.14.3`.
- Current development runtime releases:
  - Vampirism Maven artifact `1.21-1.10.12`.
  - Iron's Spells `1.21.1-3.16.2`.
  - Iron's Lib `1.21.1-2.1.0-SNAPSHOT`, which currently requires NeoForge
    `21.1.200` or newer.
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

- Ray of Siphoning and Devour restore configurable vampire blood from delivered
  health damage observed at `LivingDamageEvent.Post`. Restoration accounts for
  absorption, caps lethal overkill to pre-hit health, and never requests more
  than the caster's free blood capacity.
- Vampire players use mana normally for mana-consuming Blood School spells.
  When current mana cannot cover the full price, all such spells except Ray of
  Siphoning atomically replace the complete mana debit with blood. The server
  config can instead make this blood-only replacement unconditional.
  - Creative players, cast sources that do not consume mana, and upstream
    recasts are excluded from resource replacement.
  - The blood price uses the final `SpellOnCastEvent` mana price and the
    configured mana-to-blood ratio. Devour's vampire-only mana multiplier is
    applied before either resource is selected.
  - Insufficient blood cancels before the mana debit and spell effect. No
    partial blood or mana debit is retained.
  - Ray of Siphoning always keeps its upstream mana cost and continues to
    restore blood.
- Every Blood School spell cast by a vampire, including Ray of Siphoning, uses
  the configured cooldown multiplier. The default is `2/3`, making cooldowns
  1.5 times shorter while preserving upstream player and item modifiers.
- Ray of Siphoning reverses its animated UV flow for vampire player casters on
  the client without changing its geometry or target-to-caster particles. Its
  guide text is overridden in all 15 Iron's Spells locales with the additional
  night-creature lore.
- Delivered holy health damage reflects onto vampire player casters. Vampirism
  NPC vampires take doubled holy damage independently of who cast the spell.
- Holy heals damage vampire casters/targets and queue suppression of the
  corresponding `LivingHealEvent` amount.
- Holy utility spells (`angel_wing`, `fortify`, `wisp`, `haste`, `cleanse`, and
  `sunbeam`) deal 5 damage to a vampire caster, reset upstream additional cast
  data, and cancel the cast.

Cast outcomes use a bounded thread-local LIFO so nested `SpellOnCastEvent`
dispatch remains correlated with the matching `AbstractSpell#castSpell` frame.
Holy-heal correlation remains bounded and is cleared on timeout, logout, player
clone, and server shutdown. Resource replacement does not persist player data;
upstream recasts are detected directly and remain free.

The server config is generated as
`config/vampire_spells_addon-server.toml`. Configuration definitions live in
`AddonConfig`; do not duplicate defaults in code or documentation.

## Integration Architecture

The addon compiles only against Minecraft, NeoForge, and the Mixin/MixinExtras
transformation APIs supplied by the NeoForge runtime. Parent-mod access remains
runtime-only: ordinary integration is reflective and the narrow mixins use
string targets, so parent classes are never placed on the compile classpath or
bundled into this JAR.

Verified Iron's Spells reflection targets for the current source snapshot:

- Events: `SpellPreCastEvent`, `SpellOnCastEvent`,
  `SpellCooldownAddedEvent.Pre`, `SpellDamageEvent`, and `SpellHealEvent`.
- `SpellDamageSource#spell()` and inherited `DamageSource#getEntity()`.
- `SpellRegistry#getSpell(ResourceLocation)`.
- `AbstractSpell#getSpellResource`, `getSchoolType`, and `getManaCost`.
- `SchoolType#getId`.
- `CastSource#consumesMana`.
- `SpellOnCastEvent#getSchoolType` and `getCastSource`.
- `MagicData#getPlayerMagicData`, `getMana`, `resetAdditionalCastData`, and
  `getPlayerRecasts`.
- `PlayerRecasts#hasRecastForSpell(String)`.

Verified Iron's Spells transformation targets for both the compatibility floor
and current runtime:

- `AbstractSpell#canBeCastedBy`: modify the single
  `CastSource#consumesMana()` expression so eligible vampire blood fallback can
  reach `SpellPreCastEvent` without weakening learning, cooldown, adventure, or
  spell-specific checks.
- `AbstractSpell#castSpell`: after `SpellOnCastEvent` dispatch and before the
  mana debit/effect, stop an outcome that could not atomically pay blood.
- Client `SpellRenderingHelper#renderRayOfSiphoning`: invert the stored
  `deltaUV` float only for vampire player casters.

Verified Vampirism reflection targets:

- `VampirismAPI#vampirePlayer(Player)`.
- `IVampirePlayer#getLevel`, `getBloodLevel`, and `getBloodStats`.
- `IBloodStats#getMaxBlood`.
- `IVampire#useBlood` and the four-argument `IVampire#drinkBlood` overload.
- `IDrinkBloodContext` entity/stack/block-state/block-position accessors.
- `de.teamlapen.vampirism.api.entity.vampire.IVampire` as the NPC marker.

Verified NeoForge contracts:

- Four-argument event-bus `addListener(EventPriority, boolean, Class,
  Consumer)` for reflective parent events.
- `LivingDamageEvent.Pre` and `LivingDamageEvent.Post#getNewDamage`.
- `LivingHealEvent#getAmount/setAmount` and cancellation.
- Player logout/clone and server tick/stop lifecycle events.

Expected event order matters:

```text
spell.checkPreCastConditions
-> SpellPreCastEvent
-> channel/cast start
-> SpellOnCastEvent
-> addon unpaid-blood abort hook
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

1. Direct imports are allowed only from this addon, Java, Minecraft, NeoForge,
   and, inside mixin classes, the Mixin/MixinExtras APIs shipped with NeoForge.
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
9. Keep mixin targets string-based, configs required, injections narrowly
   scoped with `defaultRequire = 1`, and verify both floor/current runtimes after
   every upstream change.

Keep the existing split between reflection bridges, cast/damage state, blood
mechanics, holy mechanics, and listener orchestration. No Java source currently
exceeds the 600-line project limit.

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
./gradlew test
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

Deterministic blood-cost, resource-selection, restoration, mana, cooldown,
nested-outcome, and localization checks have unit tests. There are no automated
gameplay GameTests. A successful `runGameTestServer` with zero tests is not
gameplay evidence.

## GitHub Actions and Versioning

`.github/workflows/build.yml` listens for closed `pull_request_target` events
whose base branch is `master`, but its release job runs only when the pull
request was actually merged. Direct pushes, manual events, and pull requests
closed without merging do not build or publish anything. The workflow checks
the exact merge commit's `gradle.properties` through the GitHub API and reserves
the release version as a draft before building. A separate read-only job checks
out that commit without persisting Git credentials, then uses Java 21 and the
committed wrapper to build, resolve the parent runtime, run the Gradle
verification tasks, and smoke-load the current and compatibility-floor parent
versions. It passes only the exact main JAR through a 30-day, unarchived
internal artifact to the publish job. Repository code never runs in either
write-enabled job.
`runGameTestServer` is finalized by a log-marker check because NeoGradle can
otherwise report success after an early mod-loading failure.

`mod_version` in `gradle.properties` is the version baseline. CI scans numeric
release tags with the same version prefix and selects the next patch above both
the baseline and existing tags. A rerun for the same merge commit reuses its
existing tag or draft version. The selected version is passed through
`-Pmod_version`; the generated `neoforge.mods.toml`, manifest, JAR filename, and
release tag therefore agree. The publish job uploads and verifies the main JAR
by its SHA-256 digest, then publishes the reserved release. Release workflows
queue instead of canceling one another. A later descendant merge may replace a
workflow-created unfinished draft and reuse its reserved version, which lets a
fixing merge recover from a deterministic build failure. Manual drafts and
drafts on unrelated commits remain fail-closed and require explicit cleanup.
If GitHub delivers an older merge event after a newer tagged merge, the stale
event exits successfully instead of publishing older code as the latest
release. Do not add routine version-bump commits for CI builds. Change the
baseline only for an intentional version-line reset or release policy change.

## Verification Pipeline

Quick feedback:

```bash
./gradlew test
./gradlew compileJava processResources
```

Required before committing build or code changes:

```bash
./gradlew --no-daemon clean build resolveParentRuntime
./gradlew runGameTestServer
./gradlew runGameTestServer \
  -Pvampirism_runtime_version=1.21-1.10.7 \
  -Pirons_spells_runtime_version=1.21.1-3.14.3
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
- sufficient/insufficient mana, default fallback, forced blood-only mode,
  insufficient blood, creative and non-mana sources, cast cancellation, and
  recasts;
- Ray and Devour against armor, absorption, lethal/overkill, and invalid targets;
- each blood-cost spell at multiple levels;
- Ray UV direction for local and remote vampire/non-vampire players;
- holy damage, self-heal, targeted heal, area heal, utility, and friendly fire;
- logout/death/dimension transition while cast state is pending.

## Known Risks Requiring Runtime Validation

- Iron's Spells `3.15.5`/`3.15.6` continuous-cast fixes make the 100-tick Ray of
  Siphoning apply 11 pulses rather than 10. Total blood restoration may need a
  separate balance adjustment after gameplay measurement.
- The pre-cast affordability estimate cannot observe third-party changes made
  later in `SpellOnCastEvent`. The final event price is authoritative, so a
  late increase can switch to blood or stop the cast and a late decrease can
  keep it on mana.
- Resource fallback and Ray UV reversal depend on narrow mixin injection points.
  Recheck the invocation target and Ray float-local ordinal against every Iron's
  Spells update, then smoke-load both the compatibility floor and current
  runtime on the appropriate server/client side.
- Post-damage correlation assumes NeoForge's synchronous paired Pre/Post events.
  Nested damage is tracked, but third-party source replacement and unusual
  re-entrant paths still need runtime coverage.
- Holy-heal suppression matches target, amount, and server tick. A third-party
  modifier that rewrites or defers the heal makes the token expire instead of
  suppressing an unrelated heal.
- Iron's Lib resolves as the mutable `1.21.1-2.1.0-SNAPSHOT` and currently
  requires NeoForge `21.1.200`; record and retest the resolved artifact and its
  metadata floor for releases.
- Vampirism's POM references a legacy JEI artifact id. The runtime excludes it
  and explicitly selects `jei-1.21.1-neoforge`; never let both JEI artifact ids
  enter a development launch.
- The audited Vampirism `1.10.13` source was newer than the published `1.21`
  Maven artifact. Recheck availability and exact release sources before raising
  the development runtime.

Do not silently change these mechanics during unrelated maintenance. Reproduce
the behavior, define the intended rule, add focused validation, and then change
it in a separate commit.

## Release Checklist

1. Build from a clean checkout with Java 21 and the committed wrapper.
2. Confirm parent mod versions, Maven availability, and metadata floors.
3. Run JAR hygiene verification and inspect the expanded mod metadata.
4. Complete client and dedicated-server smoke tests with the runtime mod set.
5. Complete the manual gameplay matrix relevant to the release.
6. Merge the intended pull request into `master`, then confirm that the release
   workflow tagged the exact merge commit and attached the main JAR.

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
