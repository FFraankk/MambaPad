package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

type preferences struct {
	Serial        string `json:"device,omitempty"`
	TrayHintShown bool   `json:"tray_hint_shown"`
}
type usbDevice struct{ Serial, Label string }
type appSnapshot struct {
	Status, Detail string
	Devices        []usbDevice
	Connected      bool
}
type desktopApp struct {
	mu           sync.Mutex
	prefs        preferences
	snapshot     appSnapshot
	dataDir      string
	notify       func()
	selectDevice chan string
	cancel       context.CancelFunc
	done         chan struct{}
	quitting     bool
}

func dataDirectory(exe, localData string) (string, error) {
	if _, err := os.Stat(filepath.Join(filepath.Dir(exe), "portable.flag")); err == nil {
		return filepath.Join(filepath.Dir(exe), "data"), nil
	} else if !os.IsNotExist(err) {
		return "", err
	}
	if localData == "" {
		return "", fmt.Errorf("cannot locate local application data")
	}
	return filepath.Join(localData, "MambaPad"), nil
}
func newDesktopApp(exe string) (*desktopApp, error) {
	dir, err := dataDirectory(exe, os.Getenv("LOCALAPPDATA"))
	if err != nil {
		return nil, err
	}
	if err = os.MkdirAll(dir, 0700); err != nil {
		return nil, err
	}
	// Fail visibly instead of silently storing portable data elsewhere.
	probe, err := os.CreateTemp(dir, ".write-check-")
	if err != nil {
		return nil, err
	}
	name := probe.Name()
	probe.Close()
	os.Remove(name)
	a := &desktopApp{dataDir: dir, selectDevice: make(chan string, 1), done: make(chan struct{})}
	raw, err := os.ReadFile(filepath.Join(dir, "settings.json"))
	if err == nil {
		if err = json.Unmarshal(raw, &a.prefs); err != nil {
			return nil, fmt.Errorf("invalid settings.json: %w", err)
		}
	} else if !os.IsNotExist(err) {
		return nil, err
	}
	a.snapshot.Status = "等待手机连接"
	return a, nil
}
func (a *desktopApp) saveLocked() error {
	raw, err := json.MarshalIndent(a.prefs, "", "  ")
	if err != nil {
		return err
	}
	temp := filepath.Join(a.dataDir, "settings.tmp")
	if err = os.WriteFile(temp, raw, 0600); err != nil {
		return err
	}
	return os.Rename(temp, filepath.Join(a.dataDir, "settings.json"))
}
func (a *desktopApp) update(fn func(*appSnapshot)) {
	a.mu.Lock()
	fn(&a.snapshot)
	notify := a.notify
	a.mu.Unlock()
	if notify != nil {
		notify()
	}
}
func (a *desktopApp) read() appSnapshot {
	a.mu.Lock()
	defer a.mu.Unlock()
	s := a.snapshot
	s.Devices = append([]usbDevice(nil), s.Devices...)
	return s
}
func (a *desktopApp) choose(serial string) {
	a.mu.Lock()
	a.prefs.Serial = serial
	err := a.saveLocked()
	a.mu.Unlock()
	if err != nil {
		a.update(func(s *appSnapshot) { s.Detail = "无法保存设备选择：" + err.Error() })
	}
	select {
	case a.selectDevice <- serial:
	default:
		select {
		case <-a.selectDevice:
		default:
		}
		a.selectDevice <- serial
	}
}
func (a *desktopApp) quit() {
	a.mu.Lock()
	if a.quitting {
		a.mu.Unlock()
		return
	}
	a.quitting = true
	cancel := a.cancel
	a.mu.Unlock()
	if cancel != nil {
		cancel()
	}
}
func (a *desktopApp) supervise(ctx context.Context, adb, serial string, dry, listenOnly bool) {
	defer close(a.done)
	for {
		child, cancel := context.WithCancel(ctx)
		finished := make(chan struct{})
		go func(selected string) { defer close(finished); a.receive(child, adb, selected, dry, listenOnly) }(serial)
		select {
		case <-ctx.Done():
			cancel()
			<-finished
			return
		case serial = <-a.selectDevice:
			cancel()
			<-finished
		}
	}
}
func (a *desktopApp) receive(ctx context.Context, adb, serial string, dry, listenOnly bool) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	listener, err := net.Listen("tcp4", "127.0.0.1:27183")
	if err != nil {
		a.update(func(s *appSnapshot) {
			s.Status = "无法启动连接"
			s.Detail = "本机端口 27183 无法使用。请关闭旧接收端后重新选择设备重试。"
		})
		return
	}
	defer listener.Close()
	a.update(func(s *appSnapshot) { s.Connected = false; s.Status = "等待手机连接"; s.Detail = "" })
	go func() { <-ctx.Done(); listener.Close() }()
	var workers sync.WaitGroup
	if !listenOnly {
		workers.Add(1)
		go func() { defer workers.Done(); manageADBWithStatus(ctx, adb, serial, a) }()
	}
	var sessions sync.WaitGroup
	busy := make(chan struct{}, 1)
	for {
		c, err := listener.Accept()
		if err != nil {
			break
		}
		select {
		case busy <- struct{}{}:
			sessions.Add(1)
			go func() {
				defer sessions.Done()
				defer func() { <-busy }()
				err := serveSession(ctx, c, WindowsInjector{dry}, func(connected bool) {
					a.update(func(s *appSnapshot) {
						s.Connected = connected
						if connected {
							s.Detail = ""
						}
					})
				})
				if err != nil && ctx.Err() == nil {
					a.update(func(s *appSnapshot) { s.Detail = "输入会话已结束，请检查连接后重试。" })
					log.Print("Input session ended with an error")
				}
			}()
		default:
			c.Close()
		}
	}
	cancel()
	sessions.Wait()
	workers.Wait()
}

// Device serials are used for selection, but never included in diagnostic logs.
func deviceList(out string) []usbDevice {
	var result []usbDevice
	for _, line := range strings.Split(out, "\n") {
		f := strings.Fields(line)
		if len(f) < 2 || f[1] != "device" || strings.HasPrefix(f[0], "emulator-") || strings.ContainsAny(f[0], ":") || strings.Contains(f[0], "_adb-tls-") {
			continue
		}
		label := "Android 手机"
		for _, part := range f[2:] {
			if strings.HasPrefix(part, "model:") {
				label = strings.ReplaceAll(strings.TrimPrefix(part, "model:"), "_", " ")
			}
		}
		result = append(result, usbDevice{f[0], fmt.Sprintf("%s · USB %d", label, len(result)+1)})
	}
	return result
}

type boundedLog struct {
	mu   sync.Mutex
	path string
	file *os.File
	size int64
}

func openLog(dir string) (*boundedLog, error) {
	p := filepath.Join(dir, "receiver.log")
	f, err := os.OpenFile(p, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		return nil, err
	}
	st, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, err
	}
	return &boundedLog{path: p, file: f, size: st.Size()}, nil
}
func (l *boundedLog) Write(p []byte) (int, error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.size+int64(len(p)) > 256*1024 {
		l.file.Close()
		os.Remove(l.path + ".1")
		if err := os.Rename(l.path, l.path+".1"); err != nil {
			return 0, err
		}
		f, err := os.OpenFile(l.path, os.O_CREATE|os.O_WRONLY, 0600)
		if err != nil {
			return 0, err
		}
		l.file = f
		l.size = 0
	}
	n, err := l.file.Write(p)
	l.size += int64(n)
	return n, err
}
func (l *boundedLog) Close() error { l.mu.Lock(); defer l.mu.Unlock(); return l.file.Close() }

var _ io.Writer = (*boundedLog)(nil)
