//go:build windows && (amd64 || arm64)

package main

import (
	"encoding/binary"
	"fmt"
	"syscall"
	"unicode/utf16"
	"unsafe"
)

var sendInput = syscall.NewLazyDLL("user32.dll").NewProc("SendInput")

type WindowsInjector struct{ dry bool }

// INPUT is 40 bytes on Windows x64/ARM64; its union begins at offset 8.
// MOUSEINPUT dx/dy/data/flags are four consecutive DWORDs at offsets 8..23.
func (w WindowsInjector) mouse(dx, dy, data int, flags uint32) error {
	if w.dry {
		return nil
	}
	var input [40]byte
	binary.LittleEndian.PutUint32(input[8:12], uint32(dx))
	binary.LittleEndian.PutUint32(input[12:16], uint32(dy))
	binary.LittleEndian.PutUint32(input[16:20], uint32(data))
	binary.LittleEndian.PutUint32(input[20:24], flags)
	n, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&input[0])), 40)
	if n != 1 {
		return fmt.Errorf("SendInput failed (UIPI may block elevated windows): %v", err)
	}
	return nil
}
func (w WindowsInjector) Move(x, y int) error { return w.mouse(x, y, 0, 1) }
func (w WindowsInjector) Button(b string, down bool) error {
	flags := map[string]uint32{"left": 2, "right": 8, "middle": 32}[b]
	if !down {
		flags *= 2
	}
	return w.mouse(0, 0, 0, flags)
}
func (w WindowsInjector) Scroll(v, h int) error {
	if v != 0 {
		if err := w.mouse(0, 0, v, 0x800); err != nil {
			return err
		}
	}
	if h != 0 {
		return w.mouse(0, 0, h, 0x1000)
	}
	return nil
}

func (w WindowsInjector) Key(usage int, down bool) error {
	scan, ok := keyScanCode(usage)
	if !ok {
		return fmt.Errorf("unsupported keyboard usage")
	}
	if w.dry {
		return nil
	}
	var input [40]byte
	binary.LittleEndian.PutUint32(input[0:4], 1) // INPUT_KEYBOARD
	binary.LittleEndian.PutUint16(input[10:12], scan&0xff)
	flags := uint32(8) // KEYEVENTF_SCANCODE
	if scan&0xff00 == 0xe000 {
		flags |= 1
	}
	if !down {
		flags |= 2
	}
	binary.LittleEndian.PutUint32(input[12:16], flags)
	n, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&input[0])), 40)
	if n != 1 {
		return fmt.Errorf("keyboard SendInput failed (UIPI may block elevated windows): %v", err)
	}
	return nil
}

// Unicode packets preserve phone commits independently of the PC keyboard layout.
func (w WindowsInjector) Text(text string) error {
	if w.dry {
		return nil
	}
	for _, unit := range utf16.Encode([]rune(text)) {
		for _, flags := range []uint32{4, 6} {
			var input [40]byte
			binary.LittleEndian.PutUint32(input[0:4], 1)
			binary.LittleEndian.PutUint16(input[10:12], unit)
			binary.LittleEndian.PutUint32(input[12:16], flags)
			n, _, err := sendInput.Call(1, uintptr(unsafe.Pointer(&input[0])), 40)
			if n != 1 {
				return fmt.Errorf("text SendInput failed: %v", err)
			}
		}
	}
	return nil
}
