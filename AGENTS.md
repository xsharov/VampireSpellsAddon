# AGENTS.md

## Maintenance Scope

- Project: Vampire Spells Addon (`com.vampirespells.addon`).
- Active targets:
  - Minecraft 1.21.1, NeoForge, Java 21.
  - Minecraft 1.20.1, Forge, Java 17.
- Package/mod id: `com.vampirespells.addon.*` / `vampire_spells_addon`.
- The two targets are one release line and must ship together. Loader-specific
  adapters may differ, but shared gameplay behavior must remain aligned.
- Treat this as an actively maintained compatibility mod, not as a finished or
  fully verified project. A successful compile does not prove runtime API or
  gameplay compatibility.

## Sources of Truth

- `gradle.properties`: canonical release-version baseline and shared metadata.
- `gradle/targets/mc1_21_1.properties` and
  `gradle/targets/mc1_20_1.properties`: platform toolchains, compatibility
  floors, and development runtime versions.
- `platforms/*/src/main/resources/META-INF/`: loader metadata and accepted
  runtime version ranges after resource expansion.
- `src/main/java/com/vampirespells/addon/`: implemented behavior.
- `platforms/*/src/main/java/com/vampirespells/addon/`: loader-specific entry
  points, event/config adapters, and mixins.
- `dependency-source/`: ignored, read-only upstream reference clones.
- `AGENTS.md`: maintenance workflow, verified contracts, and known risks.
- `README.md`: concise user-facing behavior and build instructions.

Do not use the legacy `CLAUDE.md` text as an independent source of project
facts. It should only direct tools to this file.

## Git Workflow

- Never edit or commit directly on `master`. Before changing repository files,
  fetch the latest `origin/master`, then create and switch to a task-specific
  branch from that remote-tracking ref. Do not update or modify local `master`
  as part of task work.
- Codex may fetch, create and switch branches, stage, commit, and push without
  requesting additional user permission when completing repository work.
- Stage only files that belong to the current task. Preserve unrelated local
  changes and keep every commit focused.
- Commit messages must briefly summarize the completed work. The commit body
  must include relevant implementation and verification notes when they add
  useful context.
- After completing and verifying a change, push the task branch and use the
  authenticated GitHub CLI (`gh`) to create a pull request targeting `master`.
  Do not leave completed work only in local commits.
- Every pull request must have a detailed Markdown description covering:
  - what changed;
  - why the change was needed;
  - user, developer, or runtime impact;
  - the verification performed and its result;
  - known limitations, risks, or follow-up work, when applicable.
- Use `gh` for GitHub operations, including repository inspection, pull request
  creation, and pull request updates. If push or pull request creation is
  blocked, stop and report the exact blocker instead of committing to
  `master` or claiming that publication succeeded.

## Current Baselines

Minecraft 1.21.1 / NeoForge:

- NeoForge compile and runtime compatibility floor: `21.1.200`.
- Runtime compatibility floors: Vampirism `1.10.7`; Iron's Spells 'n
  Spellbooks `1.21.1-3.14.3`.
- Current development runtime:
  - Vampirism Maven artifact `1.21-1.10.12`.
  - Iron's Spells `1.21.1-3.16.2`.
  - Iron's Lib `1.21.1-2.1.0-SNAPSHOT`, which currently requires NeoForge
    `21.1.200` or newer.
- Source snapshots used for the July 2026 audit:
  - Iron's Spells branch `1.21`, version `3.16.2`, commit `e57a7dcb`.
  - Vampirism branch `version/1.21/latest`, source version `1.10.13`, commit
    `c3224867`. This source version was newer than the published `1.21` Maven
    artifact at audit time.
  - NeoForge branch `1.21.1`, commit `73ab9150`.

Minecraft 1.20.1 / Forge:

- Forge development runtime `47.4.10`; runtime compatibility floor `47.4.0`.
- Runtime compatibility floors: Vampirism `1.20.1-1.10.7`; Iron's Spells
  `1.20.1-3.15.0`.
- Current development runtime: Vampirism `1.20.1-1.10.16`; Iron's Spells
  `1.20.1-3.16.2`; Iron's Lib `1.20.1-2.1.0-SNAPSHOT`.
- Build plugins are pinned to ForgeGradle `6.0.54`, MixinGradle `0.7.38`, and
  Librarian `1.2.0`.
- Audited source snapshots: Iron's Spells tags `v1.20.1-3.15.0` at
  `86f6cc73` and `v1.20.1-3.16.2` at `a136b3ad`; Vampirism branch
  `version/1.20/1.20.1/latest` at `2ad80f8c`.

Minecraft 1.20.1 intentionally targets Forge. Iron's Spells 3.15+ requires
Forge `47.4.0` or newer, while the NeoForge 1.20.1 line stopped before that
loader baseline. Do not replace this with a nominal NeoForge target that cannot
load the supported parent-mod stack.

Keep compatibility floors separate from current tested/reference versions.
Only raise a floor when the addon intentionally stops supporting older parent
versions and the new range has been tested.

## Implemented Behavior

- Ray of Siphoning and Devour restore configurable vampire blood from delivered
  health damage. On NeoForge 1.21.1 the amount comes from
  `LivingDamageEvent.Post`; on Forge 1.20.1 the platform adapter observes the
  final `LivingDamageEvent` at `LOWEST` priority. Restoration accounts for
  absorption, caps lethal overkill to the target's health at that point, and
  never requests more than the caster's free blood capacity.
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

The server config is generated per world as
`<world>/serverconfig/vampire_spells_addon-server.toml`. Configuration
definitions live in `AddonConfig`; do not duplicate defaults in code or
documentation.

## Integration Architecture

The build is a multi-project Gradle build. Shared Java and resources under
`src/` are compiled once per target; `platforms/mc1_21_1` supplies NeoForge
adapters and `platforms/mc1_20_1` supplies Forge adapters. Each target compiles
only against Minecraft, its loader, and Mixin transformation APIs. Parent-mod
access remains runtime-only: ordinary integration is reflective and narrow
mixins use string targets, so parent classes are never placed on the compile
classpath or bundled into either JAR.

Keep gameplay rules, reflection bridges, and state machines shared. Loader
imports, event registration, configuration wrappers, entry points, and
loader-specific mixin descriptors belong in the platform project. Do not add
runtime loader detection to shared gameplay code when a platform adapter can
express the difference directly.

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

- NeoForge 1.21.1 `VampirismAPI#vampirePlayer(Player)` direct return.
- Forge 1.20.1 `VampirismAPI#getVampirePlayer(Player)` returning
  `LazyOptional<IVampirePlayer>`; the bridge unwraps it reflectively without a
  Forge compile dependency in shared code.
- `IVampirePlayer#getLevel`, `getBloodLevel`, and `getBloodStats`.
- `IBloodStats#getMaxBlood`.
- `IVampire#useBlood` and the four-argument `IVampire#drinkBlood` overload.
- `IDrinkBloodContext` entity/stack/block-state/block-position accessors.
- `de.teamlapen.vampirism.api.entity.vampire.IVampire` as the NPC marker.

Verified NeoForge 1.21.1 contracts:

- Four-argument event-bus `addListener(EventPriority, boolean, Class,
  Consumer)` for reflective parent events.
- `LivingDamageEvent.Pre` and `LivingDamageEvent.Post#getNewDamage`.
- `LivingHealEvent#getAmount/setAmount` and cancellation.
- Player logout/clone and server tick/stop lifecycle events.

Verified Forge 1.20.1 contracts:

- Four-argument event-bus `addListener(EventPriority, boolean, Class,
  Consumer)` for reflective parent events.
- Final `LivingDamageEvent` delivery at `LOWEST` priority and
  `LivingHealEvent#getAmount/setAmount` plus cancellation.
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
-> NeoForge 1.21.1 LivingDamageEvent.Pre
-> absorption and health change
-> NeoForge 1.21.1 LivingDamageEvent.Post

Forge 1.20.1 LivingEntity.hurt
-> armor, enchantment, absorption and other damage processing
-> LivingDamageEvent (addon at LOWEST)
-> health change

SpellHealEvent
-> LivingEntity.heal
-> LivingHealEvent
```

## Reflection and Packaging Guardrails

1. Shared-source imports are allowed only from this addon, Java, Minecraft, and
   Mixin APIs common to both targets. NeoForge and Forge imports belong only in
   their corresponding platform source set.
2. Never add parent-mod compile dependencies, copied API classes, or stubs under
   `io.redspace.*` or `de.teamlapen.*`.
3. Development parent dependencies are allowed only in the platform runtime
   configuration (`localRuntime` on NeoForge, `parentRuntime` on Forge). They
   must not extend the shared compile classpath or be shaded into either JAR.
4. Keep parent events dynamically registered on the platform gameplay event
   bus through `PlatformEvents`.
5. Reuse cached reflection resolution, handle missing members explicitly, and
   avoid per-tick/per-hit warning spam for a permanently missing contract.
6. Preserve normal behavior for non-vampires and unrelated spells.
7. Never copy classes or resources from `dependency-source/` into the addon.
8. Each main JAR may contain only addon classes/resources and its own loader
   metadata. Never package NeoForge classes in the Forge JAR or Forge classes in
   the NeoForge JAR.
9. Keep mixin targets string-based, configs required, injections narrowly
   scoped with `defaultRequire = 1`, and verify each target's floor/current
   runtimes after every upstream change.

Keep the existing split between reflection bridges, cast/damage state, blood
mechanics, holy mechanics, and listener orchestration. No Java source currently
exceeds the 600-line project limit.

## Upstream Update Workflow

When updating a parent mod, NeoForge, or Forge:

1. Refresh the appropriate ignored clone under `dependency-source/` without
   adding it to this repository.
2. Record branch, commit, declared version, and whether that version is actually
   published to Maven.
3. Use `rg` to locate every reflected class, nested event class, and method
   signature in the upstream source.
4. Check event emission sites, cancellation rules, and ordering; matching names
   alone are insufficient.
5. Update the relevant `gradle/targets/*.properties` development runtime
   independently from metadata floors.
6. Run the build and JAR checks below.
7. Smoke-test both the compatibility floor and the current release on every
   affected Minecraft/loader target.
8. Update the snapshot section and durable MemPalace knowledge after verification.

## Build and Dependency Workflow

Use the committed Gradle wrapper. The 1.21.1 project selects Java 21 and the
1.20.1 project selects Java 17 through toolchains. Never commit a
machine-specific `org.gradle.java.home` value.

```bash
./gradlew test
./gradlew --no-daemon clean build
./gradlew resolveParentRuntime
./gradlew collectReleaseJars
./gradlew :platforms:mc1_21_1:runClient
./gradlew :platforms:mc1_21_1:runServer
./gradlew :platforms:mc1_21_1:runGameTestServer
./gradlew :platforms:mc1_20_1:runClient
./gradlew :platforms:mc1_20_1:runServer
./gradlew :platforms:mc1_20_1:runGameTestServer
```

Root `test`, `build`, and `resolveParentRuntime` aggregate both platforms.
Compile/resource/run tasks are intentionally platform-qualified. The first
build downloads Gradle, the required Java toolchains, loaders, mappings, and
other build dependencies. Parent mods and their transitive mods belong only on
the platform runtime configuration. `dependency-source/` is not a Gradle input.

Platform `runClient`, `runServer`, and `runGameTestServer` tasks only become
meaningful integration checks when Vampirism, Iron's Spells, Iron's Lib,
GeckoLib, Player Animator, Curios, and Vampirism's runtime dependencies are all
resolved for that Minecraft version.
`runGameTestServer` deletes and recreates only its platform-local disposable
`runs/gameTestServer` directory before every launch so current and floor
profiles never reuse a world, config, or success marker.

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
the release version as a draft before building. Separate read-only matrix jobs
check out that commit without persisting Git credentials, then use Java 21 for
NeoForge 1.21.1 and Java 17 for Forge 1.20.1. Each job builds its qualified
platform project, resolves its parent runtime, runs JAR verification, and
smoke-loads current and compatibility-floor parent versions. Each passes only
its exact main JAR through a 30-day, unarchived internal artifact to the publish
job. Repository code never runs in either write-enabled job.
`runGameTestServer` is finalized by a log-marker check because either loader's
Gradle task can otherwise report success after an early mod-loading failure.

`mod_version` in `gradle.properties` is the version baseline. CI scans numeric
release tags with the same version prefix and selects the next patch above both
the baseline and existing tags. A rerun for the same merge commit reuses its
existing tag or draft version. The selected version is passed through
`-Pmod_version`. The canonical tag stays `1.21.1-X.Y.Z`; the neutral `X.Y.Z`
suffix is reused for the Forge artifact. The generated loader metadata,
manifests, JAR filenames, and release tag therefore agree. Each release must
contain exactly:

- `vampire_spells_addon-neoforge-1.21.1-X.Y.Z.jar`.
- `vampire_spells_addon-forge-1.20.1-X.Y.Z.jar`.

The publish job verifies both JARs by SHA-256 digest and publishes the reserved
release only after both are present. Release workflows
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
./gradlew :platforms:mc1_21_1:compileJava :platforms:mc1_21_1:processResources
./gradlew :platforms:mc1_20_1:compileJava :platforms:mc1_20_1:processResources
```

Required before committing build or code changes:

```bash
./gradlew --no-daemon clean build resolveParentRuntime
./gradlew :platforms:mc1_21_1:runGameTestServer
./gradlew :platforms:mc1_21_1:runGameTestServer \
  -Pvampirism_runtime_version=1.21-1.10.7 \
  -Pirons_spells_runtime_version=1.21.1-3.14.3
./gradlew :platforms:mc1_20_1:runGameTestServer
./gradlew :platforms:mc1_20_1:runGameTestServer \
  -Pforge_version=47.4.0 \
  -Pvampirism_runtime_version=1.20.1-1.10.7 \
  -Pirons_spells_runtime_version=1.20.1-3.15.0
./gradlew collectReleaseJars
```

Verify all of the following:

- No unexpanded `${...}` placeholders remain in `neoforge.mods.toml` or
  `mods.toml`.
- No `io/redspace/`, `de/teamlapen/`, or bundled loader classes exist in either
  main JAR.
- The main JAR, not `*-sources.jar`, is selected for distribution.
- The JAR version, manifest version, and expanded mod version agree.
- The NeoForge JAR uses class-file major 65; the Forge JAR uses major 61.
- Reflection listeners register without warnings in a real development launch.
- Client and dedicated-server smoke tests include both parent mods and all
  required runtime dependencies on both platforms.

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
- NeoForge post-damage correlation assumes synchronous paired Pre/Post events.
  Forge 1.20.1 has no matching Post event; its `LOWEST` `LivingDamageEvent`
  amount is the best available delivered-damage estimate and is capped to the
  target's then-current health. Third-party handlers registered after the addon
  can still rewrite or cancel that event, so absorption, cancellation, lethal
  overkill, and re-entrant damage require platform-specific runtime coverage.
- Holy-heal suppression matches target, amount, and server tick. A third-party
  modifier that rewrites or defers the heal makes the token expire instead of
  suppressing an unrelated heal.
- Iron's Lib resolves as mutable `1.21.1-2.1.0-SNAPSHOT` and
  `1.20.1-2.1.0-SNAPSHOT` artifacts. The 1.21.1 artifact currently requires
  NeoForge `21.1.200`; record and retest both resolved artifacts and their
  metadata floors for releases.
- The Forge 1.20.1 Vampirism API returns its vampire-player capability through
  `LazyOptional` on audited builds. Shared code unwraps it reflectively; verify
  both present and empty capability paths whenever Vampirism changes.
- Vampirism's POM references a legacy JEI artifact id. Both runtimes exclude it
  and explicitly select the loader-appropriate `jei-1.21.1-neoforge` or
  `jei-1.20.1-forge` artifact; never let both JEI artifact ids enter a
  development launch.
- The audited Vampirism `1.10.13` source was newer than the published `1.21`
  Maven artifact. Recheck availability and exact release sources before raising
  the development runtime.

Do not silently change these mechanics during unrelated maintenance. Reproduce
the behavior, define the intended rule, add focused validation, and then change
it in a separate commit.

## Release Checklist

1. Build from a clean checkout with Java 21 and Java 17 toolchains available,
   using the committed wrapper.
2. Confirm parent mod versions, Maven availability, and metadata floors.
3. Run JAR hygiene verification and inspect both expanded loader metadata files.
4. Complete client and dedicated-server smoke tests with each platform's
   runtime mod set.
5. Complete the manual gameplay matrix relevant to the release.
6. Merge the intended pull request into `master`, then confirm that the release
   workflow tagged the exact merge commit and attached both expected main JARs.

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
