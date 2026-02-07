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
Special Model Loader: https://modrinth.com/mod/special-model-loader
MCME Mod Marker: XXX

## How to update the Installer to a new version

1. Update the version for the MCME-Installer
   - Change the value of the variable "selectedVersion" in "NewInstaller.java" to the new version
   - compile the installer
   - upload the new installer .jar on the releases page on GitHub (change the file of the "Sodium quick MCME mod installer for Windows version 2.0" release)
     -> the download links don't need to be changed
2. Update the mods for the MCME-Installer
   - compile them into a .zip-File and name it MCME-Mods
   - upload the file on the releases page on GitHub (change the file of the "Mods" release (marked as Pre-release))
