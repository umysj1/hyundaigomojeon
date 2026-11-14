package kr.gildongmu.bingo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 이번 길에서 있었던 일. 도착하면 이것으로 일기를 쓴다. */
data class Trip(
    val dest: String,
    val friend: String,
    val board: Board,
    /** 채운 순서대로의 칸. */
    val checkedOrder: List<Cell>,
    val km: Int,
    val minutes: Int,
    /** 이번 길에 도장 찍은 휴게소와 거기서 먹은 음식 이름. */
    val rests: List<String>,
    val foods: List<String>,
    val grewPoints: Int,
    /** 이번 길에 비를 만났는지(와이퍼), 지난 터널 이름. 빙고판에 칸이 없어도 일기에는 적는다. */
    val rained: Boolean,
    val tunnels: List<String>,
    /** 이번 길에 올라간 단계 이름(없으면 null). */
    val newLevel: String?,
    /** 공부 카드 퀴즈: 푼 문제 수와 맞힌 수. */
    val quizTotal: Int = 0,
    val quizRight: Int = 0,
)

/** 아이 일기 말투(~했다)로 이번 길을 적는다. 지금은 규칙으로 쓰고, 나중에 생성형 AI로 바꿀 수 있다. */
object DiaryWriter {
    fun write(t: Trip): List<String> {
        val out = mutableListOf<String>()
        out += "오늘은 ${t.friend}${josa(t.friend, "이랑", "랑")} ${t.dest}에 갔다."

        val passed = t.checkedOrder.filter { it.trigger is Trigger.Pass }.map { it.title.removeSuffix(" 지나기") }
            .filter { it.endsWith("톨게이트") || it.endsWith("휴게소") || it.endsWith("쉼터") }
        if (passed.isNotEmpty()) out += "가는 길에 ${passed.take(3).joinToString(", ")}${josa(passed.take(3).last(), "을", "를")} 지났다."

        val named = t.tunnels.filter { it.isNotBlank() }.distinct()
        val tunnel = when {
            t.tunnels.isEmpty() -> null
            named.isEmpty() -> "터널을 ${t.tunnels.size}개 지났다."
            t.tunnels.size == 1 -> "깜깜한 ${named[0]}${josa(named[0], "을", "를")} 지났다."
            else -> "${named.take(2).joinToString(", ")} 같은 터널을 ${t.tunnels.size}개나 지났다."
        }
        when {
            t.rained && tunnel != null -> out += "중간에 비가 와서 와이퍼가 움직였고, $tunnel"
            t.rained -> out += "중간에 비가 와서 와이퍼가 움직였다."
            tunnel != null -> out += tunnel
        }

        if (t.rests.isNotEmpty()) {
            val rest = t.rests.joinToString(", ")
            out += if (t.foods.isNotEmpty()) "${rest}에서 쉬면서 ${t.foods.joinToString(", ")}${josa(t.foods.last(), "을", "를")} 먹었다. 맛있었다!"
            else "${rest}에서 잠깐 쉬었다. 도감에 도장도 찍었다."
        } else if (t.foods.isNotEmpty()) {
            out += "휴게소에서 ${t.foods.joinToString(", ")}${josa(t.foods.last(), "을", "를")} 먹었다."
        }

        t.checkedOrder.filter { it.family && it.title.endsWith("여쭤보기") }.forEach {
            out += "${it.title.removeSuffix(" 여쭤보기")} \"${it.hint}\" 하고 여쭤봤다."
        }

        if (t.quizTotal > 0) out += "가는 길 퀴즈는 ${t.quizTotal}문제 중 ${t.quizRight}문제를 맞혔다."

        val lines = t.board.lines()
        out += if (lines > 0) "빙고는 ${lines}줄! ${t.board.cells.size}칸 중 ${t.board.checkedCount}칸을 채웠다."
        else "빙고는 아깝게 못 했지만 ${t.board.cells.size}칸 중 ${t.board.checkedCount}칸을 채웠다."

        out += if (t.newLevel != null) "${t.friend}${josa(t.friend, "이", "가")} ${t.newLevel}${josa(t.newLevel, "이", "가")} 됐다!"
        else "${t.friend}도 ${t.grewPoints}걸음 자랐다."

        out += if (t.board.checkedCount == t.board.cells.size) "빙고판을 다 채웠다. 최고의 하루!" else "다음엔 더 많이 채워야지!"
        return out
    }

    /** 받침이 있으면 [withFinal], 없으면 [noFinal]. 한글이 아니면 받침 없는 쪽. */
    fun josa(word: String, withFinal: String, noFinal: String): String {
        val c = word.lastOrNull { !it.isWhitespace() && it != ')' && it != '!' } ?: return noFinal
        if (c !in '가'..'힣') return noFinal
        return if ((c - '가') % 28 != 0) withFinal else noFinal
    }
}

/** 지난 일기. 앱을 다시 켜도 남는다. */
class DiaryStore(context: Context) {
    private val prefs = context.getSharedPreferences("diary", Context.MODE_PRIVATE)

    fun all(): JSONArray = runCatching { JSONArray(prefs.getString("all", "[]")) }.getOrElse { JSONArray() }

    /** [ai]는 AI가 쓴 일기면 그 일기 이름(아니면 빈 값). */
    fun save(t: Trip, lines: List<String>, ai: String = ""): JSONObject {
        val o = JSONObject()
            .put("date", SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(Date()))
            .put("dest", t.dest).put("friend", t.friend)
            .put("lines", JSONArray(lines))
            .put("bingo", t.board.lines()).put("checked", t.board.checkedCount).put("total", t.board.cells.size)
            .put("km", t.km).put("minutes", t.minutes)
            .put("quiz", t.quizTotal).put("quizRight", t.quizRight).put("ai", ai)
        prefs.edit().putString("all", all().put(o).toString()).apply()
        return o
    }
}
