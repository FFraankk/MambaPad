package main

import (
	"bufio"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"sync"
	"time"
)

func serveSession(ctx context.Context, c net.Conn, injector Injector) error {
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
			log.Print("Phone connected / 手机已连接")
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
	dry := flag.Bool("dry-run", false, "validate protocol without sending desktop input")
	listenOnly := flag.Bool("listen-only", false, "do not run ADB or alter reverse mappings")
	adb := flag.String("adb", "", "path to adb.exe (otherwise tools/adb/adb.exe beside this EXE)")
	serial := flag.String("serial", "", "explicit ADB serial if multiple phones are connected")
	flag.Parse()
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()
	finished := make(chan struct{})
	defer close(finished)
	installCloseHandler(cancel, finished)
	listener, err := net.Listen("tcp4", "127.0.0.1:27183")
	if err != nil {
		log.Printf("Cannot listen: %v", err)
		return
	}
	defer listener.Close()
	log.Print("MambaPad USB — no Root / 无需 Root。仅本机端口 27183。Ctrl+C 退出。")
	if *dry {
		log.Print("DRY RUN: desktop input disabled")
	}
	var adbWorkers sync.WaitGroup
	if !*listenOnly {
		adbWorkers.Add(1)
		go func() { defer adbWorkers.Done(); manageADB(ctx, *adb, *serial) }()
	}
	go func() { <-ctx.Done(); listener.Close() }()
	var sessions sync.WaitGroup
	busy := make(chan struct{}, 1)
	for {
		conn, err := listener.Accept()
		if err != nil {
			break
		}
		select {
		case busy <- struct{}{}:
			sessions.Add(1)
			go func() {
				defer sessions.Done()
				defer func() { <-busy }()
				if err := serveSession(ctx, conn, WindowsInjector{*dry}); err != nil {
					log.Printf("Session closed: %v", err)
				}
				log.Print("Disconnected / 已断开")
			}()
		default:
			conn.Close()
		}
	}
	sessions.Wait()
	cancel()
	adbWorkers.Wait()
}
