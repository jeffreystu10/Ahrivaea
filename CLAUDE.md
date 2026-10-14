# Working on Ahrivaea

- The project builds several separate mods (one jar each) from one codebase. `mods.json` is the list:
  each entry's `include` patterns say which code and files go in that mod's jar.
- **Every time a mod is added or changed**, update its entry in `mods.json` (summary, features, commands,
  status) and send the owner the regenerated mods spreadsheet: `python3 tools/mods_sheet.py Ahrivaea_Mods.xlsx`.
  CI also publishes it, with every jar, to the `latest` GitHub release.
- A new mod gets its own package under `dev/ahrivaea/<mod>/` with its own `ModInitializer`, and must not use
  another mod's classes (CI's `tools/check_jars.py` fails if it does, or if a built file isn't in any jar).
- Minecraft 26.3, Fabric, Mojang names. The dev `fabric.mod.json` loads all mods together for testing.
- CI (`.github/workflows/build.yml`) builds, checks the jars, runs a server test and client game tests that take
  screenshots (pushed to the `ci-screenshots` branch).
