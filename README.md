# Vampire Spells Addon

Vampire Spells Addon is a NeoForge compatibility mod for Minecraft 1.21.1. It
connects Iron's Spells 'n Spellbooks with Vampirism and changes selected spell
behavior for vampire players.

The integration is reflection-based: the addon compiles without either parent
mod on its compile classpath and loads their APIs only at runtime.

## Current behavior

- Ray of Siphoning keeps dealing damage and restores configurable Vampirism
  blood to a vampire caster.
- Devour restores configurable blood and applies a configurable mana-cost
  multiplier.
- Eight blood spells spend additional blood above a configurable blood
  threshold and use different high/low-blood cooldown multipliers. The current
  implementation still charges the normal Iron's Spells mana cost.
- Holy damage reflects onto vampire casters and can deal double damage to
  Vampirism NPC vampires.
- Holy healing damages vampire casters/targets and suppresses the corresponding
  heal. Holy utility spells damage a vampire caster and cancel the cast.
- Server-side tuning is generated in
  `config/vampire_spells_addon-server.toml`.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Vampirism 1.10.7 or newer
- Iron's Spells 'n Spellbooks 1.21.1-3.14.3 or newer

The development runtime currently resolves Iron's Spells `3.16.2` and
Vampirism `1.21-1.10.12`, plus their required runtime libraries.

## Build and development

Java 21 and the committed Gradle wrapper are required.

```bash
./gradlew resolveParentRuntime  # download parent mods for development runs
./gradlew --no-daemon clean build
./gradlew runClient
./gradlew runServer
```

The distributable file is written to:

```text
build/libs/vampire_spells_addon-neoforge-<version>.jar
```

`build` also verifies that parent-mod and NeoForge classes were not bundled and
that `neoforge.mods.toml` was fully expanded. There are no automated gameplay
tests yet; client and dedicated-server smoke testing are still required for a
release.

## Continuous integration and versions

GitHub Actions builds the mod after every push and pull request with Java 21.
CI derives an increasing version from the `mod_version` baseline in
`gradle.properties` plus the workflow run number, so ordinary builds do not
need version-bump commits. The main JAR is available as a workflow artifact.

## Reference sources

Ignored upstream clones live under `dependency-source/` and are used only to
verify reflective API contracts. They are not Gradle inputs and are never
packaged with the addon. Detailed maintenance instructions and the audited API
contract are in [AGENTS.md](AGENTS.md).

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
