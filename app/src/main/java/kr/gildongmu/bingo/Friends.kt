package kr.gildongmu.bingo

import android.content.Context

/** 아이가 하나 골라 키우는 도트 친구. 16×18 그림, 'A'는 몸 색. */
data class Friend(
    val id: String, val name: String, val kind: String, val colors: List<String>, val raw: List<String>,
    /** 왼눈·오른눈 2×2의 왼쪽 위 (행, 열)과 냠냠 때 혀를 넣을 행. */
    val eyes: List<Pair<Int, Int>>, val tongueRow: Int,
    /** 머리 꼭대기 행. 키우기 장식은 이 바로 위에 얹는다. */
    val headTop: Int,
)

/** 친구 표정. */
enum class Mood { NORMAL, JOY, UMBRELLA, SLEEPY, YUM }

object Friends {
    val ALL = listOf(
        Friend(
            "dog", "현댕이", "강아지", listOf("#F2C38B", "#FFFFFF", "#C98A55"), listOf(
                "................",
                ".....KKKKKK.....",
                "...KKAAAAAAKK...",
                "..KAAAAAAAAAAK..",
                ".KNKAAAAAAAAKNK.",
                "KNNKAKWAAKWAKNNK",
                "KNNKAKKAAKKAKNNK",
                "KNNKAAAAAAAAKNNK",
                ".KNKPAAKKAAPKNK.",
                ".KNKAAAAAAAAKNK.",
                "..KKAAAPPAAAKK..",
                "...KRRRRRRRRK...",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KKKKKKKKKKKK..",
            ), listOf(5 to 5, 5 to 9), 10, 1
        ),
        Friend(
            "cat", "현냥이", "고양이", listOf("#FFB067", "#C3C8D2", "#FFE7C2"), listOf(
                ".KK..........KK.",
                ".KAK........KAK.",
                ".KAPK......KPAK.",
                ".KAAPKKKKKKPAAK.",
                "KAAAAAAAAAAAAAAK",
                "KAAAAAAAAAAAAAAK",
                "KAAAKWAAAAKWAAAK",
                "KAAAKKAAAAKKAAAK",
                "KAPAAAAPPAAAAPAK",
                "KAAAAAKAAKAAAAAK",
                ".KAAAAAKKAAAAAK.",
                "..KKAAAAAAAAKK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KKKKKKKKKKKK..",
            ), listOf(6 to 4, 6 to 10), 11, 3
        ),
        Friend(
            "dino", "현룡이", "아기 공룡", listOf("#6CD18B", "#8EC2FF", "#C9A2FF"), listOf(
                ".....KK..KK.....",
                "....KOOKKOOK....",
                "...KKAAAAAAKK...",
                "..KAAAAAAAAAAK..",
                ".KAAAAAAAAAAAAK.",
                ".KAAKWAAAAKWAAK.",
                ".KAAKKAAAAKKAAK.",
                ".KAPAAAAAAAAPAK.",
                ".KAAAKAAAAKAAAK.",
                "..KAAAKKKKAAAK..",
                "...KKAAAAAAKK...",
                "..KKAAAAAAAAKK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KAAAAAAAAAAK..",
                "..KKKKKKKKKKKK..",
            ), listOf(5 to 4, 5 to 10), 10, 1
        ),
    )

    /** 'V' 현대 엠블럼 남색. 그 밖의 글자는 [Sprites.PALETTE]. */
    const val NAVY = "#002C5F"

    /**
     * 현대 엠블럼: 위키미디어 공용의 현대자동차 로고 SVG를 18×10으로 줄여 손으로 다듬은 것.
     * 다듬은 친구 그림(64×72) 위 배 자리에 그대로 얹는다(Scale2x를 거치면 H가 뭉개져서).
     */
    private val EMBLEM = listOf(
        ".....VVVVVVVV.....",
        "...VV.VV.....VVV..",
        ".VV..VV......VVVV.",
        "V....VV....VVVV..V",
        "V...VVVVVVVVVVV..V",
        "V...VVVVVVVVVV...V",
        "V..VVVV....VVV...V",
        ".VVVV......VV...V.",
        "..VVV.....VV..VV..",
        ".....VVVVVVVV.....",
    )
    private const val EMBLEM_TOP = 53
    private const val EMBLEM_LEFT = 23

    /** 표정 장식(우산·반짝이·Z·하트)이 들어갈 머리 위 빈 줄 수. */
    private const val HEAD_ROOM = 6

    private val UMBRELLA = listOf(
        "........K.......",
        "....KKRRRRKK....",
        "L.KKRRWRRWRRKK..",
        ".KRRRRWRRWRRRRK.",
        "KKKKKKKKKKKKKKKK",
        "........K.......",
    )
    /** 키우기 단계별 머리 장식([Growth.LEVELS]의 hat). 왼쪽 끝을 4열에 맞춘다. */
    private val HATS = mapOf(
        "sprout" to listOf(".KK..KK.", "KEEKKEEK", ".KKEEKK.", "...KK..."),
        "star" to listOf("...KK...", "..KYYK..", "KKYYYYKK", ".KYYYYK.", "KYYKKYYK", "KKK..KKK"),
        "cap" to listOf("..KKKK....", ".KDDDDK...", "KDDWWDDK..", "KKKKKKKKKK"),
        "crown" to listOf("K..KK..K", "KYKYYKYK", "KYYRRYYK", "KKKKKKKK"),
    )
    private val SPARKLE = listOf(".Y.", "YYY", ".Y.")
    private val ZZZ = listOf("KKKK", "..K.", ".K..", "KKKK")
    private val HEART = listOf("R.R", "RRR", ".R.")

    private val cache = mutableMapOf<String, List<String>>()

    fun byId(id: String?) = ALL.firstOrNull { it.id == id }

    /**
     * 그릴 때 쓰는 그림(Scale2x 두 번 + 엠블럼). [headRoom]이면 머리 위에 장식 자리를 둬서 64×96,
     * 아니면 64×72(친구 고르기 화면).
     */
    fun rows(friend: Friend, mood: Mood = Mood.NORMAL, headRoom: Boolean = false, hat: String? = null): List<String> =
        cache.getOrPut("${friend.id}/$mood/$headRoom/$hat") {
            val pad = if (headRoom) HEAD_ROOM else 0
            val raw = (List(pad) { ".".repeat(16) } + friend.raw).map { it.toCharArray() }
            fun put(top: Int, left: Int, art: List<String>) = art.forEachIndexed { y, line ->
                line.forEachIndexed { x, ch -> if (ch != '.') raw[top + y][left + x] = ch }
            }
            fun eyes(top: String, bottom: String) = friend.eyes.forEach { (r, c) ->
                put(pad + r, c - 1, listOf(top, bottom))
            }
            if (headRoom && hat != null && mood != Mood.UMBRELLA) HATS[hat]?.let { art ->
                put(pad + friend.headTop - art.size, 4, art)
            }
            when (mood) {
                Mood.NORMAL -> Unit
                Mood.JOY -> { eyes("AKKA", "KAAK"); if (headRoom) { put(2, 0, SPARKLE); put(2, 13, SPARKLE) } }
                Mood.UMBRELLA -> if (headRoom) put(1, 0, UMBRELLA)
                Mood.SLEEPY -> { eyes("AAAA", "KKKK"); if (headRoom) put(0, 11, ZZZ) }
                Mood.YUM -> {
                    eyes("AKKA", "KAAK")
                    put(pad + friend.tongueRow, 7, listOf("PP"))
                    if (headRoom) put(2, 12, HEART)
                }
            }
            val out = Smooth.scale2x(Smooth.scale2x(raw.map { String(it) })).map { it.toCharArray() }
            EMBLEM.forEachIndexed { y, line ->
                line.forEachIndexed { x, ch -> if (ch == 'V') out[EMBLEM_TOP + pad * 4 + y][EMBLEM_LEFT + x] = 'V' }
            }
            out.map { String(it) }
        }
}

/** 고른 친구와 색. 앱을 다시 켜도 남는다. */
class FriendStore(context: Context) {
    private val prefs = context.getSharedPreferences("friend", Context.MODE_PRIVATE)

    val friend get() = Friends.byId(prefs.getString("id", null))
    val color get() = prefs.getString("color", null) ?: friend?.colors?.first()

    fun save(friend: Friend, color: String) = prefs.edit().putString("id", friend.id).putString("color", color).apply()
}
