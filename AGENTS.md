# AGENTS.md

## Project Snapshot
- **Name**: Vampire Spells Addon
- **Package**: `com.vampirespells.addon`
- **Version**: 1.21.1-0.0.3
- **Status**: ✅ Production ready
- **Integration**: Reflection-only bridge between Iron's Spells 'n Spellbooks (1.21.1-3.14.3) and Vampirism (1.10.7)

## Gameplay Changes
- **Ray of Siphoning** now keeps its damage while restoring blood equal to the dealt damage for vampire casters.
- **Devour** restores double the dealt damage as blood and now costs 2× its original mana.
- **Blood Spell Surcharge**: the following spells charge blood in addition to mana when cast by vampires, at a rate of 1 blood per 20 mana (rounded up):
  `wither_skull`, `sacrifice`, `raise_dead`, `heartstop`, `blood_step`, `blood_slash`, `blood_needles`, `acupuncture`.
  - Casts are cancelled if the vampire cannot afford the blood cost (non-vampires unaffected).
- All logic is runtime-detected through NeoForge events; no parent APIs are linked at compile time.

## Technical Notes
- **Event Hooks**:
  - `LivingDamageEvent.Pre` (NeoForge) handles Ray/Devour blood restoration.
  - `SpellPreCastEvent` and `SpellOnCastEvent` are attached reflectively (using `NeoForge.EVENT_BUS.addListener`) to enforce costs without compile-time dependencies.
- **Reflection Helpers**: All access to Vampirism (`VampirismAPI`, `IVampirePlayer`, `IDrinkBloodContext`) and Iron's Spells (`SpellRegistry`, spell metadata) is performed via cached reflection lookups.
- **Blood Cost Logic**: `ceil(manaCost / 20f)` with a minimum of 1. Cost is only deducted when the cast successfully fires.
- **Mana Adjustments**: Devour's mana cost is doubled via `SpellOnCastEvent#setManaCost` if it has not already been modified by other mods.

## Build & Run
```bash
./gradlew build              # build
./gradlew runClient          # client
./gradlew runData            # data gen
./gradlew runGameTestServer  # tests
```
- Build output: `build/libs/vampire_spells_addon-1.21.1-0.0.3.jar`
- Copy helper (if desired): `cp build/libs/vampire_spells_addon-*.jar ./VampireSpellsAddon.jar`

## Development Guardrails
1. **Do not add** compile-time dependencies on either parent mod.
2. **Stay in our namespace** (`com.vampirespells.addon.*`). No `io.redspace.*`, `de.teamlapen.*`, or `net.neoforged.*` classes.
3. **Reuse reflection helpers** inside `SpellEventHandler` for any future integrations.
4. **Preserve dynamic event registration**; do not hard-link to Iron's Spells classes at compile time.
5. **Jar hygiene**: confirm final jar only contains our classes and metadata when shipping (`jar -tf`).

## Validation Checklist
- [ ] `./gradlew build` succeeds.
- [ ] Ray of Siphoning damages targets and restores vampire blood per hit.
- [ ] Devour heals vampire blood for double damage and consumes double mana.
- [ ] Blood-cost spells deduct blood according to the 1:20 ratio and refuse to cast if insufficient blood is available.
- [ ] Blood deductions apply only to vampires; non-vampires behave normally.
- [ ] Reflection listeners register without logging errors.
- [ ] Log output shows informative messages for blood gains/losses.

## Troubleshooting
- **Missing parent mods**: Startup logs will report missing APIs; addon should fail gracefully.
- **Blood not deducting**: verify Vampirism API method names (obfuscated updates may change signatures) and the 1:20 ratio math.
- **Cast not blocked**: ensure `SpellPreCastEvent` listener is firing; check reflective registration logs.
- **Devour mana unchanged**: confirm no other mods set the mana cost larger than ours before we run.

## Build Artifact Verification
```bash
jar -tf build/libs/vampire_spells_addon-1.21.1-0.0.3.jar
```
Expect only our package and NeoForge descriptors (`META-INF/neoforge.mods.toml`).
