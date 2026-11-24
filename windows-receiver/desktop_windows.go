package main

import (
	"context"
	"fmt"
	"log"
	"runtime"
	"syscall"
	"time"
	"unsafe"
)

var user32 = syscall.NewLazyDLL("user32.dll")
var shell32 = syscall.NewLazyDLL("shell32.dll")
var kernel32 = syscall.NewLazyDLL("kernel32.dll")
var gdi32 = syscall.NewLazyDLL("gdi32.dll")

const windowClass = "MambaPad.Receiver.Window"
const mutexName = "Local\\MambaPad.Receiver"
const wmUpdate = 0x8001
const wmTray = 0x8002
const wmShow = 0x8003
const wmQuit = 0x8004
const wmFinished = 0x8005

func utf(s string) *uint16 { p, _ := syscall.UTF16PtrFromString(s); return p }
func proc(name string, args ...uintptr) uintptr {
	v, _, _ := user32.NewProc(name).Call(args...)
	return v
}
func showError(title, detail string) {
	proc("MessageBoxW", 0, uintptr(unsafe.Pointer(utf(detail))), uintptr(unsafe.Pointer(utf(title))), 0x10)
}
func instanceMutex() (syscall.Handle, bool, error) {
	h, _, err := kernel32.NewProc("CreateMutexW").Call(0, 1, uintptr(unsafe.Pointer(utf(mutexName))))
	if h == 0 {
		return 0, false, err
	}
	return syscall.Handle(h), err != syscall.Errno(183), nil
}
func releaseInstance(h syscall.Handle) { kernel32.NewProc("ReleaseMutex").Call(uintptr(h)) }
func existingWindow() uintptr {
	return proc("FindWindowW", uintptr(unsafe.Pointer(utf(windowClass))), 0)
}
func showExisting() {
	for i := 0; i < 40; i++ {
		if h := existingWindow(); h != 0 {
			proc("PostMessageW", h, wmShow, 0, 0)
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	showError("MambaPad 已在运行", "请从任务栏通知区域打开已有实例。若运行的是命令行接收端，请先退出它。")
}
func stopExisting() error {
	h, _, err := kernel32.NewProc("OpenMutexW").Call(0x100001, 0, uintptr(unsafe.Pointer(utf(mutexName))))
	if h == 0 {
		if err == syscall.Errno(2) {
			return nil
		}
		return err
	}
	defer syscall.CloseHandle(syscall.Handle(h))
	window := existingWindow()
	if window == 0 {
		return fmt.Errorf("running receiver has no desktop window")
	}
	proc("PostMessageW", window, wmQuit, 0, 0)
	result, _, _ := kernel32.NewProc("WaitForSingleObject").Call(h, 15000)
	if result != 0 && result != 0x80 {
		return fmt.Errorf("receiver did not finish cleanup")
	}
	releaseInstance(syscall.Handle(h))
	return nil
}

type winClass struct {
	Size, Style                        uint32
	Callback                           uintptr
	ClassExtra, WindowExtra            int32
	Instance, Icon, Cursor, Background uintptr
	Menu, Name                         *uint16
	SmallIcon                          uintptr
}
type point struct{ X, Y int32 }
type winMessage struct {
	Window         uintptr
	Message        uint32
	WParam, LParam uintptr
	Time           uint32
	Point          point
	Private        uint32
}
type notifyData struct {
	Size                uint32
	Window              uintptr
	ID, Flags, Callback uint32
	Icon                uintptr
	Tip                 [128]uint16
	State, StateMask    uint32
	Info                [256]uint16
	Version             uint32
	Title               [64]uint16
	InfoFlags           uint32
	GUID                [16]byte
	BalloonIcon         uintptr
}

func copyWide(dest []uint16, s string) {
	v := syscall.StringToUTF16(s)
	if len(v) > len(dest) {
		v = v[:len(dest)]
		v[len(v)-1] = 0
	}
	copy(dest, v)
}

type desktopWindow struct {
	app                                     *desktopApp
	hwnd, status, detail, combo, font, icon uintptr
	tray                                    bool
	taskbarCreated                          uint32
	devices                                 []usbDevice
	closing                                 bool
	dpi                                     int
	theme                                   *desktopTheme
}

var desktop *desktopWindow
var windowCallback = syscall.NewCallback(windowProc)

func (w *desktopWindow) scale(n int) uintptr { return uintptr(n * w.dpi / 96) }
func (w *desktopWindow) control(class, text string, style uintptr, x, y, width, height, id int) uintptr {
	h := proc("CreateWindowExW", 0, uintptr(unsafe.Pointer(utf(class))), uintptr(unsafe.Pointer(utf(text))), 0x50000000|style, w.scale(x), w.scale(y), w.scale(width), w.scale(height), w.hwnd, uintptr(id), 0, 0)
	proc("SendMessageW", h, 0x30, w.font, 1)
	return h
}
func (w *desktopWindow) notice(op uintptr, balloon bool) bool {
	n := notifyData{Window: w.hwnd, ID: 1, Flags: 1 | 2 | 4, Callback: wmTray, Icon: w.icon}
	n.Size = uint32(unsafe.Sizeof(n))
	copyWide(n.Tip[:], "MambaPad · "+w.summary())
	if balloon {
		n.Flags |= 0x10
		n.InfoFlags = 1
		copyWide(n.Title[:], "MambaPad 仍在运行")
		copyWide(n.Info[:], "连接保持开启。点击托盘图标打开窗口；右键选择“退出”结束程序。")
	}
	ok, _, _ := shell32.NewProc("Shell_NotifyIconW").Call(op, uintptr(unsafe.Pointer(&n)))
	if op == 0 && ok != 0 {
		n.Version = 4
		shell32.NewProc("Shell_NotifyIconW").Call(4, uintptr(unsafe.Pointer(&n)))
	}
	return ok != 0
}
func (w *desktopWindow) summary() string {
	s := w.app.read()
	if s.Connected {
		return "手机已连接"
	}
	if s.Status == "" {
		return "等待手机连接"
	}
	return s.Status
}
func (w *desktopWindow) hide() {
	if !w.tray {
		w.tray = w.notice(0, false)
	}
	if !w.tray {
		showError("无法最小化到托盘", "通知区域暂时不可用。窗口会保持打开，你仍可使用“退出”。")
		return
	}
	w.app.mu.Lock()
	first := !w.app.prefs.TrayHintShown
	w.app.prefs.TrayHintShown = true
	err := w.app.saveLocked()
	w.app.mu.Unlock()
	if first {
		w.notice(1, true)
	}
	if err != nil {
		logDesktop("无法保存托盘提示设置")
	}
	proc("ShowWindow", w.hwnd, 0)
}
func logDesktop(message string) { log.Print(message) }
func (w *desktopWindow) show()  { proc("ShowWindow", w.hwnd, 9); proc("SetForegroundWindow", w.hwnd) }
func (w *desktopWindow) refresh() {
	s := w.app.read()
	status := s.Status
	if s.Connected {
		status = "手机已连接"
	}
	if w.closing {
		status = "正在释放输入并退出…"
	}
	proc("SetWindowTextW", w.status, uintptr(unsafe.Pointer(utf(status))))
	detail := s.Detail
	if detail == "" {
		detail = "用数据线连接手机，授权后即可开始。"
	}
	if s.Connected {
		w.app.mu.Lock()
		serial := w.app.prefs.Serial
		w.app.mu.Unlock()
		for _, device := range s.Devices {
			if serial == device.Serial || len(s.Devices) == 1 {
				detail = "当前设备：" + device.Label
				break
			}
		}
	}
	proc("SetWindowTextW", w.detail, uintptr(unsafe.Pointer(utf(detail))))
	same := len(w.devices) == len(s.Devices)
	if same {
		for i := range w.devices {
			if w.devices[i] != s.Devices[i] {
				same = false
				break
			}
		}
	}
	if !same || w.devices == nil {
		w.devices = append([]usbDevice{}, s.Devices...)
		proc("SendMessageW", w.combo, 0x14B, 0, 0)
		proc("SendMessageW", w.combo, 0x143, 0, uintptr(unsafe.Pointer(utf("自动选择（仅连接一台手机时）"))))
		w.app.mu.Lock()
		selected := w.app.prefs.Serial
		w.app.mu.Unlock()
		index := 0
		for i, d := range w.devices {
			proc("SendMessageW", w.combo, 0x143, 0, uintptr(unsafe.Pointer(utf(d.Label))))
			if d.Serial == selected {
				index = i + 1
			}
		}
		// Keep an explicit missing selection visible; never quietly fall back to another phone.
		if selected != "" && index == 0 {
			proc("SendMessageW", w.combo, 0x143, 0, uintptr(unsafe.Pointer(utf("已选设备未连接"))))
			index = len(w.devices) + 1
		}
		proc("SendMessageW", w.combo, 0x14E, uintptr(index), 0)
	}
	if w.tray {
		w.notice(1, false)
	}
	proc("InvalidateRect", w.hwnd, 0, 0)
}
func (w *desktopWindow) requestQuit() {
	if w.closing {
		return
	}
	w.closing = true
	w.refresh()
	w.app.quit()
	go func() { <-w.app.done; proc("PostMessageW", w.hwnd, wmFinished, 0, 0) }()
}
func (w *desktopWindow) menu() {
	menu := proc("CreatePopupMenu")
	defer proc("DestroyMenu", menu)
	proc("AppendMenuW", menu, 0x2|0x1, 0, uintptr(unsafe.Pointer(utf(w.summary()))))
	proc("AppendMenuW", menu, 0x800, 0, 0)
	proc("AppendMenuW", menu, 0, 101, uintptr(unsafe.Pointer(utf("打开窗口"))))
	proc("AppendMenuW", menu, 0, 103, uintptr(unsafe.Pointer(utf("退出"))))
	var p point
	proc("GetCursorPos", uintptr(unsafe.Pointer(&p)))
	proc("SetForegroundWindow", w.hwnd)
	cmd := proc("TrackPopupMenu", menu, 0x100|0x2, uintptr(p.X), uintptr(p.Y), 0, w.hwnd, 0)
	if cmd == 101 {
		w.show()
	}
	if cmd == 103 {
		w.requestQuit()
	}
	proc("PostMessageW", w.hwnd, 0, 0, 0)
}
func windowProc(hwnd uintptr, msg uint32, wp, lp uintptr) uintptr {
	w := desktop
	if w == nil {
		return proc("DefWindowProcW", hwnd, uintptr(msg), wp, lp)
	}
	if msg == w.taskbarCreated && msg != 0 {
		w.tray = w.notice(0, false)
		if !w.tray {
			w.show()
		}
		return 0
	}
	switch msg {
	case 0xf: // WM_PAINT
		w.paint()
		return 0
	case 0x14: // WM_ERASEBKGND; paint the complete background in WM_PAINT.
		return 1
	case 0x138, 0x133, 0x134: // Static/read-only text, edit, dropdown list.
		return w.colorControl(wp, lp)
	case 0x2b: // WM_DRAWITEM
		if lp != 0 {
			// Copy the Win32-owned payload while the callback is active.
			var item drawItemData
			kernel32.NewProc("RtlMoveMemory").Call(uintptr(unsafe.Pointer(&item)), lp, unsafe.Sizeof(item))
			if item.Type == 4 {
				w.drawButton(&item)
				return 1
			}
		}
	case 0x10:
		w.hide()
		return 0 // close keeps USB running
	case 0x112:
		if wp&0xfff0 == 0xf020 {
			w.hide()
			return 0
		}
	case wmUpdate:
		w.refresh()
		return 0
	case wmShow:
		w.show()
		return 0
	case wmQuit:
		w.requestQuit()
		return 0
	case wmFinished:
		w.app.mu.Lock()
		w.app.notify = nil
		w.app.mu.Unlock()
		if w.tray {
			w.notice(2, false)
			w.tray = false
		}
		proc("DestroyWindow", hwnd)
		return 0
	case 0x2:
		proc("PostQuitMessage", 0)
		return 0
	case 0x11:
		return 1 // WM_QUERYENDSESSION: cleanup only once shutdown is committed
	case 0x16:
		if wp != 0 {
			w.app.quit()
			select {
			case <-w.app.done:
			case <-time.After(4 * time.Second):
			}
			if w.tray {
				w.notice(2, false)
			}
		}
		return 0
	case wmTray:
		event := uint32(lp) & 0xffff
		if event == 0x400 || event == 0x401 || event == 0x202 {
			w.show()
		}
		if event == 0x7b || event == 0x205 {
			w.menu()
		}
		return 0
	case 0x111:
		id := wp & 0xffff
		notification := wp >> 16
		switch id {
		case 101:
			w.show()
		case 102:
			w.hide()
		case 103:
			w.requestQuit()
		case 104:
			if notification == 1 && !w.closing {
				idx := int(proc("SendMessageW", w.combo, 0x147, 0, 0))
				if idx == 0 {
					w.app.choose("")
				} else if idx > 0 && idx <= len(w.devices) {
					w.app.choose(w.devices[idx-1].Serial)
				}
			}
		case 105:
			w.app.mu.Lock()
			serial := w.app.prefs.Serial
			w.app.mu.Unlock()
			w.app.choose(serial)
		}
		return 0
	}
	return proc("DefWindowProcW", hwnd, uintptr(msg), wp, lp)
}
func runDesktop(a *desktopApp, ctx context.Context, adb, serial string, dry, listenOnly bool) error {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	proc("SetProcessDPIAware")
	dpi := int(proc("GetDpiForSystem"))
	if dpi == 0 {
		dpi = 96
	}
	module, _, _ := kernel32.NewProc("GetModuleHandleW").Call(0)
	icon := proc("LoadImageW", module, 1, 1, uintptr(64*dpi/96), uintptr(64*dpi/96), 0x8000)
	if icon == 0 {
		icon = proc("LoadIconW", 0, 32512)
	}
	cursor := proc("LoadCursorW", 0, 32512)
	theme := newDesktopTheme(dpi)
	defer theme.close()
	wc := winClass{Callback: windowCallback, Instance: module, Icon: icon, SmallIcon: icon, Cursor: cursor, Background: theme.brush(creamColor), Name: utf(windowClass)}
	wc.Size = uint32(unsafe.Sizeof(wc))
	if proc("RegisterClassExW", uintptr(unsafe.Pointer(&wc))) == 0 {
		return fmt.Errorf("cannot register window")
	}
	defer proc("UnregisterClassW", uintptr(unsafe.Pointer(utf(windowClass))), module)
	w := &desktopWindow{app: a, icon: icon, dpi: dpi, font: theme.body, theme: theme}
	desktop = w
	defer func() { desktop = nil }()
	w.taskbarCreated = uint32(proc("RegisterWindowMessageW", uintptr(unsafe.Pointer(utf("TaskbarCreated")))))
	const style = 0x02CA0000 // Fixed-size native window, clip child controls while painting.
	bounds := w.rect(0, 0, 620, 540)
	proc("AdjustWindowRectEx", uintptr(unsafe.Pointer(&bounds)), style, 0, 0)
	hwnd := proc("CreateWindowExW", 0, uintptr(unsafe.Pointer(utf(windowClass))), uintptr(unsafe.Pointer(utf("MambaPad"))), style, 0x80000000, 0x80000000, uintptr(bounds.Right-bounds.Left), uintptr(bounds.Bottom-bounds.Top), 0, 0, module, 0)
	if hwnd == 0 {
		return fmt.Errorf("cannot create window")
	}
	w.hwnd = hwnd
	w.styleCaption()
	w.label("MambaPad", theme.brand, 96, 20, 360, 38, inkColor, creamColor)
	w.label("指尖一划，即刻掌控", theme.small, 96, 60, 350, 20, mutedColor, creamColor)
	badge := w.control("STATIC", "USB 有线", 1, 500, 39, 86, 20, 0)
	proc("SendMessageW", badge, 0x30, theme.small, 1)
	theme.labels[badge] = labelColors{orangeColor, softColor}
	w.status = w.label("等待手机连接", theme.status, 76, 112, 500, 36, inkColor, whiteColor)
	w.detail = w.control("EDIT", "", 0x844, 44, 160, 532, 40, 0)
	w.label("连接设备", theme.heading, 44, 248, 500, 24, inkColor, whiteColor)
	w.combo = w.control("COMBOBOX", "", 0x00200003|0x10000, 44, 286, 370, 240, 104)
	proc("SendMessageW", w.combo, 0x153, ^uintptr(0), w.scale(28)) // Selection field height.
	proc("SendMessageW", w.combo, 0x153, 0, w.scale(28))
	w.control("BUTTON", "重新连接", 0x1000b, 430, 284, 146, 36, 105)
	w.label("开始使用", theme.heading, 44, 372, 500, 24, inkColor, whiteColor)
	guide := "1. 用数据线连接手机，开启 USB 调试并确认授权。\r\n2. 打开手机 MambaPad，选择 USB 有线。\r\n3. 连接成功后，即可使用触控板和键盘。"
	w.label(guide, theme.small, 44, 402, 532, 56, mutedColor, whiteColor)
	footer := "关闭窗口后，连接保留在托盘"
	if dry {
		footer = "测试模式 · 不发送电脑输入"
	}
	w.label(footer, theme.small, 24, 495, 260, 20, mutedColor, creamColor)
	w.control("BUTTON", "最小化到托盘", 0x1000b, 312, 486, 164, 36, 102)
	w.control("BUTTON", "退出", 0x1000b, 488, 486, 108, 36, 103)
	a.mu.Lock()
	a.prefs.Serial = serial
	a.notify = func() { proc("PostMessageW", hwnd, wmUpdate, 0, 0) }
	a.mu.Unlock()
	w.refresh()
	w.tray = w.notice(0, false)
	proc("ShowWindow", hwnd, 5)
	proc("UpdateWindow", hwnd)
	go a.supervise(ctx, adb, serial, dry, listenOnly)
	go func() { <-ctx.Done(); proc("PostMessageW", hwnd, wmQuit, 0, 0) }()
	var msg winMessage
	for {
		r := proc("GetMessageW", uintptr(unsafe.Pointer(&msg)), 0, 0, 0)
		if int32(r) <= 0 {
			break
		}
		if proc("IsDialogMessageW", hwnd, uintptr(unsafe.Pointer(&msg))) == 0 {
			proc("TranslateMessage", uintptr(unsafe.Pointer(&msg)))
			proc("DispatchMessageW", uintptr(unsafe.Pointer(&msg)))
		}
	}
	a.quit()
	<-a.done
	return nil
}
