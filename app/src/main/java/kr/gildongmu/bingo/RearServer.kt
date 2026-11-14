package kr.gildongmu.bingo

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * 뒷좌석 태블릿·휴대폰용 화면. 차와 같은 와이파이에 붙은 기기가 브라우저로 열면
 * 빙고판 전체를 보여 주고(주행 중에도), 누르는 칸은 아이가 직접 누를 수 있다.
 * GET /       화면(assets/rear.html)
 * GET /state  지금 빙고판(JSON, 메인 스레드가 [publish]로 넣어 둔 것)
 * GET /tap?i= 칸 누르기 → [onTap]
 * GET /quiz?a= 공부 카드 퀴즈 답 → [onQuiz]
 * GET /ask?k=  아이 부탁(toilet·hungry·sick) → [onAsk]
 */
class RearServer(
    context: Context,
    private val onTap: (Int) -> Unit,
    private val onQuiz: (Int) -> Unit = {},
    private val onAsk: (String) -> Unit = {},
) {
    private val page = context.assets.open("rear.html").bufferedReader().use { it.readText() }
    @Volatile private var state = "{}"
    @Volatile private var running = false
    private var socket: ServerSocket? = null

    fun publish(json: String) { state = json }

    fun start() {
        running = true
        thread(name = "rear-server", isDaemon = true) {
            runCatching {
                ServerSocket(PORT).use { server ->
                    socket = server
                    while (running) {
                        val client = server.accept()
                        thread(isDaemon = true) { handle(client) }
                    }
                }
            }.onFailure { if (running) Log.w(TAG, "rear server: ${it.message}") }
        }
    }

    private fun handle(client: Socket) = client.use {
        val line = BufferedReader(InputStreamReader(it.getInputStream())).readLine() ?: return
        val path = line.split(" ").getOrNull(1) ?: "/"
        val (type, body) = when {
            path == "/" || path.startsWith("/?") -> "text/html; charset=utf-8" to page
            path.startsWith("/state") -> "application/json; charset=utf-8" to state
            path.startsWith("/tap") -> {
                path.substringAfter("i=", "").toIntOrNull()?.let(onTap)
                "application/json; charset=utf-8" to "{\"ok\":true}"
            }
            path.startsWith("/quiz") -> {
                path.substringAfter("a=", "").toIntOrNull()?.let(onQuiz)
                "application/json; charset=utf-8" to "{\"ok\":true}"
            }
            path.startsWith("/ask") -> {
                path.substringAfter("k=", "").takeIf { it in ASKS }?.let(onAsk)
                "application/json; charset=utf-8" to "{\"ok\":true}"
            }
            else -> "text/plain; charset=utf-8" to "not found"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = it.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.write(bytes)
        out.flush()
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
    }

    companion object {
        const val TAG = "GILDONGMU"
        const val PORT = 8765
        val ASKS = setOf("toilet", "hungry", "sick")

        /** 차가 붙은 와이파이 주소. 없으면 null. */
        fun address(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}
