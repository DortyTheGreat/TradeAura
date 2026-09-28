# Changelog

Newest first. Versions follow the `vX.Y.Z` git tags; the Minecraft version is part of the jar name
(`TradeAura-1.2.3+26.1.2.jar`). Releases before `v1.0.0` used the old naming (`26.1.2.f`, `1.21.11d`, ...)
and are listed on the [Releases page](https://github.com/DortyTheGreat/TradeAura/releases).

## Unreleased

- Setting names are lowercase now (`close`, `villager-aura`, `action-delay`, ...), like the rest of Meteor.
  Saved configs are migrated on load, nothing has to be set up again.
- `fabric.mod.json` and `meteor-addon-list.json` are generated from `gradle.properties`, so
  [meteoraddons.com](https://meteoraddons.com) shows the real name, author, modules and supported versions.
- Debug messages no longer break on item names containing `%`.
- Release jars are built by GitHub Actions for every `vX.Y.Z` tag instead of being committed to the repository.
- The `buildArchive` task and the `releases/` folder are gone - `./gradlew build` puts the jar in `build/libs`.
