package kr.gildongmu.bingo

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 출발 전에 고르는 "가는 길 공부" 주제. */
enum class Topic(val label: String, val sprite: String, val color: String) {
    HISTORY("역사", "hanok", "#7B61FF"),
    SCIENCE("과학·자연", "flask", "#12A5DB"),
    FESTIVAL("축제", "firework", "#FF9A1F"),
    FOOD("맛집", "udon", "#F0508C"),
}

/** 뒷좌석에 보여 줄 카드 하나. 내용은 한국관광공사 관광정보(TourAPI)에서 온다. */
data class StudyCard(val topic: Topic, val title: String, val addr: String, val image: String, val text: String) {
    fun json(): JSONObject = JSONObject().put("topic", topic.name).put("title", title).put("addr", addr).put("image", image).put("text", text)

    companion object {
        fun from(o: JSONObject) = StudyCard(Topic.valueOf(o.getString("topic")), o.getString("title"), o.optString("addr"), o.optString("image"), o.optString("text"))
    }
}

/**
 * 도착지 근처(20km) 관광정보를 주제별로 가져온다. 한 번 받은 도착지는 파일로 남겨 다시 묻지 않는다.
 * 출처: 한국관광공사 TourAPI(공공누리). 키가 없으면 빈 목록.
 */
class StudyRepo(private val context: Context, private val key: String) {
    val hasKey get() = key.isNotBlank()

    fun load(lat: Double, lon: Double): Map<Topic, List<StudyCard>> {
        val cache = File(context.filesDir, "study_%.3f_%.3f.json".format(lat, lon))
        if (cache.exists()) runCatching { return parse(JSONObject(cache.readText())) }.onFailure { Log.w(TAG, "cache: ${it.message}") }
        // 앱에 같이 넣어 둔 도착지(시연용 전주). 키 없이 만든 제출용 APK도 이걸로 카드가 나온다.
        runCatching { return parse(JSONObject(context.assets.open("study/${cache.name}").bufferedReader().use { it.readText() })) }
        if (!hasKey) return emptyMap()
        val out = Topic.entries.associateWith { t -> runCatching { fetch(t, lat, lon) }.onFailure { Log.w(TAG, "tour $t: ${it.message}") }.getOrDefault(emptyList()) }
        if (out.values.any { it.isNotEmpty() }) cache.writeText(JSONObject().apply { out.forEach { (t, l) -> put(t.name, JSONArray(l.map { it.json() })) } }.toString())
        return out
    }

    private fun parse(o: JSONObject) = Topic.entries.associateWith { t ->
        val a = o.optJSONArray(t.name) ?: JSONArray()
        List(a.length()) { StudyCard.from(a.getJSONObject(it)) }
    }

    /**
     * 주제마다 고르는 기준(전주 한옥마을 기준 실제 응답을 보고 정함):
     * 역사=관광지 중 역사관광지(A0201), 과학·자연=과학관류 문화시설 + 자연관광지(A01),
     * 축제=행사 중 아직 안 끝난 것, 맛집=음식점 중 지역 음식 이름이 들어간 곳 먼저. 모두 사진 있는 것 먼저.
     */
    private fun fetch(topic: Topic, lat: Double, lon: Double): List<StudyCard> {
        val withImage = compareByDescending<JSONObject> { it.optString("firstimage").isNotBlank() }
        val picked = when (topic) {
            Topic.HISTORY -> list(12, lat, lon).filter { it.optString("cat2") == "A0201" }.sortedWith(withImage)
            Topic.SCIENCE -> list(14, lat, lon).filter { SCIENCE_WORDS.containsMatchIn(it.optString("title")) } +
                list(12, lat, lon).filter { it.optString("cat1") == "A01" }.sortedWith(withImage)
            Topic.FESTIVAL -> list(15, lat, lon).filter { notOver(it.optString("contentid")) }.sortedWith(withImage)
            Topic.FOOD -> list(39, lat, lon).sortedWith(withImage.thenByDescending { LOCAL_FOOD.containsMatchIn(it.optString("title")) })
                .sortedByDescending { LOCAL_FOOD.containsMatchIn(it.optString("title")) }
        }.take(PER_TOPIC)
        return picked.map { item ->
            val id = item.optString("contentid")
            var text = overview(id)
            if (topic == Topic.FESTIVAL) dates(id)?.let { text = "$it. $text" }
            StudyCard(topic, item.optString("title"), item.optString("addr1"), item.optString("firstimage"), text)
        }
    }

    private val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.KOREA).format(java.util.Date())

    /** 행사가 오늘 이후에도 열리면 true. 날짜를 모르면 보여 준다. */
    private fun notOver(id: String): Boolean = intro(id)?.optString("eventenddate")?.takeIf { it.length == 8 }?.let { it >= today } ?: true

    /** "11월 30일 ~ 12월 1일에 열려요" */
    private fun dates(id: String): String? {
        val o = intro(id) ?: return null
        fun md(s: String) = if (s.length == 8) "${s.substring(4, 6).toInt()}월 ${s.substring(6, 8).toInt()}일" else null
        val a = md(o.optString("eventstartdate")) ?: return null
        val b = md(o.optString("eventenddate"))
        return if (b == null || a == b) "${a}에 열려요" else "$a ~ ${b}에 열려요"
    }

    private val intros = mutableMapOf<String, JSONObject?>()
    private fun intro(id: String) = intros.getOrPut(id) {
        runCatching { get("detailIntro2", "contentId=$id&contentTypeId=15").optJSONObject("items")?.optJSONArray("item")?.optJSONObject(0) }.getOrNull()
    }

    private fun list(type: Int, lat: Double, lon: Double): List<JSONObject> {
        val body = get("locationBasedList2", "mapX=$lon&mapY=$lat&radius=20000&contentTypeId=$type&numOfRows=40&pageNo=1&arrange=E")
        val item = body.optJSONObject("items")?.opt("item") ?: return emptyList()
        return when (item) {
            is JSONArray -> List(item.length()) { item.getJSONObject(it) }
            is JSONObject -> listOf(item)
            else -> emptyList()
        }
    }

    /** 소개글에서 앞 두 문장만(아이가 읽기 좋게). */
    private fun overview(id: String): String {
        if (id.isBlank()) return ""
        val body = runCatching { get("detailCommon2", "contentId=$id") }.getOrNull() ?: return ""
        val raw = body.optJSONObject("items")?.optJSONArray("item")?.optJSONObject(0)?.optString("overview").orEmpty()
        val text = raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        // 문장 단위로 120자 안에서 자른다. 첫 문장부터 길면 말줄임.
        // 한글 바로 뒤의 마침표만 문장 끝으로 본다("1949. 8." 같은 날짜에서 자르지 않게).
        val sentences = text.split(Regex("(?<=[가-힣][.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(text) }
        val out = StringBuilder()
        for (s in sentences.take(2)) { if (out.isNotEmpty() && out.length + s.length > 120) break; if (out.isNotEmpty()) out.append(' '); out.append(s) }
        return if (out.length > 130) out.take(118).toString().substringBeforeLast(' ') + "…" else out.toString()
    }

    private fun get(op: String, query: String): JSONObject {
        val url = "$BASE/$op?serviceKey=${URLEncoder.encode(key, "UTF-8")}&MobileOS=AND&MobileApp=gildongmu&_type=json&$query"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        val text = c.inputStream.bufferedReader().use { it.readText() }
        val res = JSONObject(text).getJSONObject("response")
        val code = res.getJSONObject("header").optString("resultCode")
        check(code == "0000") { "$op resultCode=$code ${res.getJSONObject("header").optString("resultMsg")}" }
        return res.getJSONObject("body")
    }

    companion object {
        const val TAG = "GILDONGMU"
        const val BASE = "https://apis.data.go.kr/B551011/KorService2"
        const val PER_TOPIC = 3
        val SCIENCE_WORDS = Regex("과학|천문|우주|발명|에너지|자연사|생태|로봇|기상|공룡")
        /** 지역 음식이 이름에 드러나는 곳을 먼저(전주 기준으로 고름, 다른 지역도 흔한 이름들). */
        val LOCAL_FOOD = Regex("비빔밥|콩나물|국밥|한정식|밥상|피순대|순대|막걸리|백반|떡갈비|육전|팥죽|칼국수|메밀")
    }
}

/**
 * 고른 순서대로 길을 나눠, 지나온 비율에 맞는 카드를 고른다.
 * 예) 역사→맛집이면 앞 절반은 역사 카드, 뒤 절반은 맛집 카드.
 */
class StudyPlan(val order: List<Topic>, val cards: Map<Topic, List<StudyCard>>) {
    private val usable get() = order.filter { cards[it].orEmpty().isNotEmpty() }

    fun at(progress: Double): Pair<StudyCard, String>? {
        val topics = usable.ifEmpty { return null }
        val p = progress.coerceIn(0.0, 0.999)
        val seg = (p * topics.size).toInt()
        val list = cards.getValue(topics[seg])
        val i = ((p * topics.size - seg) * list.size).toInt().coerceIn(0, list.lastIndex)
        return list[i] to "${seg + 1}/${topics.size}"
    }
}
