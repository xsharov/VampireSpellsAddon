# Vampire Spells Addon

Vampire Spells Addon connects Iron's Spells 'n Spellbooks with Vampirism and
changes selected spell behavior for vampire players. Each release supports two
platforms:

- Minecraft 1.21.1 on NeoForge.
- Minecraft 1.20.1 on Forge.

The shared integration compiles without either parent mod on its compile
classpath, loads their APIs only at runtime, and does not package parent-mod
classes in either JAR. Reflection handles the parent APIs, platform adapters
handle loader events and configuration, and narrow runtime mixins replace the
mana gate and reverse the Ray animation.

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
- Server-side tuning is stored per world in
  `<world>/serverconfig/vampire_spells_addon-server.toml`.

## Requirements

| Minecraft | Loader | Java | Vampirism | Iron's Spells |
| --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge `>=21.1.200` and `<21.2` | 21 | `>=1.10.7` and `<1.11` | `>=1.21.1-3.14.3` and `<1.21.1-4` |
| 1.20.1 | Forge `>=47.4.0` and `<48` | 17 | `>=1.10.7` and `<1.11` | `>=1.20.1-3.15.0` and `<1.20.1-4` |

Minecraft 1.20.1 deliberately uses Forge rather than NeoForge: supported
Iron's Spells 3.15+ builds require the Forge 47.4 line, while the NeoForge
1.20.1 line does not provide that loader baseline.

## Support development

[Support me with a donation ✨](https://web.tribute.tg/d/Oec)

Your support motivates me to continue developing and maintaining this project. Thanks!

## Build and development

Use the committed Gradle wrapper. Gradle selects the Java 21 toolchain for the
1.21.1 target and Java 17 for the 1.20.1 target.

```bash
./gradlew test
./gradlew resolveParentRuntime
./gradlew --no-daemon clean build
./gradlew collectReleaseJars
```

Root `test`, `build`, and `resolveParentRuntime` aggregate both platforms.
Target-specific work uses qualified tasks:

```bash
./gradlew :platforms:mc1_21_1:runClient
./gradlew :platforms:mc1_21_1:runGameTestServer
./gradlew :platforms:mc1_20_1:runClient
./gradlew :platforms:mc1_20_1:runGameTestServer
```

The distributable files use the same release number and are collected in
`build/release/`:

```text
vampire_spells_addon-neoforge-1.21.1-<release>.jar
vampire_spells_addon-forge-1.20.1-<release>.jar
```

Each platform build checks its expanded loader metadata, manifest and archive
version, Java class-file level, packaged resources, and absence of bundled
parent-mod or loader classes. `runGameTestServer` additionally fails unless the
development server logs successful validation of every reflective parent-mod
contract. Its disposable platform run directory is reset before every launch,
so current and compatibility-floor profiles cannot reuse a world or config.
CI runs both profiles on both platforms. There are no automated gameplay
GameTests, so client and dedicated-server gameplay testing is still required
for a release.

## Continuous integration and versions

GitHub Actions builds and publishes the mod only after a pull request targeting
`master` is merged. Direct pushes, manual runs, and pull requests closed without
merging do not build or publish a release.

The canonical release tag remains `1.21.1-X.Y.Z`. The workflow takes the next
numeric patch after the `mod_version` baseline and existing tags, then reserves
it as a draft targeting the exact merge commit. A rerun for the same merge
reuses that version. If its build fails, a later descendant merge can safely
take over the workflow-created draft; manual or unrelated drafts are never
replaced automatically.

CI builds and smoke-tests both targets independently, verifies both JAR
digests, and publishes the draft only after both assets are present. GitHub's
generated source archives are not installable mod files; choose the attached
`neoforge-1.21.1` or `forge-1.20.1` JAR for your game and loader.

## Reference sources

Ignored upstream clones live under `dependency-source/` and are used only to
verify reflective API contracts. They are not Gradle inputs and are never
packaged with the addon. The latest source review and remaining runtime risks
are recorded in [COMPATIBILITY_AUDIT.md](COMPATIBILITY_AUDIT.md) and
[COMPATIBILITY_AUDIT_1_20_1.md](COMPATIBILITY_AUDIT_1_20_1.md). Detailed
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
