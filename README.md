# Vampire Spells Addon

Vampire Spells Addon is a NeoForge compatibility mod for Minecraft 1.21.1. It
connects Iron's Spells 'n Spellbooks with Vampirism and changes selected spell
behavior for vampire players.

The integration compiles without either parent mod on its compile classpath,
loads their APIs only at runtime, and does not package parent-mod classes in its
JAR. Reflection handles the parent APIs, while two narrowly scoped runtime
mixins replace the mana gate and reverse the Ray animation.

## Current behavior

- Ray of Siphoning and Devour restore configurable Vampirism blood to a vampire
  caster from delivered health damage. The amount is observed after absorption
  and other damage processing, excludes overkill by capping it to the target's
  health before the hit, and never requests more blood than the caster can hold.
- By default, a vampire pays mana for every mana-consuming Blood School spell.
  When the full mana price cannot be covered, every such spell except Ray of
  Siphoning falls back to an atomic blood payment and spends no mana. If the
  blood price cannot be paid in full, the spell effect is stopped without a
  partial debit.
- `alwaysUseBloodForVampireBloodSpells = true` changes those non-Ray casts to
  blood-only mode even while mana is available. Creative casts, non-mana cast
  sources, and recasts keep their normal free-resource behavior. Ray of
  Siphoning always keeps its normal mana cost and continues to restore blood.
- Devour's configurable mana multiplier still applies only to vampire casters;
  its scaled final price is also the basis for a blood payment. Every Blood
  School spell cast by a vampire uses
  `vampireBloodSpellCooldownMultiplier`, which defaults to `2/3` for a
  cooldown that is 1.5 times shorter.
- A vampire's Ray of Siphoning animates toward the caster rather than toward the
  target. Its guide text also explains the spell's night-creature origin in all
  languages supported by Iron's Spells.
- Holy spell damage dealt to a Vampirism NPC vampire is doubled regardless of
  who cast it. Delivered holy health damage reflects onto a vampire player
  caster.
- Holy healing damages vampire casters/targets and suppresses the corresponding
  heal. Holy utility spells damage a vampire caster, clear any targeting data
  created during pre-cast checks, and cancel the cast.
- Cast and heal correlation state is bounded and cleared on lifecycle
  boundaries so nested or interrupted actions cannot affect an unrelated later
  action.
- Server-side tuning is generated in
  `config/vampire_spells_addon-server.toml`.

## Requirements

- Minecraft 1.21.1
- NeoForge `>=21.1.200` and `<21.2`
- Vampirism `>=1.10.7` and `<1.11`
- Iron's Spells 'n Spellbooks `>=1.21.1-3.14.3` and `<1.21.1-4`

The development runtime currently resolves Iron's Spells `3.16.2` and
Vampirism `1.21-1.10.12`, plus their required runtime libraries.

## Build and development

Java 21 and the committed Gradle wrapper are required.

```bash
./gradlew test
./gradlew compileJava processResources
./gradlew resolveParentRuntime
./gradlew --no-daemon clean build
jar --list --file build/libs/vampire_spells_addon-neoforge-*.jar
```

Development launches with the resolved parent-mod runtime use:

```bash
./gradlew runClient
./gradlew runServer
./gradlew runGameTestServer
```

The distributable file is written to:

```text
build/libs/vampire_spells_addon-neoforge-<version>.jar
```

`build` also checks the expanded mod metadata, manifest and archive versions,
packaged license, mixin resources, localized Ray descriptions, and absence of
bundled parent-mod or NeoForge classes. Unit tests cover deterministic resource
selection, blood and cooldown calculations, nested cast outcomes, and language
resource structure.
`runGameTestServer` additionally fails unless the development server logs a
successful validation of every reflective parent-mod contract. CI runs that
class-loading smoke check against both the current and compatibility-floor
parent versions. There are no automated gameplay GameTests, so client and
dedicated-server gameplay testing is still required for a release.

## Continuous integration and versions

GitHub Actions builds and publishes the mod only after a pull request targeting
`master` is merged. Direct pushes, manual runs, and pull requests closed without
merging do not build or publish a release.

The release workflow takes the next numeric patch after the `mod_version`
baseline and existing release tags in the same version line, then reserves it
as a draft targeting the exact merge commit. A rerun for the same merge reuses
that version. If its build fails, a later descendant merge safely takes over
the workflow-created draft and reuses the reserved version; manual or unrelated
drafts are never replaced automatically. After the full build and both
parent-version smoke tests pass, CI uploads the verified main JAR and publishes
the release. GitHub's automatically
generated source ZIP and TAR archives are not installable mod files; use the
attached `vampire_spells_addon-neoforge-*.jar`.

## Reference sources

Ignored upstream clones live under `dependency-source/` and are used only to
verify reflective API contracts. They are not Gradle inputs and are never
packaged with the addon. The latest source review and remaining runtime risks
are recorded in [COMPATIBILITY_AUDIT.md](COMPATIBILITY_AUDIT.md). Detailed
maintenance instructions and durable contracts are in [AGENTS.md](AGENTS.md).

## License

Vampire Spells Addon is available under the MIT License. Iron's Spells,
Vampirism, and their assets remain under their respective upstream licenses.

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

Run MemPalace through the project launcher. The launcher owns the local paths,
environment variables, and `--palace` argument.

macOS:

```bash
./.mempalace/mempalace.sh status
```

Windows:

```powershell
.\.mempalace\mempalace.ps1 status
```

Codex MCP should use the stable wrapper in Codex home. If MemPalace was changed
outside a running MCP session, reconnect or restart the Codex task before
relying on memory reads.
<!-- LOCAL_MEMPALACE_MODULE_END -->
