package kr.gildongmu.bingo

import android.content.Context
import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 전국 도로 터널 지도(assets/tunnels.json). OpenStreetMap에서 tunnel=yes인 도로를 받아 [이름, 위도·경도 목록]으로 줄인 것.
 * © OpenStreetMap contributors, ODbL. 차의 현재 위치가 터널 선 위(가까이)에 있으면 터널 안으로 본다.
 */
class Tunnels(context: Context) {
    private class Seg(val name: String, val lat1: Double, val lon1: Double, val lat2: Double, val lon2: Double)

    /** 0.01도(약 1km) 칸마다 지나는 선분. 위치마다 주변 9칸만 본다. */
    private val grid = HashMap<Long, MutableList<Seg>>()

    init {
        val arr = JSONArray(context.assets.open("tunnels.json").bufferedReader().use { it.readText() })
        for (i in 0 until arr.length()) {
            val t = arr.getJSONArray(i)
            val name = t.getString(0)
            val p = t.getJSONArray(1)
            for (k in 0 until p.length() / 2 - 1) {
                val s = Seg(name, p.getDouble(2 * k), p.getDouble(2 * k + 1), p.getDouble(2 * k + 2), p.getDouble(2 * k + 3))
                val r0 = cell(min(s.lat1, s.lat2)); val r1 = cell(max(s.lat1, s.lat2))
                val c0 = cell(min(s.lon1, s.lon2)); val c1 = cell(max(s.lon1, s.lon2))
                for (r in r0..r1) for (c in c0..c1) grid.getOrPut(key(r, c)) { mutableListOf() } += s
            }
        }
    }

    /** 이 위치가 터널 안이면 터널 이름(이름 없는 터널은 ""), 아니면 null. */
    fun at(lat: Double, lon: Double, meters: Double = NEAR_M): String? {
        val r = cell(lat); val c = cell(lon)
        var best: Seg? = null
        var bestD = meters
        for (dr in -1..1) for (dc in -1..1) grid[key(r + dr, c + dc)]?.forEach { s ->
            val d = distance(lat, lon, s)
            if (d <= bestD) { bestD = d; best = s }
        }
        return best?.name
    }

    /** 점과 선분 사이 거리(m). 짧은 거리라 평면으로 근사한다. */
    private fun distance(lat: Double, lon: Double, s: Seg): Double {
        val ky = 111_320.0
        val kx = 111_320.0 * cos(Math.toRadians(lat))
        val ax = (s.lon1 - lon) * kx; val ay = (s.lat1 - lat) * ky
        val bx = (s.lon2 - lon) * kx; val by = (s.lat2 - lat) * ky
        val dx = bx - ax; val dy = by - ay
        val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
        val px = ax + t * dx; val py = ay + t * dy
        return sqrt(px * px + py * py)
    }

    private fun cell(deg: Double) = Math.floor(deg * 100).toLong()
    private fun key(r: Long, c: Long) = r * 100_000 + c

    companion object {
        /** 차선 폭·위치 오차를 감안한 거리. */
        const val NEAR_M = 25.0
    }
}
