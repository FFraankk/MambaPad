package com.github.ifeel.keysjoy.transport

import com.github.ifeel.keysjoy.input.InputTransport
import com.github.ifeel.keysjoy.input.RemoteInputEvent
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** One worker owns all socket IO and sequence numbers. Never buffers across sessions. */
class AdbSocketTransport(private val status: (String) -> Unit) : InputTransport {
    @Volatile private var running = true
    @Volatile override var isConnected = false
        private set
    @Volatile private var socket: Socket? = null
    @Volatile override var supportsKeyboard = false
        private set
    @Volatile var supportsText = false
        private set
    private val queue = ArrayBlockingQueue<RemoteInputEvent>(256)
    private val worker = Thread({ runConnectionLoop() }, "KeysJoy-USB").apply { isDaemon = true; start() }

    override fun send(event: RemoteInputEvent) {
        if (!isConnected) return
        // Fail the entire session on overflow instead of ever losing a button-up.
        if (!queue.offer(event)) socket?.close()
    }

    override fun close() {
        running = false
        isConnected = false
        socket?.close()
        worker.interrupt()
        queue.clear()
    }

    private fun runConnectionLoop() {
        var delay = 500L
        while (running) {
            try {
                status("Connecting / 正在连接")
                Socket().use { s ->
                    socket = s
                    s.tcpNoDelay = true
                    s.soTimeout = 1500
                    s.connect(InetSocketAddress("127.0.0.1", 27183), 1500)
                    val writer = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
                    val input = s.getInputStream()
                    fun readReply(): JSONObject {
                        val buffer = java.io.ByteArrayOutputStream()
                        while (true) {
                            val b = input.read()
                            if (b == -1) error("Receiver closed")
                            if (b == 10) break
                            if (buffer.size() >= 4096) error("Oversized reply")
                            buffer.write(b)
                        }
                        return JSONObject(buffer.toString("UTF-8"))
                    }
                    fun write(json: JSONObject) { writer.write(json.toString()); writer.newLine(); writer.flush() }
                    write(JSONObject().put("v", 1).put("type", "hello").put("client", "KeysJoy-Android").put("protocol", 1))
                    val welcome = readReply()
                    check(welcome.getInt("v") == 1 && welcome.getString("type") == "welcome" && welcome.getInt("protocol") == 1)
                    val features = welcome.optJSONArray("features")
                    supportsKeyboard = features != null && (0 until features.length()).any { features.optString(it) == "keyboard" }
                    supportsText = features != null && (0 until features.length()).any { features.optString(it) == "text" }
                    if (!running) return
                    queue.clear()
                    isConnected = true
                    delay = 500L
                    status("Connected / USB 已连接")
                    var seq = 0L
                    var lastPing = 0L
                    while (running && !s.isClosed) {
                        val untilPing = (500 - (android.os.SystemClock.elapsedRealtime() - lastPing)).coerceIn(1, 500)
                        val event = queue.poll(untilPing, TimeUnit.MILLISECONDS)
                        if (event != null) write(serialize(event).put("seq", ++seq))
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastPing >= 500) {
                            write(JSONObject().put("v", 1).put("type", "ping").put("seq", ++seq))
                            val pong = readReply()
                            check(pong.getInt("v") == 1 && pong.getString("type") == "pong" && pong.getLong("seq") == seq)
                            lastPing = now
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) status("Disconnected / 未连接：请运行电脑 EXE 并确认 USB 调试授权")
            } finally {
                isConnected = false
                supportsKeyboard = false
                supportsText = false
                socket?.close()
                socket = null
                queue.clear()
            }
            if (running) {
                status("Retrying / ${delay}ms 后重连；请确认电脑 EXE 和 USB 授权")
                try { Thread.sleep(delay) } catch (_: InterruptedException) { return }
                delay = (delay * 2).coerceAtMost(5000)
            }
        }
    }

    private fun serialize(event: RemoteInputEvent): JSONObject = JSONObject().put("v", 1).apply {
        when (event) {
            is RemoteInputEvent.Move -> put("type", "move").put("dx", event.dx).put("dy", event.dy)
            is RemoteInputEvent.Button -> put("type", "button").put("button", event.button).put("down", event.down)
            is RemoteInputEvent.Scroll -> put("type", "scroll").put("vertical", event.vertical).put("horizontal", event.horizontal)
            is RemoteInputEvent.Key -> put("type", "key").put("usage", event.usage).put("down", event.down).put("modifiers", event.modifiers)
            is RemoteInputEvent.Text -> put("type", "text").put("text", event.text)
            RemoteInputEvent.ReleaseAll -> put("type", "release_all")
        }
    }
}
