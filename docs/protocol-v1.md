# USB protocol v1

UTF-8 JSON Lines, LF separated; maximum 4096 bytes per line. TCP frame boundaries are unrelated to JSON message boundaries. One active session on Windows loopback port 27183. Sequence numbers restart per connection and must strictly increase after handshake.

Handshake:

```json
{"v":1,"type":"hello","client":"KeysJoy-Android","protocol":1}
{"v":1,"type":"welcome","protocol":1}
```

Client messages:

```json
{"v":1,"type":"move","dx":12,"dy":-3,"seq":1}
{"v":1,"type":"button","button":"left","down":true,"seq":2}
{"v":1,"type":"scroll","vertical":120,"horizontal":0,"seq":3}
{"v":1,"type":"release_all","seq":4}
{"v":1,"type":"ping","seq":5}
```

Server replies to ping with `{"v":1,"type":"pong","seq":5}`. Mouse buttons: left/right/middle. Movement and wheel axes must be integers in [-32767,32767]. Scroll uses Windows wheel delta units (120 = one notch), not raw HID wheel ticks. Android splits large deltas without discarding distance.

Missing required fields, unknown types/fields, invalid versions, duplicate/out-of-order sequence numbers, rates above 1000 messages/s and oversized lines terminate only that session. Idle receive timeout is 2s including handshake. Shutdown, parse errors, disconnects and injection errors attempt to release all buttons held by this session. The process listener remains available after client errors. Only one session is accepted at a time.

Android queue capacity is 256 events. Overflow closes the connection to force server release instead of losing a button-up. Events are never replayed across reconnections. The worker blocks waiting for events/heartbeat instead of polling continually; UI thread never writes sockets.

Keyboard and text events require the corresponding advertised feature, as described below. There is no application-layer authentication; do not expose this endpoint to a network. Local processes on the Windows host remain within the trust boundary.


## Optional keyboard and text features (1.2 preview)

The welcome message advertises `features: ["mouse", "keyboard", "text"]`. A client must check each feature before sending its optional events.

`{"v":1,"type":"key","seq":1,"usage":6,"down":true,"modifiers":1}` presses Ctrl+C using USB HID usages mapped explicitly to Windows set-1 scan codes. Follow with key-up and modifiers 0. Disconnect releases ordinary keys before modifiers.

`{"v":1,"type":"text","seq":2,"text":"中文😀"}` sends committed Unicode text, using Windows KEYEVENTF_UNICODE UTF-16 units. No clipboard is used. Text must be nonempty valid UTF-8, at most 2048 bytes, with no C0 control characters; the complete JSON line must still fit the existing 4096-byte bound. The Android sender chunks at 128 UTF-16 units and emits Enter/Tab as physical key events. Composing input is not sent until committed. Text injection releases session modifiers first. Bluetooth HID has no corresponding Unicode event.
