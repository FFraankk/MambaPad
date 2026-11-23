package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"strings"
	"sync"
	"testing"
	"time"
)

type fakeInjector struct {
	mu     sync.Mutex
	events []string
}

func (f *fakeInjector) Text(text string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.events = append(f.events, "text "+text)
	return nil
}
func (f *fakeInjector) Move(x, y int) error   { return nil }
func (f *fakeInjector) Scroll(v, h int) error { return nil }
func (f *fakeInjector) Key(usage int, down bool) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.events = append(f.events, fmt.Sprintf("key %d %t", usage, down))
	return nil
}
func (f *fakeInjector) Button(b string, down bool) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if down {
		f.events = append(f.events, b+" down")
	} else {
		f.events = append(f.events, b+" up")
	}
	return nil
}

func TestRejectInvalidMessages(t *testing.T) {
	for _, s := range []string{`{"v":2,"type":"ping","seq":1}`, `{"v":1,"type":"button","button":"left","seq":1}`, `{"v":1,"type":"move","dx":1,"seq":1}`, `{"v":1,"type":"ping","seq":0}`, `{"v":1,"type":"key","seq":1}`, `{"v":1,"type":"ping","seq":1} {}`} {
		if _, err := parseMessage([]byte(s)); err == nil {
			t.Fatalf("accepted %s", s)
		}
	}
}
func TestDisconnectReleasesHeldButton(t *testing.T) {
	a, b := net.Pipe()
	f := &fakeInjector{}
	done := make(chan error, 1)
	go func() { done <- serveSession(context.Background(), a, f) }()
	b.Write([]byte("{\"v\":1,\"type\":\"hello\",\"client\":\"KeysJoy-Android\",\"protocol\":1}\n"))
	var welcome map[string]any
	if err := json.NewDecoder(b).Decode(&welcome); err != nil || welcome["type"] != "welcome" {
		t.Fatal(err)
	}
	b.Write([]byte("{\"v\":1,\"type\":\"button\",\"button\":\"left\",\"down\":true,\"seq\":1}\n"))
	b.Close()
	<-done
	if strings.Join(f.events, ",") != "left down,left up" {
		t.Fatal(f.events)
	}
}
func TestHandshakeAndTimeout(t *testing.T) {
	a, b := net.Pipe()
	done := make(chan error, 1)
	go func() { done <- serveSession(context.Background(), a, &fakeInjector{}) }()
	b.Write([]byte("{\"v\":1,\"type\":\"ping\",\"seq\":1}\n"))
	if err := <-done; err == nil {
		t.Fatal("accepted input before handshake")
	}
	b.Close()
	a, b = net.Pipe()
	go func() { done <- serveSession(context.Background(), a, &fakeInjector{}) }()
	select {
	case err := <-done:
		if err == nil {
			t.Fatal("missing timeout")
		}
	case <-time.After(3 * time.Second):
		t.Fatal("no timeout")
	}
	b.Close()
}
func TestDeviceSelection(t *testing.T) {
	if s, _ := chooseDevice("192.168.1.2:5555 device\nemulator-5554 device\n", ""); s != "" {
		t.Fatal("selected non-USB device")
	}
	if s, _ := chooseDevice("List of devices attached\na device\nb device\n", ""); s != "" {
		t.Fatal("selected arbitrary device")
	}
	if s, _ := chooseDevice("a device\nb device\n", "b"); s != "b" {
		t.Fatal(s)
	}
	if s, status := chooseDevice("a unauthorized\n", ""); s != "" || !strings.Contains(status, "Unlock") {
		t.Fatal(status)
	}
}

func TestUnicodeTextProtocol(t *testing.T) {
	f := &fakeInjector{}
	state := newState(f)
	m, err := parseMessage([]byte(`{"v":1,"type":"text","text":"中文😀","seq":1}`))
	if err != nil {
		t.Fatal(err)
	}
	if err = state.apply(m); err != nil {
		t.Fatal(err)
	}
	if len(f.events) != 1 || f.events[0] != "text 中文😀" {
		t.Fatal(f.events)
	}
	for _, value := range []string{"", strings.Repeat("a", 2049), "a\nb"} {
		raw, _ := json.Marshal(map[string]any{"v": 1, "type": "text", "text": value, "seq": 1})
		if _, err := parseMessage(raw); err == nil {
			t.Fatalf("accepted text %q", value)
		}
	}
}
