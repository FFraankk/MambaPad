package main

import (
	"bufio"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"os/signal"
	"runtime"
	"syscall"
	"time"
)

func serveSession(ctx context.Context, c net.Conn, injector Injector, connection ...func(bool)) error {
	notify := func(on bool) {
		for _, fn := range connection {
			fn(on)
		}
	}
	defer notify(false)
	defer c.Close()
	state := newState(injector)
	defer func() {
		if err := state.release(); err != nil {
			log.Printf("Release failed: %v", err)
		}
	}()
	done := make(chan struct{})
	defer close(done)
	go func() {
		select {
		case <-ctx.Done():
			c.Close()
		case <-done:
		}
	}()
	reader := bufio.NewScanner(c)
	reader.Buffer(make([]byte, 4097), 4097)
	write := json.NewEncoder(c)
	handshaken := false
	var lastSeq int64
	window := time.Now()
	count := 0
	for {
		c.SetReadDeadline(time.Now().Add(2 * time.Second))
		if !reader.Scan() {
			if err := reader.Err(); err != nil {
				return err
			}
			return nil
		}
		if len(reader.Bytes()) > 4096 {
			return fmt.Errorf("line too long")
		}
		if time.Since(window) >= time.Second {
			window = time.Now()
			count = 0
		}
		count++
		if count > 1000 {
			return fmt.Errorf("message rate exceeded")
		}
		m, err := parseMessage(reader.Bytes())
		if err != nil {
			return err
		}
		c.SetWriteDeadline(time.Now().Add(time.Second))
		if !handshaken {
			if m.Type != "hello" {
				return fmt.Errorf("hello required")
			}
			if err = write.Encode(map[string]any{"v": 1, "type": "welcome", "protocol": 1, "features": []string{"mouse", "keyboard", "text"}}); err != nil {
				return err
			}
			handshaken = true
			notify(true)
			log.Print("Phone connected")
			continue
		}
		if m.Type == "hello" || m.Seq <= lastSeq {
			return fmt.Errorf("invalid message order")
		}
		lastSeq = m.Seq
		if m.Type == "ping" {
			if err = write.Encode(map[string]any{"v": 1, "type": "pong", "seq": m.Seq}); err != nil {
				return err
			}
		} else if err = state.apply(m); err != nil {
			return err
		}
	}
}

func main() {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	dry := flag.Bool("dry-run", false, "validate without sending desktop input")
	listenOnly := flag.Bool("listen-only", false, "do not run ADB or alter mappings")
	adb := flag.String("adb", "", "path to adb.exe")
	serial := flag.String("serial", "", "explicit USB device serial")
	console := flag.Bool("console", false, "run without the desktop window")
	quit := flag.Bool("quit", false, "gracefully stop the running desktop receiver")
	flag.Parse()
	if *quit {
		if err := stopExisting(); err != nil {
			os.Exit(1)
		}
		return
	}
	mutex, first, err := instanceMutex()
	if err != nil {
		showError("无法启动 MambaPad", err.Error())
		return
	}
	defer syscall.CloseHandle(mutex)
	if !first {
		showExisting()
		return
	}
	defer releaseInstance(mutex)
	exe, err := os.Executable()
	if err != nil {
		showError("无法启动 MambaPad", err.Error())
		return
	}
	app, err := newDesktopApp(exe)
	if err != nil {
		showError("无法保存设置", "请将便携版解压到可写目录。\n"+err.Error())
		return
	}
	logger, err := openLog(app.dataDir)
	if err != nil {
		showError("无法打开日志", err.Error())
		return
	}
	defer logger.Close()
	log.SetOutput(logger)
	if *console {
		log.SetOutput(io.MultiWriter(logger, os.Stderr))
	}
	if *serial == "" {
		*serial = app.prefs.Serial
	}
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()
	app.cancel = cancel
	finished := app.done
	installCloseHandler(cancel, finished)
	if *console {
		go app.supervise(ctx, *adb, *serial, *dry, *listenOnly)
		<-ctx.Done()
		<-finished
		return
	}
	if err = runDesktop(app, ctx, *adb, *serial, *dry, *listenOnly); err != nil {
		cancel()
		showError("MambaPad", err.Error())
	}
}
