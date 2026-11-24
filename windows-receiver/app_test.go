package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"unsafe"
)

func TestPortableAndInstalledDataDirectories(t *testing.T) {
	dir := t.TempDir()
	exe := filepath.Join(dir, "MambaPad.exe")
	local := t.TempDir()
	got, err := dataDirectory(exe, local)
	if err != nil || got != filepath.Join(local, "MambaPad") {
		t.Fatal(got, err)
	}
	if err = os.WriteFile(filepath.Join(dir, "portable.flag"), nil, 0600); err != nil {
		t.Fatal(err)
	}
	got, err = dataDirectory(exe, local)
	if err != nil || got != filepath.Join(dir, "data") {
		t.Fatal(got, err)
	}
}
func TestDeviceListFiltersNonUSBAndKeepsStableIdentity(t *testing.T) {
	list := deviceList("List of devices attached\na device model:Mi_10 transport_id:1\nb unauthorized\n192.168.1.2:5555 device model:Other\nemulator-5554 device\nc device model:Pixel_9\n")
	if len(list) != 2 || list[0].Serial != "a" || list[0].Label != "Mi 10 · USB 1" || list[1].Serial != "c" {
		t.Fatal(list)
	}
}
func TestSettingsSurviveReplacementAndDeviceChanges(t *testing.T) {
	a := &desktopApp{dataDir: t.TempDir(), selectDevice: make(chan string, 1)}
	a.choose("first")
	a.choose("second")
	if got := <-a.selectDevice; got != "second" {
		t.Fatal(got)
	}
	raw, err := os.ReadFile(filepath.Join(a.dataDir, "settings.json"))
	if err != nil || !strings.Contains(string(raw), "second") {
		t.Fatal(string(raw), err)
	}
}
func TestLogsRotateAtBound(t *testing.T) {
	dir := t.TempDir()
	l, err := openLog(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer l.Close()
	for i := 0; i < 300; i++ {
		if _, err = l.Write([]byte(strings.Repeat("x", 1024))); err != nil {
			t.Fatal(err)
		}
	}
	for _, name := range []string{"receiver.log", "receiver.log.1"} {
		st, err := os.Stat(filepath.Join(dir, name))
		if err != nil || st.Size() > 256*1024 {
			t.Fatal(name, st, err)
		}
	}
}
func TestNativeStructureLayouts(t *testing.T) {
	// This release targets Windows x64; incorrect padding corrupts tray callbacks.
	if unsafe.Sizeof(uintptr(0)) == 8 && (unsafe.Sizeof(notifyData{}) != 976 || unsafe.Sizeof(winClass{}) != 80 || unsafe.Sizeof(winMessage{}) != 48) {
		t.Fatal(unsafe.Sizeof(notifyData{}), unsafe.Sizeof(winClass{}), unsafe.Sizeof(winMessage{}))
	}
	if unsafe.Sizeof(uintptr(0)) == 8 && (unsafe.Sizeof(paintData{}) != 72 || unsafe.Sizeof(drawItemData{}) != 64) {
		t.Fatal("Native paint structure padding", unsafe.Sizeof(paintData{}), unsafe.Sizeof(drawItemData{}))
	}
}

func TestBundledADBMarkerStaysWithinPackage(t *testing.T) {
	dir := t.TempDir()
	if got := bundledADB(dir); got != filepath.Join(dir, "tools", "adb", "adb.exe") {
		t.Fatal(got)
	}
	for _, rel := range []string{"../adb.exe", "C:\\other\\adb.exe", "tools/adb/other.exe"} {
		os.WriteFile(filepath.Join(dir, "adb-path.txt"), []byte(rel), 0600)
		if got := bundledADB(dir); got != "" {
			t.Fatal(rel, got)
		}
	}
	os.WriteFile(filepath.Join(dir, "adb-path.txt"), []byte("tools/adb/version/adb.exe\r\n"), 0600)
	if got := bundledADB(dir); got != filepath.Join(dir, "tools", "adb", "version", "adb.exe") {
		t.Fatal(got)
	}
}

func TestConnectionMessagesExplainRecovery(t *testing.T) {
	for _, m := range []string{"Multiple phones", "Unlock phone", "USB ready", "Waiting for phone", "Cannot inspect", "Cannot create", "ADB error"} {
		title, detail := connectionStatus(m)
		if title == "" || detail == "" {
			t.Fatal(m, title, detail)
		}
	}
}
