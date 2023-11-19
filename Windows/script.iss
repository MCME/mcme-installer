#define MyAppName "MCME Installer"
#define MyAppVersion "1.1"
#define MyAppPublisher "Minecraft Middle-Earth"
#define MyAppURL "https://www.mcmiddleearth.com/"
#define MyAppExeName "MCME-Installer.exe"

[Setup]
AppId={{8F6B0D21-9ECC-478A-9594-92445EA45782}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
CreateAppDir=yes
LicenseFile=gpl-3.0.rtf
OutputDir="Output"
DefaultDirName={userappdata}\.minecraft\{#MyAppName}
OutputBaseFilename=MCME-Installer
SetupIconFile=BaseLogo64.ico
Compression=lzma
SolidCompression=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "fabric-api-0.86.1+1.20.1.jar"; \
  DestDir: "{code:GetInstallDir}\mods"; \
  Flags: ignoreversion

Source: "iris-mc1.20.1-1.6.5.jar"; \
  DestDir: "{code:GetInstallDir}\mods"; \
  Flags: ignoreversion

Source: "logical_zoom-0.0.20.jar"; \
  DestDir: "{code:GetInstallDir}\mods"; \
  Flags: ignoreversion

Source: "Human-Sodium.zip"; \
  DestDir: "{code:GetInstallDir}\resourcepacks"; \
  Flags: ignoreversion

Source: "sodium-fabric-mc1.20.1-0.5.0.jar"; \
  DestDir: "{code:GetInstallDir}\mods"; \
  Flags: ignoreversion

Source: "special-model-loader-mc1.20-1.2.0.jar"; \
  DestDir: "{code:GetInstallDir}\mods"; \
  Flags: ignoreversion

Source: "indium-1.0.23+mc1.20.1.jar"; \
  DestDir: "{code:GetInstallDir}\mods"; \
  Flags: ignoreversion

Source: "continuity-3.0.0-beta.3+1.20.1.jar"; \
  DestDir: "{code:GetInstallDir}\mods"; \
  Flags: ignoreversion

Source: "fabric-installer-0.11.2.exe"; \
  DestDir: "{code:GetInstallDir}\MCME Installer"; \
  Flags: ignoreversion


[Run]
Filename: "{app}\fabric-installer-0.11.2.exe";

[Code]
var
  InstallDir: string;
  DataDirPage: TInputDirWizardPage;

function InitializeSetup: Boolean;
begin
  MsgBox('If you haven´t done already. Please let Minecraft version 1.20 at least run once.', mbInformation, MB_OK);
  Result := True;
end;

function GetInstallDir(Param: String): String;
begin
  Result := InstallDir;
end;

procedure InitializeWizard;
begin
  { Create the custom directory page for choosing the installation folder }
  DataDirPage := CreateInputDirPage(wpSelectDir, 'Choose Installation Folder', 'Select the folder where you want to install the application.', 'Select the folder in which to install the application, then click Next.', False, '');
  DataDirPage.Add('Minecraft Folder');
  DataDirPage.Values[0] := ExpandConstant('{userappdata}\.minecraft');
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  { After the user selects the installation folder, store the chosen directory in the "InstallDir" variable }
  if CurStep = ssInstall then
  begin
    InstallDir := DataDirPage.Values[0];
  end;
end;
