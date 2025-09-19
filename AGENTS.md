# AGENTS.md

## Project Snapshot
- **Name**: Vampire Spells Addon
- **Package**: `com.vampirespells.addon`
- **Version**: 1.21.1-0.0.6
- **Status**: ✅ Production ready
- **Integration**: Reflection-only bridge between Iron's Spells 'n Spellbooks (1.21.1-3.14.3) and Vampirism (1.10.7)

- **Ray of Siphoning** keeps its damage and restores blood using the configurable `rayBloodRestoreMultiplier` and `rayBloodSaturation` values.
- **Devour** restores blood scaled by `devourBloodRestoreMultiplier`, applies `devourBloodSaturation`, and multiplies its mana cost by `devourManaMultiplier`.
- **Blood Spell Threshold**: `wither_skull`, `sacrifice`, `raise_dead`, `heartstop`, `blood_step`, `blood_slash`, `blood_needles`, and `acupuncture` now:
  - Spend blood (derived from the mana cost curve) and apply the `highBloodCooldownMultiplier` when the vampire has at least `highBloodThresholdFraction` of their maximum blood.
  - Skip the blood drain and apply the `lowBloodCooldownMultiplier` when below the threshold, relying on mana only.
- **Holy School Backlash**:
  - Holy damage spells reflect their damage back onto vampire casters and deal double damage to Vampirism NPC vampires.
  - Holy heals translate into damage for vampire recipients and vampire casters; heals on vampires are fully suppressed via `LivingHealEvent`.
  - Holy utility/buff spells (`angel_wing`, `fortify`, `wisp`, `haste`, `cleanse`, `sunbeam`) simply inflict 5 damage on vampire casters and the cast is cancelled.
- Blood cost per spell level is computed from the mana floor/ceiling span using `bloodCostRatioMin` → `bloodCostRatioMax`; setting both to the same value recreates a flat ratio.
- All logic is runtime-detected through NeoForge events; no parent APIs are linked at compile time.

## Technical Notes
- **Event Hooks**:
  - `LivingDamageEvent.Pre` (NeoForge) handles Ray/Devour blood restoration.
  - `SpellPreCastEvent`, `SpellOnCastEvent`, and `SpellCooldownAddedEvent.Pre` are attached reflectively to apply blood usage and cooldown multipliers.
  - `SpellDamageEvent`, `SpellHealEvent`, and `LivingHealEvent` impose holy-school backlash on vampires.
- **Reflection Helpers**: All access to Vampirism (`VampirismAPI`, `IVampirePlayer`, `IDrinkBloodContext`) and Iron's Spells (`SpellRegistry`, spell metadata) is performed via cached reflection lookups.
- **Blood Cost Logic**: Mana cost is mapped onto the `[bloodCostManaFloor, bloodCostManaCeiling]` band, blending between `bloodCostRatioMin` and `bloodCostRatioMax`, then rounded up.
- **Cooldown Scaling**: High-blood casts use `highBloodCooldownMultiplier`, low-blood casts use `lowBloodCooldownMultiplier` before NeoForge applies the cooldown.
- **Mana Adjustments**: Devour's mana cost is multiplied by `devourManaMultiplier` via `SpellOnCastEvent#setManaCost`.

## Configuration
- A server config file (`config/vampire_spells_addon-server.toml`) is generated automatically.
- `blood_spells.bloodCostManaFloor`: Lowest mana cost used when normalising mana → blood ratios.
- `blood_spells.bloodCostManaCeiling`: Highest mana cost used when normalising mana → blood ratios.
- `blood_spells.bloodCostRatioMin`: Blood-per-mana ratio at or below the mana floor.
- `blood_spells.bloodCostRatioMax`: Blood-per-mana ratio at or above the mana ceiling.
- `blood_spells.highBloodThresholdFraction`: Fraction of max blood that qualifies for the high-blood casting state.
- `blood_spells.highBloodCooldownMultiplier`: Cooldown multiplier applied in the high-blood state.
- `blood_spells.lowBloodCooldownMultiplier`: Cooldown multiplier applied when below the threshold (blood is not spent).
- `blood_spells.devourManaMultiplier`: Multiplier applied to Devour's mana cost.
- `blood_spells.devourBloodRestoreMultiplier`: Damage → blood multiplier for Devour.
- `blood_spells.devourBloodSaturation`: Saturation modifier used when Devour restores blood.
- `blood_spells.rayBloodRestoreMultiplier`: Damage → blood multiplier for Ray of Siphoning.
- `blood_spells.rayBloodSaturation`: Saturation modifier used when Ray of Siphoning restores blood.

## Build & Run
```bash
./gradlew build              # build
./gradlew runClient          # client
./gradlew runData            # data gen
./gradlew runGameTestServer  # tests
```
- Build output: `build/libs/vampire_spells_addon-1.21.1-0.0.6.jar`
- Copy helper (if desired): `cp build/libs/vampire_spells_addon-*.jar ./VampireSpellsAddon.jar`

## Development Guardrails
1. **Do not add** compile-time dependencies on either parent mod.
2. **Stay in our namespace** (`com.vampirespells.addon.*`). No `io.redspace.*`, `de.teamlapen.*`, or `net.neoforged.*` classes.
3. **Reuse reflection helpers** inside `SpellEventHandler` for any future integrations.
4. **Preserve dynamic event registration**; do not hard-link to Iron's Spells classes at compile time.
5. **Jar hygiene**: confirm final jar only contains our classes and metadata when shipping (`jar -tf`).

## Validation Checklist
- [ ] `./gradlew build` succeeds.
- [ ] Ray of Siphoning restores blood at the configured multiplier and saturation.
- [ ] Devour heals blood/mana in line with the config multipliers.
- [ ] High-blood vampire casts spend blood and apply the high-blood cooldown multiplier.
- [ ] Low-blood vampire casts skip the blood drain and apply the low-blood cooldown multiplier.
- [ ] Holy damage spells reflect damage back onto vampire casters and deal 2× damage to Vampirism NPC vampires.
- [ ] Holy heals damage vampire casters/targets and the underlying heals are suppressed via `LivingHealEvent`.
- [ ] Holy utility spells (`angel_wing`, `fortify`, `wisp`, `haste`, `cleanse`, `sunbeam`) deal 5 damage to vampire casters and the cast is cancelled.
- [ ] Non-vampires continue to cast normally without blood/holy penalties.
- [ ] Reflection listeners register without logging errors.
- [ ] Log output shows informative messages for blood gains/losses and holy backlash.

## Troubleshooting
- **Missing parent mods**: Startup logs will report missing APIs; addon should fail gracefully.
- **Unexpected blood usage**: verify threshold/config values; ensure Vampirism API method names still match (`getBloodStats`, `useBlood`).
- **Holy backlash missing**: confirm `SpellDamageEvent`/`SpellHealEvent` listeners register (debug log) and that the spell ID is holy.
- **Cooldown not scaling**: confirm the `SpellCooldownAddedEvent.Pre` listener registers (look for debug log).
- **Devour mana unchanged**: confirm no other mods set the mana cost after our handler, or adjust `devourManaMultiplier`.

## Build Artifact Verification
```bash
jar -tf build/libs/vampire_spells_addon-1.21.1-0.0.6.jar
```
Expect only our package and NeoForge descriptors (`META-INF/neoforge.mods.toml`).
