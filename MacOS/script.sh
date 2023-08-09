#!/bin/bash

CURRENT_USER=$USER

DESTM="/USERS/$CURRENT_USER/Library/Application Support/minecraft/mods/"
DESTR="/USERS/$CURRENT_USER/Library/Application Support/minecraft/resourcepacks/"

SOURCEM="/Library/MCME/mods/"
SOURCER="/Library/MCME/resourcepacks/"


MODSname="mods"
RPname="resourcepacks"

DEST="/USERS/$CURRENT_USER/Library/Application Support/minecraft/"

mkdir -p "$DEST$MODSname"
mkdir -p "$DEST$RPname"

cp -r "$SOURCEM"* "$DESTM"
cp -r "$SOURCER"* "$DESTR"