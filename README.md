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

1. Update the version for the MCME-Installer
   - Change the value of the variable "selectedVersion" in "NewInstaller.java" to the new version
   - compile the installer
   - upload the new installer .jar on the releases page on GitHub (change the file of the "Sodium quick MCME mod installer for Windows version 2.0" release)
     -> the download links don't need to be changed
2. Update the mods for the MCME-Installer
   - compile them into a .zip-File and name it MCME-Mods
   - upload the file on the releases page on GitHub (change the file of the "Mods" release (marked as Pre-release))

## Shader pack edits (Mordor's fire eye)

Under an Iris shader pack the resource pack's own shaders don't run, so Mordor's fire eye needs an edited copy of the shader pack. After installing (or from the "Shader packs…" button) the installer lists the shader packs in `<game folder>/shaderpacks` that it knows:

- a tick box: this version can be edited. Ticked packs are copied, edited and saved next to the original as `<name>-MCME-edit` (a zip stays a zip, a folder a folder), with a copy of its Iris settings file (`<name>.zip.txt` → `<name>-MCME-edit.zip.txt`). An edit made by the installer is replaced when it's made again; anything else under that name is replaced only if the player agrees.
- a download button: the pack is known, but this version can't be edited. It downloads the version that can from Modrinth (checked by SHA-1) into `shaderpacks`, after which it can be ticked.

Supported: Bliss, BSL, Complementary Reimagined and Unbound, Spooklementary, MakeUp - Ultra Fast, Mellow, Solas and Sildur's Vibrant.

The edits are a Java port of `patch_shaderpack.py` (MCME/ResourcePackScripts, `patchShaderpack/`), in `src/main/java/net/hypercubemc/iris_installer/shaders/ShaderPackPatcher.java`. It writes exactly what the script writes, so **a change to a recipe belongs in both**. To check that they still agree, patch an unpacked shader pack with each and compare the folders (only `MCME-PATCH.txt` differs):

    python patch_shaderpack.py <pack.zip> <RP-Mordor> --out py-out
    java -cp MCME-Installer-<version>.jar net.hypercubemc.iris_installer.shaders.ShaderPackPatcher <unpacked pack folder>

The window can be opened on its own for testing:

    java -cp MCME-Installer-<version>.jar net.hypercubemc.iris_installer.shaders.ShaderPacksDialog [game folder]

### How to update it

- **The eye changed** (its look, position or reach): copy `far_terrain.glsl`, `fire_eye_config.glsl` and `fire_eye.glsl` from RP-Mordor's `assets/minecraft/shaders/include` into `src/main/resources/mcme/fire_eye/`. The eye's block is read from `FIRE_EYE_BLOCK` in `fire_eye_config.glsl`.
- **A shader pack released a new version**: patch it with the script (or the command above). If it works, update its entry in `FAMILIES` in `ShaderPacks.java` - version, file name, Modrinth CDN URL and SHA-1 (all from `https://api.modrinth.com/v2/project/<slug>/version`). If it doesn't, the recipe needs fixing first, in both places.
- Then compile and release the installer as above.
