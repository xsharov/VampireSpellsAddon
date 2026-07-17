# Compatibility Audit: 2026-07-17

This document records the source-level compatibility review for the Minecraft
1.21.1 maintenance line. The ignored clones under `dependency-source/` were the
primary sources; release notes were used to identify behavior changes, and the
actual declarations and event emission sites were then checked in code.

## Audited upstream snapshots

| Component | Branch | Commit | Version represented |
| --- | --- | --- | --- |
| Iron's Spells 'n Spellbooks | `1.21` | `e57a7dcb` | `1.21.1-3.16.2` |
| Vampirism | `version/1.21/latest` | `c3224867` | source version `1.10.13` |
| NeoForge | `1.21.1` | `73ab9150` | current 1.21.1 source snapshot |

The Iron's Spells `3.16.2` release tag points to `8e233f5d`; the audited branch
head only adds generated site data after that tag, so its runtime contracts are
the same. Vampirism commit `c3224867` declares `1.10.13`. Its parent `39c1b02`
declares `1.10.12`, and the only difference between them is version metadata.
At audit time `1.10.13` was newer than the published `1.21` Maven artifact, so
the development runtime intentionally remains on `1.21-1.10.12`.

The supported metadata ranges are:

- NeoForge `>=21.1.200` and `<21.2`.
- Vampirism `>=1.10.7` and `<1.11`.
- Iron's Spells `>=1.21.1-3.14.3` and `<1.21.1-4`.

These are compatibility floors, not claims that every gameplay path has been
tested on every intermediate release. The current development runtime uses
Vampirism `1.21-1.10.12` and Iron's Spells `1.21.1-3.16.2`.

## Release changes relevant to the addon

Iron's Spells 3.15 moved spell configuration into data-pack JSON. Spell balance
can therefore change without a Java signature change, but the registry, spell,
school, mana, event and damage-source methods used by this addon remained
compatible. The 3.15.5/3.15.6 continuous-cast fixes changed tick scheduling:
Ray of Siphoning now applies 11 pulses over its 100-tick cast instead of 10,
and a 200-tick continuous cast such as Cloud of Regeneration can apply 21
pulses instead of 20. This affects aggregate blood restoration and holy-heal
penalties even though no reflected API changed.

The 3.16 line changed gameplay around integrations already handled here:
Blood Step's range was reduced, Haste became single-target, and Fortify's
cooldown changed. No configured blood or holy spell identifier was renamed or
removed, and no audited event signature changed.

Vampirism kept the player-vampire, blood-stat and blood-consumption API used by
the addon source-compatible from the `1.10.7` floor through the audited source.
NeoForge kept the required event types and four-argument event-bus listener
registration contract compatible. Although the addon source also compiles on
NeoForge `21.1.134`, the current Iron's Lib artifact required by Iron's Spells
declares NeoForge `21.1.200` as its own minimum. The addon therefore uses
`21.1.200` as the lowest installable runtime combination instead of advertising
a combination that the parent-mod ecosystem rejects during loading.

## Verified reflective contracts

The addon still compiles only against Minecraft and NeoForge. At startup it
resolves the parent-mod contracts once, registers parent events dynamically,
and fails closed with one diagnostic if a required contract is missing.

Iron's Spells contracts checked in source:

- `SpellPreCastEvent`, `SpellOnCastEvent`, `SpellCooldownAddedEvent.Pre`,
  `SpellDamageEvent`, and `SpellHealEvent`, including their player, spell,
  level, cast-source, mana, cooldown, amount and cancellation accessors.
- `SpellDamageSource#spell()` and inherited `DamageSource#getEntity()`.
- `SpellRegistry#getSpell(ResourceLocation)`.
- `AbstractSpell#getSpellResource()`, `getSchoolType()`, and
  `getManaCost(int)`; `SchoolType#getId()`.
- `CastSource#consumesMana()`, `MagicData#getPlayerMagicData(LivingEntity)`,
  `getMana()`, and `resetAdditionalCastData()`.

Vampirism contracts checked in source:

- `VampirismAPI#vampirePlayer(Player)`.
- `IVampirePlayer#getLevel()`, `getBloodLevel()`, and `getBloodStats()`.
- `IBloodStats#getMaxBlood()` and `IVampire#useBlood(int, boolean)`.
- The four-argument `IVampire#drinkBlood(int, float, boolean,
  IDrinkBloodContext)` overload.
- The entity, item stack, block state and block position accessors on
  `IDrinkBloodContext`.
- `de.teamlapen.vampirism.api.entity.vampire.IVampire` as the NPC marker.

NeoForge contracts checked in source include the four-argument
`IEventBus#addListener` overload, `LivingDamageEvent.Pre` and `.Post`,
`LivingHealEvent`, and the entity/player/server lifecycle events used to clean
correlation state.

The audited event order is significant:

```text
spell.checkPreCastConditions
-> SpellPreCastEvent
-> channel/cast start
-> SpellOnCastEvent
-> Iron's Spells mana debit
-> spell implementation
-> SpellCooldownAddedEvent.Pre

SpellDamageEvent
-> school resistance and friendly-fire checks
-> LivingEntity.hurt
-> LivingDamageEvent.Pre
-> absorption and health change
-> LivingDamageEvent.Post

SpellHealEvent
-> LivingEntity.heal
-> LivingHealEvent
```

## Incompatibilities and weak points corrected

- Ray and Devour previously restored blood from `LivingDamageEvent.Pre`. They
  now use delivered health damage observed at `.Post`, after absorption and
  downstream damage changes, with an explicit pre-hit health cap to remove
  overkill.
- A full or nearly full blood bar could still receive an oversized
  `drinkBlood` request, which fires Vampirism events and statistics for the
  requested amount. Restoration is now capped to free capacity and reports the
  actual blood delta.
- The legacy three-argument `drinkBlood` fallback can route excess blood into
  containers. The bridge now requires the four-argument overload available at
  the supported floor and fails closed if it is missing.
- Devour's mana multiplier previously affected non-vampires and ran after the
  original affordability decision. It is now vampire-only and has a scaled
  pre-cast affordability guard.
- Blood thresholds previously used integer rounding, while decisions could
  become stale between pre-cast, cast and cooldown events. The threshold is now
  an exact fractional comparison; cost and outcome are committed from the
  final `SpellOnCastEvent` mana cost and correlated only for a short lifetime.
- Holy damage doubling for Vampirism NPCs was accidentally nested under the
  vampire-player caster path. Target doubling is now independent of the caster,
  and caster reflection uses delivered holy health damage instead of the early
  `SpellDamageEvent` amount.
- A canceled holy utility spell could leave Iron's Spells additional cast data
  behind. This was especially visible for Cleanse, whose pre-cast condition
  creates a target-area entity. Cancellation now calls
  `MagicData#resetAdditionalCastData()`, whose cast-data reset discards that
  entity.
- Pending holy-heal suppression and blood-cast decisions previously had no
  complete lifecycle. Heal tokens now have tight tick correlation. Blood
  decisions normally expire in the cast tick, while Raise Dead retains its
  decision for the bounded ten-minute recast window so its delayed cooldown is
  still adjusted. That one delayed decision is stored in NeoForge's persisted
  player NBT and its remaining lifetime advances only while the player is
  online, matching the parent recast across dimension changes, relogging, and
  server restarts. Transient state is purged on logout/clone and server shutdown.
- Reflection used concrete runtime classes in hot paths and retained an unsafe
  one-argument listener-registration fallback. Contracts are now resolved
  against API interfaces once, listeners use the verified four-argument
  overload, failures are explicit, and permanent failures are logged once.
- NeoGradle's `runGameTestServer` task can return success after an early parent
  dependency loading failure. The task now removes stale logs and is finalized
  by a check for the addon's successful contract-validation marker. CI runs the
  guarded smoke test against both current and compatibility-floor parent mods.

## Residual runtime risks

This audit establishes source and build compatibility; it does not replace a
gameplay run with both parent mods loaded.

- The 11-pulse Ray of Siphoning schedule in Iron's Spells 3.16.2 changes total
  blood restored versus older releases. The configured multiplier may need a
  separate balance decision after gameplay measurement.
- Iron's Lib currently resolves as `1.21.1-2.1.0-SNAPSHOT` and declares
  NeoForge `21.1.200` as its minimum. A mutable snapshot can change without
  this repository changing, so release builds should record the resolved
  artifact and retest both the dependency floor and contracts after any cache
  refresh.
- The development runtime excludes Vampirism's transitive legacy
  `jei-1.21-neoforge` artifact and selects Iron's current
  `jei-1.21.1-neoforge` `19.21.0.247`. Do not allow both artifact ids into a
  launch: they provide the same JEI mod id and packages.
- Post-damage correlation depends on NeoForge's synchronous paired Pre/Post
  events. Nested damage is tracked, but third-party cancellation, replacement
  sources and unusual re-entrant damage still need an integration smoke test.
- Holy-heal suppression correlates target, amount and server tick. A third-party
  mod that rewrites or defers the heal can intentionally break that match; the
  token expires instead of suppressing an unrelated future heal.
- Devour's scaled affordability guard follows Iron's normal non-creative,
  mana-consuming path. Iron's optional `creativeMana` policy is internal rather
  than part of the public event contract, so a creative vampire with that option
  enabled is still checked only against Iron's original cost. A third-party
  listener that changes Devour mana later in `SpellOnCastEvent` can likewise
  make the final debit differ from the pre-cast estimate.
- The Vampirism `1.10.13` source snapshot was not the published development
  artifact at audit time. Re-check Maven availability and compare the exact
  release commit before changing the runtime version.
- There are unit tests for deterministic calculations but no automated
  gameplay GameTests. A successful compile or zero-test GameTest launch is not
  evidence that reflective registration and gameplay behavior are correct.

## Required runtime matrix

Before release, smoke-test both the compatibility floors and current runtime
versions on a client and a dedicated server. Cover at least:

- vampire and non-vampire casters;
- Vampirism NPC vampires and ordinary living targets;
- high, low and insufficient blood; creative and non-creative mana paths;
- instant, channeled, interrupted and repeated casts;
- Ray and Devour with armor, absorption, lethal overkill and a full blood bar;
- all eight blood-cost spells at multiple levels;
- holy damage, self-heal, targeted heal, area heal, utility cancellation and
  friendly fire;
- logout, death and dimension transition while cast state is pending.

The repository verification sequence is:

```bash
./gradlew test
./gradlew compileJava processResources
./gradlew resolveParentRuntime
./gradlew --no-daemon clean build
jar --list --file build/libs/vampire_spells_addon-neoforge-*.jar
```

## Verification completed for this audit

The following automated checks completed successfully on Java 21:

- `compileJava test` on the current NeoForge `21.1.200` baseline.
- `--no-daemon clean build`, including unit tests and `verifyJarContents`.
- A diagnostic `compileJava -Pneo_version=21.1.134` succeeded, but a combined
  runtime-floor launch correctly exposed Iron's Lib's NeoForge `21.1.200`
  requirement. The advertised runtime floor was raised to the first installable
  combination rather than relying on source compilation alone.
- `resolveParentRuntime` for the current parent pair (`Vampirism 1.10.12`,
  `Iron's Spells 3.16.2`) and the compatibility-floor pair (`1.10.7`,
  `3.14.3`). Both graphs contained one JEI JAR, version `19.21.0.247`.
- `runGameTestServer` loaded all 13 mods and logged successful addon contract
  validation for the current pair and for the compatibility-floor parent pair
  on NeoForge `21.1.200`. Each launch then reached NeoForge's expected `No test
  functions were given!` stop because this repository has no GameTests; Gradle
  reported `BUILD SUCCESSFUL`. The new log finalizer rejects launches that stop
  before contract validation, even when NeoGradle itself returns success.
- Manual JAR listing confirmed only addon-owned classes, expanded dependency
  ranges, matching `1.21.1-0.0.6` metadata/manifest versions, and the packaged
  MIT license.

These checks validate build, packaging, dedicated-server class loading, and
reflection resolution. They do not replace the client and gameplay matrix
above, which remains required before a release.
