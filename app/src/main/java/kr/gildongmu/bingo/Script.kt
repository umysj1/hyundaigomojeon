package kr.gildongmu.bingo

import android.content.Context
import org.json.JSONObject

/** 공부 카드 퀴즈: 두 가지 중 하나 고르기. [answer]는 정답 번호. */
data class Quiz(val question: String, val options: List<String>, val answer: Int)

/**
 * 대본 모드(시연 영상용): 생성형 AI로 미리 만든 대사·카드 설명·일기(assets/script_demo.json)를 쓴다.
 * 실제 서비스에서는 같은 자리에서 AI가 그때그때 문장을 만든다. 일어난 일(휴게소·터널 이름, 빙고 수)은 그대로 채운다.
 */
class ScriptPack(context: Context) {
    private val root = JSONObject(context.assets.open("script_demo.json").bufferedReader().use { it.readText() })
    private val lines = root.getJSONObject("lines")
    private val cards = root.getJSONObject("cards")
    private val diaries = root.getJSONArray("diaries")
    private val quizzes = root.optJSONObject("quizzes") ?: JSONObject()

    val diaryCount get() = diaries.length()
    fun diaryName(i: Int): String = diaries.getJSONObject(i.mod(diaryCount)).getString("name")

    /** 사건별 대사. 빈 값이 들어가면 null. */
    fun line(key: String, vars: Map<String, String>): String? = lines.optString(key).takeIf { it.isNotBlank() }?.let { fill(it, vars) }

    /** 관광정보 카드의 아이 말투 설명(없으면 null). */
    fun card(title: String): String? = cards.optString(title).takeIf { it.isNotBlank() }

    /** 카드에 딸린 퀴즈(카드 설명에 나온 사실로만 만든 것, 없으면 null). */
    fun quiz(title: String): Quiz? = quizzes.optJSONObject(title)?.let { q ->
        val o = q.getJSONArray("o")
        Quiz(q.getString("q"), List(o.length()) { o.getString(it) }, q.getInt("a"))
    }

    fun diary(i: Int, vars: Map<String, String>): List<String> {
        val a = diaries.getJSONObject(i.mod(diaryCount)).getJSONArray("lines")
        return List(a.length()) { a.getString(it) }.mapNotNull { fill(it, vars) }
    }

    companion object {
        /**
         * "{?x}"로 시작하면 x가 비었을 때 줄을 버린다. "{x}"는 값으로 바꾸고, 바로 뒤 "[을를]" 같은 조사는 앞 글자 받침에 맞춘다.
         * 값이 빈 "{x}"가 남으면 그 줄은 버린다(어색한 문장 방지).
         */
        fun fill(template: String, vars: Map<String, String>): String? {
            var t = template
            Regex("^\\{\\?(\\w+)\\}").find(t)?.let { m ->
                if (vars[m.groupValues[1]].isNullOrBlank()) return null
                t = t.substring(m.range.last + 1)
            }
            var missing = false
            t = Regex("\\{(\\w+)\\}").replace(t) { m -> vars[m.groupValues[1]].takeUnless { it.isNullOrBlank() } ?: run { missing = true; "" } }
            if (missing) return null
            return Regex("(.)\\[(을를|이가|은는|과와|으로로)\\]").replace(t) { m ->
                val c = m.groupValues[1][0]
                val final = if (c in '가'..'힣') (c - '가') % 28 else 0
                val pick = when (m.groupValues[2]) {
                    "을를" -> if (final != 0) "을" else "를"
                    "이가" -> if (final != 0) "이" else "가"
                    "은는" -> if (final != 0) "은" else "는"
                    "과와" -> if (final != 0) "과" else "와"
                    else -> if (final != 0 && final != 8) "으로" else "로" // ㄹ 받침은 '로'
                }
                m.groupValues[1] + pick
            }
        }
    }
}
