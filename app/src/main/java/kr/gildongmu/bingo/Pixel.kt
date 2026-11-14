package kr.gildongmu.bingo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * 12×12 도트 그림. 한 글자가 한 칸이다.
 * '.' 빈칸, 'A' 칸 종류 색(채운 칸에선 연한 색), 나머지는 [PALETTE]의 고정 색.
 */
object Sprites {
    val PALETTE = mapOf(
        'K' to "#1F2433", 'W' to "#FFFFFF", 'G' to "#A9B0BC", 'Y' to "#FFD23F",
        'R' to "#FF5A4E", 'O' to "#FF9A1F", 'S' to "#FFD9B8", 'H' to "#C9CCD3",
        'P' to "#FF9AA8", 'N' to "#8B5A2B", 'B' to "#2B2F3A", 'D' to "#2E6BE6",
        'E' to "#5CC97B", 'L' to "#7FD3F7", 'T' to "#E3A857", 'C' to "#FFF1D6",
    )

    val ALL: Map<String, List<String>> = mapOf(
        "road" to listOf(
            "EKGGGWWGGGKE",
            "EKGGGWWGGGKE",
            "EKGGGGGGGGKE",
            "EKGGGGGGGGKE",
            "EKGGGWWGGGKE",
            "EKGGGWWGGGKE",
            "EKGGGGGGGGKE",
            "EKGGGGGGGGKE",
            "EKGGGWWGGGKE",
            "EKGGGWWGGGKE",
            "EKGGGGGGGGKE",
            "EKGGGGGGGGKE",
        ),
        "exit" to listOf(
            "............",
            "..KKK.......",
            "..KAK.......",
            "..KAK.......",
            "..KAK.......",
            "..KAK...K...",
            "..KAKKKKAK..",
            "..KAAAAAAAK.",
            "..KAKKKKAK..",
            "..KKK...K...",
            "............",
            "............",
        ),
        "roundabout" to listOf(
            "............",
            "....KKKK....",
            "..KKAAAAKK..",
            ".KAAKKKKAAK.",
            ".KAK....KAK.",
            "KAK......KAK",
            "KAK......KAK",
            ".KAK....KAK.",
            ".KAAKKKKAAK.",
            "..KKAAAAKK..",
            "....KKKK....",
            "............",
        ),
        "toll" to listOf(
            "............",
            "............",
            ".KK.........",
            ".KAK........",
            ".KAKKKKKKKKK",
            ".KAKRWRWRWRK",
            ".KAKKKKKKKKK",
            ".KAK........",
            ".KAK........",
            ".KAK........",
            ".KAK........",
            "KKKKK.......",
        ),
        "rest" to listOf(
            "............",
            "..KKKKKKKK..",
            ".KAAAAAAAAK.",
            "KAAAAAAAAAAK",
            "KKKKKKKKKKKK",
            ".KWWWWWWWWK.",
            ".KWLLWWLLWK.",
            ".KWLLWWLLWK.",
            ".KWWWKKWWWK.",
            ".KWWWBBWWWK.",
            ".KWWWBBWWWK.",
            "KKKKKKKKKKKK",
        ),
        "moon" to listOf(
            "........KKKK",
            "..........K.",
            ".........K..",
            "....KKK.KKKK",
            "...KYYK.....",
            "..KYYK......",
            "..KYK.......",
            "..KYK.......",
            "..KYYK......",
            "...KYYKKKK..",
            "....KKYYYYK.",
            "......KKKK..",
        ),
        "rain" to listOf(
            "............",
            "....KKKK....",
            "..KKWWWWK...",
            ".KWWWWWWWKK.",
            "KWWWWWWWWWWK",
            "KWWWWWWWWWWK",
            ".KKKKKKKKKK.",
            "............",
            "..D...D...D.",
            ".D...D...D..",
            "............",
            "...D...D....",
        ),
        "tunnel" to listOf(
            "............",
            "....KKKK....",
            "..KKGGGGKK..",
            ".KGGGGGGGGK.",
            ".KGGKKKKGGK.",
            "KGGKBBBBKGGK",
            "KGKBBYYBBKGK",
            "KGKBBBBBBKGK",
            "KGKBBBBBBKGK",
            "KGKBBWWBBKGK",
            "KGKBBWWBBKGK",
            "KKKKKKKKKKKK",
        ),
        "cup" to listOf(
            ".......KK...",
            "......KK....",
            "..KKKKKKKK..",
            "..KWWWWWWK..",
            "..KAAAAAAK..",
            "...KAAAAK...",
            "...KAWAAK...",
            "...KAWAAK...",
            "...KAAAAK...",
            "....KAAK....",
            "....KKKK....",
            "............",
        ),
        "blink" to listOf(
            "............",
            "..........Y.",
            ".........Y..",
            "....KK......",
            "...KOK......",
            "..KOOKKKKKK.",
            ".KOOOOOOOOK.",
            "..KOOKKKKKK.",
            "...KOK......",
            "....KK......",
            ".........Y..",
            "..........Y.",
        ),
        "car" to listOf(
            "............",
            "............",
            "...KKKKKK...",
            "..KRWWKWWK..",
            ".KRRWWKWWRK.",
            "KRRRRRRRRRRK",
            "KRRRRRRRRRYK",
            "KKBBKKKKBBKK",
            ".KBBK..KBBK.",
            "..KK....KK..",
            "............",
            "............",
        ),
        "clock" to listOf(
            "............",
            "...KKKKKK...",
            "..KWWWWWWK..",
            ".KWWWKWWWWK.",
            ".KWWWKWWWWK.",
            ".KWWWKWWWWK.",
            ".KWWWKKKKWK.",
            ".KWWWWWWWWK.",
            ".KWWWWWWWWK.",
            "..KWWWWWWK..",
            "...KKKKKK...",
            "............",
        ),
        "grandma" to listOf(
            ".....KK.....",
            "....KHHK....",
            "..KKHHHHKK..",
            ".KHHHHHHHHK.",
            ".KHSSSSSSHK.",
            ".KSKKSSKKSK.",
            ".KSSSSSSSSK.",
            ".KSPSSSSPSK.",
            "..KSSKKSSK..",
            "...KSSSSK...",
            "..KAAAAAAK..",
            ".KAAAAAAAAK.",
        ),
        "grandpa" to listOf(
            "............",
            "...KKKKKK...",
            "..KSWSSSSK..",
            ".KHSSSSSSHK.",
            ".KHSSSSSSHK.",
            ".KSKKSSKKSK.",
            ".KSSSSSSSSK.",
            ".KSPSSSSPSK.",
            "..KSHHHHSK..",
            "...KSSSSK...",
            "..KAAAAAAK..",
            ".KAAAAAAAAK.",
        ),
        "flag" to listOf(
            ".KKKKKKKKK..",
            ".KWBWBWBWK..",
            ".KBWBWBWBK..",
            ".KWBWBWBWK..",
            ".KBWBWBWBK..",
            ".KKKKKKKKK..",
            ".K..........",
            ".K..........",
            ".K..........",
            ".K..........",
            ".K..........",
            "KKK.........",
        ),
        "cow" to listOf(
            "............",
            ".K........K.",
            ".KK......KK.",
            "..KKKKKKKK..",
            ".KWWBBWWWWK.",
            ".KWKWWWWKWK.",
            ".KWWWWWWBWK.",
            "..KPPPPPPK..",
            "..KPKPPKPK..",
            "..KPPPPPPK..",
            "...KKKKKK...",
            "............",
        ),
        "truck" to listOf(
            "............",
            "............",
            "KKKKKKK.....",
            "KRRRRRK.....",
            "KRRRRRKKKK..",
            "KRRRRRKWWRK.",
            "KRRRRRKRRRRK",
            "KRRRRRRRRRRK",
            "KKBBKKKKBBKK",
            ".KBBK..KBBK.",
            "..KK....KK..",
            "............",
        ),
        "note" to listOf(
            "......KK....",
            "......KAK...",
            "......KAAK..",
            "......KAKAK.",
            "......KAK.K.",
            "......KAK...",
            "......KAK...",
            "...KKKKAK...",
            "..KAAAAAK...",
            "..KAAAAAK...",
            "...KKKKK....",
            "............",
        ),
        "walnut" to listOf(
            "............",
            "...KKKKKK...",
            "..KNNNNNNK..",
            ".KNNTNNTNNK.",
            ".KNTTNNTTNK.",
            ".KNNNNNNNNK.",
            ".KNTNNNNTNK.",
            ".KNTTNNTTNK.",
            ".KNNNNNNNNK.",
            "..KNNNNNNK..",
            "...KKKKKK...",
            "............",
        ),
        "sotteok" to listOf(
            ".....KK.....",
            "...KRRRRK...",
            "...KRRRRK...",
            "...KCCCCK...",
            "...KCCCCK...",
            "...KRRRRK...",
            "...KRRRRK...",
            "...KCCCCK...",
            "...KCCCCK...",
            ".....KK.....",
            ".....NN.....",
            ".....NN.....",
        ),
        "potato" to listOf(
            "............",
            "..KKK..KKK..",
            ".KTTTKKTTTK.",
            ".KTYTTKTYTK.",
            ".KKKKKKKKKK.",
            ".KWWWWWWWWK.",
            "..KAAAAAAK..",
            "..KAWAAWAK..",
            "..KAAAAAAK..",
            "...KAAAAK...",
            "...KKKKKK...",
            "............",
        ),
        "hotbar" to listOf(
            "....KKKK....",
            "...KTTTTK...",
            "...KTNTTK...",
            "...KTTTTK...",
            "...KTTNTK...",
            "...KTTTTK...",
            "...KTNTTK...",
            "...KTTTTK...",
            "....KKKK....",
            ".....NN.....",
            ".....NN.....",
            ".....NN.....",
        ),
        "hanok" to listOf(
            "............",
            "K..........K",
            "KK.KKKKKK.KK",
            ".KKAAAAAAKK.",
            "KAAAAAAAAAAK",
            "KKKKKKKKKKKK",
            "..KTTKKTTK..",
            "..KTTKKTTK..",
            "..KCCKKCCK..",
            "..KTTKKTTK..",
            ".KKKKKKKKKK.",
            "............",
        ),
        "flask" to listOf(
            "....KKKK....",
            "....KWWK....",
            "....KWWK....",
            "...KWWWWK...",
            "..KWWWWWWK..",
            ".KWWWWWWWWK.",
            ".KAAAAAAAAK.",
            "KAAWAAAAAAAK",
            "KAAAAAAWAAAK",
            "KAAAAAAAAAAK",
            ".KKKKKKKKKK.",
            "............",
        ),
        "firework" to listOf(
            ".....Y......",
            ".Y...A...Y..",
            "..A..A..A...",
            "...A.A.A....",
            "....AWA.....",
            "YAAAWWWAAAY.",
            "....AWA.....",
            "...A.A.A....",
            "..A..A..A...",
            ".Y...A...Y..",
            ".....Y......",
            "............",
        ),
        "udon" to listOf(
            ".G..G..G....",
            "..G..G..G...",
            "............",
            "KKKKKKKKKKKK",
            "KYCYCYCYCYCK",
            "KAAAAAAAAAAK",
            ".KAAAAAAAAK.",
            ".KAAAAAAAAK.",
            "..KAAAAAAK..",
            "...KKKKKK...",
            "....KKKK....",
            "............",
        ),
        "coffee" to listOf(
            "...G..G.....",
            "....G..G....",
            "...G..G.....",
            "............",
            ".KKKKKKKK...",
            ".KNNNNNNKKK.",
            ".KAAAAAAK.K.",
            ".KAAAAAAK.K.",
            ".KAAAAAAKKK.",
            "..KAAAAK....",
            "..KKKKKK....",
            "KKKKKKKKKKK.",
        ),
    )
}

/** 그릴 때 쓰는 48×48 그림. 대부분은 Scale2x를 두 번 적용해 점을 잘게 하고 대각선을 둥글게 한다. */
object Smooth {
    /** 바퀴·체크무늬처럼 촘촘한 그림은 Scale2x가 뭉개서 원본을 그대로 4배로 키운다. */
    private val KEEP = setOf("car", "truck", "flag")
    private val cache = mutableMapOf<String, List<String>>()

    fun rows(name: String): List<String> = cache.getOrPut(name) {
        val raw = Sprites.ALL[name] ?: return@getOrPut emptyList()
        if (name in KEEP) grow(grow(raw)) else scale2x(scale2x(raw))
    }

    private fun grow(rows: List<String>) = rows.flatMap { r ->
        val wide = r.map { "$it$it" }.joinToString("")
        listOf(wide, wide)
    }

    /** Scale2x(EPX): 한 점을 2×2로 나누되, 이웃이 같은 쪽 모서리는 그 이웃 색으로 채운다. */
    fun scale2x(rows: List<String>): List<String> {
        val h = rows.size
        val w = rows.firstOrNull()?.length ?: 0
        fun at(y: Int, x: Int) = if (y in 0 until h && x in 0 until w) rows[y][x] else '.'
        val out = mutableListOf<String>()
        for (y in 0 until h) {
            val top = StringBuilder()
            val bottom = StringBuilder()
            for (x in 0 until w) {
                val e = at(y, x); val b = at(y - 1, x); val d = at(y, x - 1); val f = at(y, x + 1); val hh = at(y + 1, x)
                if (b != hh && d != f) {
                    top.append(if (d == b) d else e).append(if (b == f) f else e)
                    bottom.append(if (d == hh) d else e).append(if (hh == f) f else e)
                } else {
                    top.append(e).append(e)
                    bottom.append(e).append(e)
                }
            }
            out += top.toString()
            out += bottom.toString()
        }
        return out
    }
}

/** [Sprites] 그림 하나를 칸에 맞춰 네모 점으로 그린다. */
class PixelView(context: Context) : View(context) {
    private val paint = Paint().apply { isAntiAlias = false }
    private var rows: List<String> = emptyList()
    private var accent = Color.BLACK

    fun set(name: String, accent: Int) = setRows(Smooth.rows(name), accent)

    /** 정사각형이 아니어도 된다(친구 그림 64×72). */
    fun setRows(rows: List<String>, accent: Int) {
        this.rows = rows
        this.accent = accent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (rows.isEmpty()) return
        val h = rows.size
        val w = rows[0].length
        val cell = minOf(width.toFloat() / w, height.toFloat() / h)
        val left = (width - cell * w) / 2f
        val top = (height - cell * h) / 2f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                val color = when (ch) {
                    '.' -> return@forEachIndexed
                    'A' -> accent
                    'V' -> Color.parseColor(Friends.NAVY)
                    else -> Color.parseColor(Sprites.PALETTE[ch] ?: return@forEachIndexed)
                }
                paint.color = color
                canvas.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, paint)
            }
        }
    }
}
