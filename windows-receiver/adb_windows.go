package main

import (
	"context"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

func adbCommand(ctx context.Context, path string, args ...string) (string, error) {
	child, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	cmd := exec.CommandContext(child, path, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	out, err := cmd.CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

func chooseDevice(output, serial string) (string, string) {
	devices := []string{}
	unauthorized := false
	for _, line := range strings.Split(output, "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 || strings.HasPrefix(fields[0], "emulator-") || strings.Contains(fields[0], ":") || strings.Contains(fields[0], "_adb-tls-") {
			continue
		}
		if serial != "" && fields[0] != serial {
			continue
		}
		if fields[1] == "unauthorized" {
			unauthorized = true
		}
		if fields[1] == "device" {
			devices = append(devices, fields[0])
		}
	}
	if len(devices) == 1 {
		return devices[0], ""
	}
	if len(devices) > 1 {
		return "", "Multiple phones: restart with -serial DEVICE / 多台手机，请指定序列号"
	}
	if unauthorized {
		return "", "Unlock phone and allow USB debugging / 请解锁手机并允许 USB 调试"
	}
	return "", "Waiting for phone: check data cable, USB debugging and OEM driver / 等待手机连接"
}

func manageADB(ctx context.Context, path, serial string) {
	manageADBWithStatus(ctx, path, serial, nil)
}

func manageADBWithStatus(ctx context.Context, path, serial string, app *desktopApp) {
	if path == "" {
		exe, _ := os.Executable()
		path = bundledADB(filepath.Dir(exe))
	}
	if _, err := os.Stat(path); err != nil {
		log.Print("ADB executable missing")
		if app != nil {
			app.update(func(s *appSnapshot) {
				s.Status = "未找到 ADB"
				s.Detail = "请保留 EXE 同目录的 tools/adb 文件夹，或重新安装完整程序包。"
			})
		}
		return
	}
	previous := ""
	active := ""
	owned := map[string]bool{}
	defer func() {
		for device := range owned {
			// Remove only our mapping if it still points at this service.
			out, err := adbCommand(context.Background(), path, "-s", device, "reverse", "--list")
			if err == nil && hasMapping(out) {
				adbCommand(context.Background(), path, "-s", device, "reverse", "--remove", "tcp:27183")
			}
		}
	}()
	for ctx.Err() == nil {
		out, err := adbCommand(ctx, path, "devices", "-l")
		devices := deviceList(out)
		device, status := chooseDevice(out, serial)
		if err != nil {
			status = "ADB 启动失败，请检查随包工具和 USB 驱动"
			device = ""
		}
		if device != "" {
			existing, listErr := adbCommand(ctx, path, "-s", device, "reverse", "--list")
			if listErr != nil {
				status = "Cannot inspect USB reverse"
			} else {
				mapped := hasMapping(existing)
				if !mapped {
					// --no-rebind refuses to overwrite another tool's mapping.
					_, err = adbCommand(ctx, path, "-s", device, "reverse", "--no-rebind", "tcp:27183", "tcp:27183")
					if err != nil {
						status = "Cannot create USB tunnel; port mapping may be in use"
					} else {
						owned[device] = true
						mapped = true
					}
				}
				if mapped {
					status = "USB ready / USB 通道就绪。请打开手机的 USB (ADB) 模式。"
					if active != device {
						// No installation or settings changes. Only launch the already installed USB Activity.
						result, launchErr := adbCommand(ctx, path, "-s", device, "shell", "am", "start", "-n", "com.github.ifeel.keysjoy.usb/com.github.ifeel.keysjoy.UsbTouchpadActivity")
						if launchErr != nil || strings.Contains(result, "Error") {
							status += " App not installed or cannot open / 请先安装 APK。"
						}
						active = device
					}
				}
			}
		} else {
			active = ""
		}
		if app != nil {
			title, detail := connectionStatus(status)
			app.update(func(s *appSnapshot) {
				s.Devices = devices
				s.Status = title
				if !s.Connected {
					s.Detail = detail
				}
			})
		}
		if status != previous {
			log.Print(status)
			previous = status
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(3 * time.Second):
		}
	}
}

func hasMapping(out string) bool {
	for _, line := range strings.Split(out, "\n") {
		f := strings.Fields(line)
		if len(f) >= 3 && f[len(f)-2] == "tcp:27183" && f[len(f)-1] == "tcp:27183" {
			return true
		}
	}
	return false
}
