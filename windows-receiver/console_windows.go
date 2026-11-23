package main

import (
	"context"
	"syscall"
	"time"
)

var consoleHandler uintptr

// Closing the console gets a short Windows shutdown budget. Release the active
// session before acknowledging the event; do not merely rely on Ctrl+C.
func installCloseHandler(cancel context.CancelFunc, finished <-chan struct{}) {
	consoleHandler = syscall.NewCallback(func(event uint32) uintptr {
		if event != 2 && event != 5 && event != 6 {
			return 0
		}
		cancel()
		select {
		case <-finished:
		case <-time.After(4 * time.Second):
		}
		return 1
	})
	syscall.NewLazyDLL("kernel32.dll").NewProc("SetConsoleCtrlHandler").Call(consoleHandler, 1)
}
