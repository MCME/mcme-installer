# MCME-Installer
Installer for the MCME Modpack (Minecraft 26.2 and 26.3, Fabric).

## Installing
Download the installer `.jar` from the [releases page](https://github.com/MCME/mcme-installer/releases) and run it (Java required, works on Windows, macOS and Linux). Pick the Minecraft version in the dropdown. It creates a Fabric profile "MCME for <version>" in the Minecraft launcher and downloads the mods. Each version gets its own profile and mods folder, so 26.2 and 26.3 can be installed side by side.

## How it works
The installer is a customised fork of the [Iris Installer](https://github.com/IrisShaders/Iris-Installer). Its source is on the [`new-installer`](../../tree/new-installer) branch.
It downloads `https://github.com/MCME/mcme-installer/releases/download/Mods/MCME-Mods-<version>.zip` and unpacks it into the profile's mods folder.
Installers up to 2.1 download `MCME-Mods.zip` (26.2) instead, so keep that file in the release for them.

## Included mods
See [mod_list.md](mod_list.md). The exact jars that go into `MCME-Mods-<version>.zip` are in [`Files/<version>/`](Files).

## How to update the mods
1. Replace the jars in `Files/<version>/` with the new versions and update `mod_list.md`.
2. Zip them into `MCME-Mods-<version>.zip` with the jars inside a top-level `mods/` folder.
3. Replace that file in the "Mods" release on GitHub (the download link stays the same).

## How to add or drop a Minecraft version
1. Add or remove the `Files/<version>/` folder, its column in `mod_list.md` and its `MCME-Mods-<version>.zip` in the "Mods" release.
2. Update the installer (branch `new-installer`): change the `GAME_VERSIONS` array in `NewInstaller.java` (the first entry is the default), raise `version` in `gradle.properties` and compile.
3. Publish the installer `.jar` as a new release.

To test zips before uploading them, start the installer with `java "-Dmcme.modsUrl=file:///C:/path/to/zips/" -jar MCME-Installer-<version>.jar` (note the trailing `/`).
