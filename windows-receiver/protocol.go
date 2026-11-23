package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"unicode/utf8"
)

type Message struct {
	Text       string `json:"text,omitempty"`
	V          int    `json:"v"`
	Type       string `json:"type"`
	Protocol   int    `json:"protocol,omitempty"`
	Client     string `json:"client,omitempty"`
	Seq        int64  `json:"seq,omitempty"`
	DX         *int   `json:"dx,omitempty"`
	DY         *int   `json:"dy,omitempty"`
	Button     string `json:"button,omitempty"`
	Down       *bool  `json:"down,omitempty"`
	Vertical   *int   `json:"vertical,omitempty"`
	Horizontal *int   `json:"horizontal,omitempty"`
	Usage      *int   `json:"usage,omitempty"`
	Modifiers  int    `json:"modifiers,omitempty"`
}

func parseMessage(line []byte) (Message, error) {
	var m Message
	d := json.NewDecoder(bytes.NewReader(line))
	d.DisallowUnknownFields()
	if err := d.Decode(&m); err != nil {
		return m, err
	}
	if err := d.Decode(new(any)); err != io.EOF {
		return m, errors.New("one JSON object required")
	}
	if m.V != 1 {
		return m, errors.New("unsupported version")
	}
	bounded := func(p *int) bool { return p != nil && *p >= -32767 && *p <= 32767 }
	switch m.Type {
	case "hello":
		if m.Protocol != 1 || m.Client != "KeysJoy-Android" {
			return m, errors.New("invalid hello")
		}
	case "move":
		if !bounded(m.DX) || !bounded(m.DY) {
			return m, errors.New("invalid movement")
		}
	case "button":
		if m.Down == nil || (m.Button != "left" && m.Button != "right" && m.Button != "middle") {
			return m, errors.New("invalid button")
		}
	case "scroll":
		if !bounded(m.Vertical) || !bounded(m.Horizontal) {
			return m, errors.New("invalid scroll")
		}
	case "text":
		if m.Text == "" || len(m.Text) > 2048 || !utf8.ValidString(m.Text) {
			return m, errors.New("invalid text")
		}
		for _, ch := range m.Text {
			if ch < 32 {
				return m, errors.New("control character in text")
			}
		}
	case "key":
		if m.Usage == nil || m.Down == nil || m.Modifiers < 0 || m.Modifiers > 255 {
			return m, errors.New("invalid key")
		}
		if _, ok := keyScanCode(*m.Usage); !ok {
			return m, errors.New("unsupported keyboard usage")
		}
	case "release_all", "ping":
	default:
		return m, fmt.Errorf("unsupported type %q", m.Type)
	}
	if m.Type != "hello" && m.Seq <= 0 {
		return m, errors.New("positive sequence required")
	}
	return m, nil
}

type Injector interface {
	Move(int, int) error
	Button(string, bool) error
	Scroll(int, int) error
	Key(int, bool) error
	Text(string) error
}

// Only release buttons actually pressed by this session, never unrelated keys.
type InputState struct {
	injector Injector
	held     map[string]bool
	keys     map[int]bool
}

func newState(i Injector) *InputState {
	return &InputState{i, make(map[string]bool), make(map[int]bool)}
}
func (s *InputState) apply(m Message) error {
	switch m.Type {
	case "text":
		if err := s.modifiers(0); err != nil {
			return err
		}
		return s.injector.Text(m.Text)
	case "move":
		return s.injector.Move(*m.DX, *m.DY)
	case "scroll":
		return s.injector.Scroll(*m.Vertical, *m.Horizontal)
	case "button":
		if s.held[m.Button] == *m.Down {
			return nil
		}
		// Remember down before attempting injection: even an uncertain failure must release.
		if *m.Down {
			s.held[m.Button] = true
		}
		if err := s.injector.Button(m.Button, *m.Down); err != nil {
			return err
		}
		if !*m.Down {
			delete(s.held, m.Button)
		}
	case "release_all":
		return s.release()
	case "key":
		if *m.Down {
			if err := s.modifiers(m.Modifiers); err != nil {
				return err
			}
			return s.key(*m.Usage, true)
		}
		if err := s.key(*m.Usage, false); err != nil {
			return err
		}
		return s.modifiers(m.Modifiers)
	}
	return nil
}
func (s *InputState) release() error {
	var errs []error
	// Release ordinary keys before modifiers so a dropped session cannot leave
	// Ctrl/Shift/Alt held, or turn a held shortcut into an ordinary repeated key.
	for usage := 4; usage < 224; usage++ {
		if s.keys[usage] {
			if err := s.key(usage, false); err != nil {
				errs = append(errs, err)
			}
		}
	}
	if err := s.modifiers(0); err != nil {
		errs = append(errs, err)
	}
	for _, b := range []string{"left", "right", "middle"} {
		if s.held[b] {
			if err := s.injector.Button(b, false); err != nil {
				errs = append(errs, err)
			} else {
				delete(s.held, b)
			}
		}
	}
	return errors.Join(errs...)
}

func (s *InputState) key(usage int, down bool) error {
	if s.keys[usage] == down {
		return nil
	}
	if down {
		s.keys[usage] = true
	}
	if err := s.injector.Key(usage, down); err != nil {
		return err
	}
	if !down {
		delete(s.keys, usage)
	}
	return nil
}
func (s *InputState) modifiers(mask int) error {
	for bit := 0; bit < 8; bit++ {
		if err := s.key(224+bit, mask&(1<<bit) != 0); err != nil {
			return err
		}
	}
	return nil
}
