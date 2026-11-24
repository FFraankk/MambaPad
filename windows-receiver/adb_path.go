package main

import (
	"os"
	"path/filepath"
	"strings"
)

// Versioned ADB payloads let upgrades leave a shared, running ADB daemon intact.
func bundledADB(exeDir string) string {
	raw, err := os.ReadFile(filepath.Join(exeDir, "adb-path.txt"))
	if os.IsNotExist(err) {
		return filepath.Join(exeDir, "tools", "adb", "adb.exe")
	}
	if err != nil {
		return ""
	}
	rel := filepath.Clean(strings.TrimSpace(string(raw)))
	if filepath.IsAbs(rel) || filepath.VolumeName(rel) != "" || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) || filepath.Base(rel) != "adb.exe" {
		return ""
	}
	return filepath.Join(exeDir, rel)
}
