package main

import (
	"context"
	"encoding/json"
	"net"
	"strings"
	"testing"
)

func TestSocketDisconnectReleasesKeyboard(t *testing.T) {
    a,b:=net.Pipe();f:=&fakeInjector{};done:=make(chan error,1)
    go func(){done<-serveSession(context.Background(),a,f)}()
    b.Write([]byte("{\"v\":1,\"type\":\"hello\",\"client\":\"KeysJoy-Android\",\"protocol\":1}\n"))
    var welcome map[string]any
    if err:=json.NewDecoder(b).Decode(&welcome);err!=nil{t.Fatal(err)}
    b.Write([]byte("{\"v\":1,\"type\":\"key\",\"usage\":4,\"down\":true,\"modifiers\":2,\"seq\":1}\n"))
    b.Close();<-done
    if got:=strings.Join(f.events,",");got!="key 225 true,key 4 true,key 4 false,key 225 false"{t.Fatal(got)}
}

func TestShortcutOrderAndDisconnectRelease(t *testing.T) {
	f := &fakeInjector{}
	s := newState(f)
	down, _ := parseMessage([]byte(`{"v":1,"type":"key","usage":6,"down":true,"modifiers":1,"seq":1}`))
	if err := s.apply(down); err != nil {
		t.Fatal(err)
	}
	if err := s.release(); err != nil {
		t.Fatal(err)
	}
	want := "key 224 true,key 6 true,key 6 false,key 224 false"
	if got := strings.Join(f.events, ","); got != want {
		t.Fatal(got)
	}
}
func TestNormalKeyUpReleasesModifierAfterKey(t *testing.T) {
	f := &fakeInjector{}
	s := newState(f)
	for _, line := range []string{`{"v":1,"type":"key","usage":4,"down":true,"modifiers":2,"seq":1}`, `{"v":1,"type":"key","usage":4,"down":false,"modifiers":0,"seq":2}`} {
		m, err := parseMessage([]byte(line))
		if err != nil {
			t.Fatal(err)
		}
		if err = s.apply(m); err != nil {
			t.Fatal(err)
		}
	}
	if got := strings.Join(f.events, ","); got != "key 225 true,key 4 true,key 4 false,key 225 false" {
		t.Fatal(got)
	}
}
func TestKeyboardValidationAndScanCodeMapping(t *testing.T) {
	for _, line := range []string{`{"v":1,"type":"key","usage":0,"down":true,"seq":1}`, `{"v":1,"type":"key","usage":4,"down":true,"modifiers":256,"seq":1}`, `{"v":1,"type":"key","usage":4,"seq":1}`} {
		if _, err := parseMessage([]byte(line)); err == nil {
			t.Fatal("accepted invalid key")
		}
	}
	for usage, expected := range map[int]uint16{4: 0x1e, 29: 0x2c, 30: 0x02, 39: 0x0b, 40: 0x1c, 79: 0xe04d, 227: 0xe05b} {
		if actual, ok := keyScanCode(usage); !ok || actual != expected {
			t.Fatalf("usage %d: %x", usage, actual)
		}
	}
}
