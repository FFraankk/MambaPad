MambaPad for Windows 10/11 x64

Portable: extract the entire folder to a writable location and run MambaPad.exe.
Keep portable.flag, adb-path.txt, tools/adb and the license files beside the EXE.
Settings and bounded logs are stored in data/ beside the portable EXE.

Installer: run Setup, then open MambaPad from the Start menu.
Installation is for the current user and does not require administrator rights.
Settings and bounded logs are stored in %LOCALAPPDATA%/MambaPad.
Uninstall from Windows Settings > Apps. User settings/logs are retained.
If another tool is still using the bundled ADB daemon, Windows may defer
removal of its executable until restart; MambaPad never stops shared ADB.

Connect a USB data cable, enable USB debugging on the phone, and confirm its
computer authorization. Open USB mode in the Android app.
If multiple phones are connected, select the intended phone in the window.
Minimize or close the window to keep the connection in the tray.
Click the tray icon to reopen; choose Exit to release input and stop.
A second launch opens the existing instance, including across package types.

No startup task, background service, firewall rule or driver is installed.
This preview EXE and installer are not code-signed.

Debugging: -console, -dry-run, -listen-only, -adb PATH, -serial SERIAL.
Automation: -quit asks the running desktop instance to clean up and exit.
