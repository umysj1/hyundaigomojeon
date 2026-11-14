package kr.gildongmu.bingo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 휴게소 도감의 도장 하나. [foods]는 그 휴게소에서 먹은 음식의 도트 그림 이름. */
data class Stamp(val name: String, val first: String, var visits: Int, val foods: MutableSet<String>, var lastAt: Long)

/** 들른 휴게소마다 도장을 모은다. 앱을 다시 켜도 남는다. */
class Dex(context: Context) {
    private val prefs = context.getSharedPreferences("dex", Context.MODE_PRIVATE)
    val stamps: MutableList<Stamp> = load()

    enum class Visit { NEW, AGAIN, SAME_STOP }

    /** 도장을 찍는다. */
    fun visit(name: String): Visit {
        val now = System.currentTimeMillis()
        val old = stamps.firstOrNull { it.name == name }
        // 같은 휴게소에서 문을 여러 번 여닫아도 한 번만 센다.
        val result = when {
            old == null -> Visit.NEW.also {
                stamps += Stamp(name, SimpleDateFormat("yyyy.M.d", Locale.KOREA).format(Date(now)), 1, mutableSetOf(), now)
            }
            now - old.lastAt > SAME_STOP_MS -> Visit.AGAIN.also { old.visits++ }
            else -> Visit.SAME_STOP
        }
        old?.lastAt = now
        save()
        return result
    }

    /** 방금 들른 휴게소(1시간 안)에 먹은 음식을 붙인다. */
    fun ate(sprite: String) {
        val last = stamps.maxByOrNull { it.lastAt } ?: return
        if (System.currentTimeMillis() - last.lastAt > SAME_STOP_MS) return
        last.foods += sprite
        save()
    }

    fun json() = JSONArray().apply {
        stamps.forEach {
            put(JSONObject().put("name", it.name).put("first", it.first).put("visits", it.visits)
                .put("foods", JSONArray(it.foods.toList())).put("lastAt", it.lastAt))
        }
    }

    private fun save() = prefs.edit().putString("stamps", json().toString()).apply()

    private fun load(): MutableList<Stamp> {
        val arr = runCatching { JSONArray(prefs.getString("stamps", "[]")) }.getOrElse { JSONArray() }
        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val foods = o.getJSONArray("foods")
            Stamp(o.getString("name"), o.getString("first"), o.getInt("visits"),
                MutableList(foods.length()) { foods.getString(it) }.toMutableSet(), o.getLong("lastAt"))
        }
    }

    companion object {
        const val SAME_STOP_MS = 60 * 60 * 1000L
    }
}
