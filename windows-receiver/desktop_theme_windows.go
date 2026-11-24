package main

import (
	"syscall"
	"unsafe"
)

// COLORREF values match the Android palette in MambaUi.
func rgb(r, g, b uint32) uint32 { return r | g<<8 | b<<16 }

var (
	creamColor  = rgb(255, 248, 235)
	inkColor    = rgb(71, 43, 28)
	mutedColor  = rgb(143, 115, 93)
	orangeColor = rgb(218, 67, 32)
	lineColor   = rgb(239, 222, 197)
	whiteColor  = rgb(255, 255, 255)
	softColor   = rgb(255, 237, 217)
	greenColor  = rgb(62, 117, 83)
)

type winRect struct{ Left, Top, Right, Bottom int32 }
type paintData struct {
	DC              uintptr
	Erase           int32
	Rect            winRect
	Restore, Update int32
	Reserved        [32]byte
}
type drawItemData struct {
	Type, ID, Item, Action, State uint32
	Window, DC                    uintptr
	Rect                          winRect
	Data                          uintptr
}
type labelColors struct{ text, background uint32 }
type desktopTheme struct {
	brushes                             map[uint32]uintptr
	labels                              map[uintptr]labelColors
	fonts                               []uintptr
	body, small, heading, brand, status uintptr
}

func gdi(name string, args ...uintptr) uintptr {
	v, _, _ := gdi32.NewProc(name).Call(args...)
	return v
}
func newDesktopTheme(dpi int) *desktopTheme {
	t := &desktopTheme{brushes: make(map[uint32]uintptr), labels: make(map[uintptr]labelColors)}
	font := func(size, weight int) uintptr {
		h := gdi("CreateFontW", uintptr(int64(-size*dpi/96)), 0, 0, 0, uintptr(weight), 0, 0, 0, 1, 0, 0, 5, 0, uintptr(unsafe.Pointer(utf("Microsoft YaHei UI"))))
		if h == 0 {
			return gdi("GetStockObject", 17) // DEFAULT_GUI_FONT
		}
		t.fonts = append(t.fonts, h)
		return h
	}
	t.body, t.small = font(14, 400), font(12, 400)
	t.heading, t.brand, t.status = font(16, 600), font(28, 700), font(22, 600)
	return t
}
func (t *desktopTheme) brush(color uint32) uintptr {
	if brush, ok := t.brushes[color]; ok {
		return brush
	}
	h := gdi("CreateSolidBrush", uintptr(color))
	t.brushes[color] = h
	return h
}
func (t *desktopTheme) close() {
	for _, h := range t.fonts {
		gdi("DeleteObject", h)
	}
	for _, h := range t.brushes {
		gdi("DeleteObject", h)
	}
}
func (w *desktopWindow) rect(x, y, width, height int) winRect {
	return winRect{int32(w.scale(x)), int32(w.scale(y)), int32(w.scale(x + width)), int32(w.scale(y + height))}
}
func rounded(dc uintptr, r winRect, fill, border uint32, radius int) {
	saved := gdi("SaveDC", dc)
	defer gdi("RestoreDC", dc, saved)
	gdi("SelectObject", dc, gdi("GetStockObject", 18)) // DC_BRUSH
	gdi("SelectObject", dc, gdi("GetStockObject", 19)) // DC_PEN
	gdi("SetDCBrushColor", dc, uintptr(fill))
	gdi("SetDCPenColor", dc, uintptr(border))
	gdi("RoundRect", dc, uintptr(r.Left), uintptr(r.Top), uintptr(r.Right), uintptr(r.Bottom), uintptr(radius), uintptr(radius))
}
func (w *desktopWindow) label(text string, font uintptr, x, y, width, height int, color, background uint32) uintptr {
	h := w.control("STATIC", text, 0, x, y, width, height, 0)
	proc("SendMessageW", h, 0x30, font, 1)
	w.theme.labels[h] = labelColors{color, background}
	return h
}
func (w *desktopWindow) paint() {
	var p paintData
	dc := proc("BeginPaint", w.hwnd, uintptr(unsafe.Pointer(&p)))
	defer proc("EndPaint", w.hwnd, uintptr(unsafe.Pointer(&p)))
	var client winRect
	proc("GetClientRect", w.hwnd, uintptr(unsafe.Pointer(&client)))
	proc("FillRect", dc, uintptr(unsafe.Pointer(&client)), w.theme.brush(creamColor))
	rounded(dc, w.rect(24, 20, 56, 56), whiteColor, lineColor, int(w.scale(24)))
	proc("DrawIconEx", dc, w.scale(30), w.scale(26), w.icon, w.scale(44), w.scale(44), 0, 0, 3)
	rounded(dc, w.rect(490, 34, 106, 28), softColor, softColor, int(w.scale(24)))
	for _, r := range []winRect{w.rect(24, 96, 572, 120), w.rect(24, 232, 572, 108), w.rect(24, 356, 572, 112)} {
		rounded(dc, r, whiteColor, lineColor, int(w.scale(28)))
	}
	color, background := orangeColor, softColor
	if w.app.read().Connected && !w.closing {
		color, background = greenColor, rgb(235, 244, 231)
	}
	rounded(dc, w.rect(44, 124, 20, 20), background, background, int(w.scale(20)))
	rounded(dc, w.rect(50, 130, 8, 8), color, color, int(w.scale(8)))
}
func (w *desktopWindow) colorControl(dc, control uintptr) uintptr {
	colors, ok := w.theme.labels[control]
	if !ok {
		colors = labelColors{inkColor, whiteColor}
		if control == w.detail {
			colors.text = mutedColor
		}
	}
	gdi("SetTextColor", dc, uintptr(colors.text))
	gdi("SetBkColor", dc, uintptr(colors.background))
	return w.theme.brush(colors.background)
}
func (w *desktopWindow) drawButton(item *drawItemData) {
	// Buttons remain native controls for focus, keyboard navigation and UIA.
	saved := gdi("SaveDC", item.DC)
	defer gdi("RestoreDC", item.DC, saved)
	background := creamColor
	if item.ID == 105 {
		background = whiteColor
	}
	proc("FillRect", item.DC, uintptr(unsafe.Pointer(&item.Rect)), w.theme.brush(background))
	fill, border, text := whiteColor, lineColor, inkColor
	if item.ID == 105 {
		fill, border, text = orangeColor, orangeColor, whiteColor
	}
	if item.State&1 != 0 { // ODS_SELECTED
		if item.ID == 105 {
			fill, border = rgb(188, 53, 23), rgb(188, 53, 23)
		} else {
			fill = softColor
		}
	}
	if item.State&4 != 0 { // ODS_DISABLED
		fill, border, text = creamColor, lineColor, mutedColor
	}
	rounded(item.DC, item.Rect, fill, border, int(w.scale(24)))
	gdi("SelectObject", item.DC, w.theme.body)
	gdi("SetBkMode", item.DC, 1)
	gdi("SetTextColor", item.DC, uintptr(text))
	var caption [128]uint16
	proc("GetWindowTextW", item.Window, uintptr(unsafe.Pointer(&caption[0])), uintptr(len(caption)))
	r := item.Rect
	proc("DrawTextW", item.DC, uintptr(unsafe.Pointer(&caption[0])), ^uintptr(0), uintptr(unsafe.Pointer(&r)), 0x25) // center, vertical center, single line
	if item.State&0x10 != 0 && item.State&0x200 == 0 {
		inset := int32(w.scale(5))
		r.Left += inset
		r.Top += inset
		r.Right -= inset
		r.Bottom -= inset
		proc("DrawFocusRect", item.DC, uintptr(unsafe.Pointer(&r)))
	}
}
func (w *desktopWindow) styleCaption() {
	dwm := syscall.NewLazyDLL("dwmapi.dll").NewProc("DwmSetWindowAttribute")
	for attribute, value := range map[uint32]uint32{33: 2, 34: lineColor, 35: creamColor, 36: inkColor} {
		// Windows 10 may not support these Windows 11 decorations.
		dwm.Call(w.hwnd, uintptr(attribute), uintptr(unsafe.Pointer(&value)), 4)
	}
}
