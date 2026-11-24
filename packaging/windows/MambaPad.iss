#ifndef AppVersion
  #error AppVersion is required
#endif
#ifndef PayloadDir
  #error PayloadDir is required
#endif
#ifndef OutputDir
  #error OutputDir is required
#endif

[Setup]
AppId={{9F7590EE-53E3-4F7B-9DA1-9DBD0477CD30}
AppName=MambaPad
AppVersion={#AppVersion}
AppPublisher=MambaPad
DefaultDirName={localappdata}\Programs\MambaPad
DefaultGroupName=MambaPad
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
DisableProgramGroupPage=yes
OutputDir={#OutputDir}
OutputBaseFilename=MambaPad-Windows-x64-{#AppVersion}-Setup
SetupIconFile=..\..\branding\mambapad.ico
UninstallDisplayIcon={app}\MambaPad.exe
LicenseFile=..\..\LICENSE
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
CloseApplicationsFilter=MambaPad.exe
RestartApplications=no
UninstallLogging=yes

[Files]
Source: "{#PayloadDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "portable.flag,data\*,tools\*"
Source: "{#PayloadDir}\tools\adb\*"; DestDir: "{app}\tools\adb"; Flags: onlyifdoesntexist recursesubdirs createallsubdirs

[Icons]
Name: "{group}\MambaPad"; Filename: "{app}\MambaPad.exe"
Name: "{group}\Uninstall MambaPad"; Filename: "{uninstallexe}"

[Run]
Filename: "{app}\MambaPad.exe"; Description: "Launch MambaPad"; Flags: nowait postinstall skipifsilent

[Code]
function StopReceiver(const ExePath: String): Boolean;
var Code: Integer;
begin
  Result := True;
  if FileExists(ExePath) then
    Result := Exec(ExePath, '-quit', '', SW_HIDE, ewWaitUntilTerminated, Code) and (Code = 0);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if not StopReceiver(ExpandConstant('{app}\MambaPad.exe')) then
    Result := 'Please exit MambaPad from its tray menu before upgrading.';
  if FileExists(ExpandConstant('{app}\portable.flag')) then
    Result := 'This folder contains a portable edition. Choose a different installation folder.';
end;

function InitializeUninstall(): Boolean;
begin
  Result := StopReceiver(ExpandConstant('{app}\MambaPad.exe'));
  if not Result then
    MsgBox('Please exit MambaPad from its tray menu before uninstalling.', mbError, MB_OK);
end;

// Preferences and bounded logs live outside {app}; keep them on uninstall.
