package kr.gildongmu.bingo

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import android.speech.tts.TextToSpeech as AndroidTts

/** 짧은 안내를 차례로 읽는다. Pleos 온디바이스 TTS를 먼저 쓰고, 안 되면 안드로이드 TTS로 바꾼다. */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var speaking = false
    private var pleos: TextToSpeech? = null
    private var fallback: AndroidTts? = null

    fun start() {
        runCatching {
            val tts = TextToSpeech(app, Mode.ON_DEVICE)
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() { Log.i(TAG, "pleos tts ready"); main.post { pleos = tts; next() } }
                override fun onFailure(type: ErrorType) { Log.w(TAG, "pleos tts init: $type"); main.post { useAndroid() } }
            })
            tts.addEventListener(object : EventListener {
                override fun onStart() {}
                override fun onReady() {}
                override fun onDone() = main.post { speaking = false; next() }.let {}
                override fun onError(type: ErrorType) = main.post { Log.w(TAG, "tts: $type"); speaking = false; next() }.let {}
                override fun onStop() = main.post { speaking = false }.let {}
                override fun onUpdatedRms(rms: Double) {}
            })
        }.onFailure { Log.w(TAG, "pleos tts: ${it.message}"); useAndroid() }
    }

    private fun useAndroid() {
        if (fallback != null) return
        fallback = AndroidTts(app) { status ->
            if (status == AndroidTts.SUCCESS) fallback?.language = Locale.KOREAN
            main.post { next() }
        }
    }

    fun say(lines: List<String>) = lines.forEach { say(it) }

    fun say(text: String) {
        Log.i(TAG, "say: $text")
        queue.addLast(text)
        next()
    }

    private fun next() {
        val p = pleos
        val a = fallback
        if (p != null) {
            if (speaking) return
            val text = queue.removeFirstOrNull() ?: return
            speaking = true
            runCatching { p.speak(text, false) }.onFailure { speaking = false; Log.w(TAG, "speak: ${it.message}") }
        } else if (a != null) {
            while (queue.isNotEmpty()) a.speak(queue.removeFirst(), AndroidTts.QUEUE_ADD, null, null)
        }
    }

    fun release() {
        runCatching { pleos?.release() }
        fallback?.shutdown()
    }

    companion object { const val TAG = "GILDONGMU" }
}
