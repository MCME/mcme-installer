# MCME-Installer
Installer for the MCME Modpack (Minecraft 26.2, Fabric).

## Installing
Download the installer `.jar` from the [releases page](https://github.com/MCME/mcme-installer/releases) and run it (Java required, works on Windows, macOS and Linux). It creates a Fabric profile "MCME for 26.2" in the Minecraft launcher and downloads the mods.

## How it works
The installer is a customised fork of the [Iris Installer](https://github.com/IrisShaders/Iris-Installer). Its source is on the [`new-installer`](../../tree/new-installer) branch.
It downloads https://github.com/MCME/mcme-installer/releases/download/Mods/MCME-Mods.zip and unpacks it into the profile's mods folder.

## Included mods
See [mod_list.md](mod_list.md). The exact jars that go into `MCME-Mods.zip` are in [`Files/`](Files).

## How to update to a new Minecraft version
1. Update the mods
   - Replace the jars in `Files/` with the new versions and update `mod_list.md`.
   - Zip them into `MCME-Mods.zip` with the jars inside a top-level `mods/` folder.
   - Replace the `MCME-Mods.zip` file of the "Mods" release on GitHub (the download link stays the same).
2. Update the installer (branch `new-installer`)
   - Change the `selectedVersion` variable in `NewInstaller.java` to the new version and compile.
   - Replace the installer `.jar` on the releases page (the download links stay the same).
