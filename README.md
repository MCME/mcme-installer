# MCME-Installer
Installer for the MCME Modpack

## Installer:

Uses the Iris-Installer created by IMS. Usable on Windows, Mac and Linux.

## Included mods:

Continuity: https://modrinth.com/mod/continuity \
Fabric API: https://modrinth.com/mod/fabric-api \
Indium: https://modrinth.com/mod/indium \
Iris: https://modrinth.com/mod/iris \
LambDynamicLights: https://modrinth.com/mod/lambdynamiclights \
Logical Zoom: https://modrinth.com/mod/logical-zoom \
Sodium: https://modrinth.com/mod/sodium \
Special Model Loader: https://modrinth.com/mod/special-model-loader \
MCME Mod Marker: XXX

## How to update the Installer to a new version

The installer supports several Minecraft versions at once; the player picks one in the "Minecraft version" dropdown. It installs Fabric Loader for that version and the mods from `MCME-Mods-<version>.zip`, each version with its own launcher profile ("MCME for <version>") and mods folder.

1. Update the mods for the MCME-Installer
   - compile them into a .zip-File named `MCME-Mods-<version>.zip` (e.g. `MCME-Mods-26.3.zip`), with the jars inside a top-level `mods/` folder
   - upload the file on the releases page on GitHub (add it to / replace it in the "Mods" release (marked as Pre-release))
2. Update the versions of the MCME-Installer (only when adding or dropping a Minecraft version)
   - Change the `GAME_VERSIONS` array in "NewInstaller.java" (the first entry is the default)
   - compile the installer
   - upload the new installer .jar on the releases page on GitHub (change the file of the "Sodium quick MCME mod installer for Windows version 2.0" release)
     -> the download links don't need to be changed
