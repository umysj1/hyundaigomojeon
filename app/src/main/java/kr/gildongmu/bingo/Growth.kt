package kr.gildongmu.bingo

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 친구가 자라는 단계. [points]는 그 단계가 되는 성장 점수, [hat]은 머리에 얹는 장식 이름. */
data class Level(val name: String, val points: Int, val hat: String?)

/**
 * 친구 키우기: 함께 달린 거리(km)와 출석한 날로 자란다.
 * 성장 점수 = 함께 달린 km + 출석한 날 × [DAY_BONUS]. 그래서 짧게 타는 날(학원, 마트)도 자라는 데 보탬이 된다.
 */
class Growth(context: Context) {
    private val prefs = context.getSharedPreferences("growth", Context.MODE_PRIVATE)
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    var km: Double = prefs.getFloat("km", 0f).toDouble(); private set
    val days: MutableSet<String> = prefs.getStringSet("days", emptySet())!!.toMutableSet()
    /** 퀴즈를 맞혀 받은 점수. */
    var bonus: Int = prefs.getInt("bonus", 0); private set

    val points get() = km.toInt() + days.size * DAY_BONUS + bonus
    val levelIndex get() = LEVELS.indexOfLast { points >= it.points }
    val level get() = LEVELS[levelIndex]
    val next get() = LEVELS.getOrNull(levelIndex + 1)

    /** 함께 달린 거리를 더한다. 단계가 올라가면 새 단계를 돌려준다. */
    fun addKm(delta: Double): Level? {
        if (delta <= 0) return null
        val before = levelIndex
        km += delta
        prefs.edit().putFloat("km", km.toFloat()).apply()
        return if (levelIndex > before) level else null
    }

    /** 퀴즈 점수를 더한다. 단계가 올라가면 새 단계를 돌려준다. */
    fun addBonus(n: Int): Level? {
        val before = levelIndex
        bonus += n
        prefs.edit().putInt("bonus", bonus).apply()
        return if (levelIndex > before) level else null
    }

    /** 오늘 출석 도장을 찍는다. 오늘 처음이면 이번 달 몇 번째인지, 이미 찍었으면 null. */
    fun attend(now: Date = Date()): Int? {
        val today = day.format(now)
        if (!days.add(today)) return null
        prefs.edit().putStringSet("days", days.toSet()).apply()
        return days.count { it.startsWith(today.take(7)) }
    }

    /** 이번 달 출석한 날(1~31). */
    fun daysThisMonth(now: Date = Date()): Set<Int> {
        val month = day.format(now).take(7)
        return days.filter { it.startsWith(month) }.map { it.takeLast(2).toInt() }.toSet()
    }

    companion object {
        const val DAY_BONUS = 5
        const val QUIZ_BONUS = 5
        val LEVELS = listOf(
            Level("아기", 0, null),
            Level("새싹", 30, "sprout"),
            Level("반짝별", 100, "star"),
            Level("탐험가", 300, "cap"),
            Level("길동무 대장", 1000, "crown"),
        )
    }
}
