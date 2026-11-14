package kr.gildongmu.bingo

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import kotlin.random.Random

/** 칸이 어떻게 채워지는지. */
sealed interface Trigger {
    /** 내비 안내 목록의 index번째 지점을 지나면. */
    data class Pass(val index: Int) : Trigger
    /** 차량 신호로 판정하는 칸. */
    data class Car(val event: CarEvent) : Trigger
    /** 지나온 거리(km)가 넘으면. */
    data class Distance(val km: Int) : Trigger
    /** 예상 주행 시간 중 지난 시간(분)이 넘으면. */
    data class Minutes(val min: Int) : Trigger
    /** 깜빡이를 n번 켜면. */
    data class Blinks(val count: Int) : Trigger
    /** 동승자가 직접 누르는 칸(가족 대화, 창밖 찾기). */
    data object Tap : Trigger
}

enum class CarEvent { RAIN, TUNNEL, REST_STOP, ARRIVE }

/** 칸 종류별 색. */
enum class Tone { ROAD, REST, TOLL, SKY, FAMILY, FUN, FOOD, GOAL }

data class Cell(
    val title: String,
    val hint: String,
    val trigger: Trigger,
    val tone: Tone,
    val sprite: String,
    var checked: Boolean = false,
) {
    val family get() = tone == Tone.FAMILY
}

class Board(val size: Int, val cells: List<Cell>) {
    val checkedCount get() = cells.count { it.checked }

    /** 완성된 줄 수(가로·세로·대각선). */
    fun lines(): Int {
        val n = size
        fun at(r: Int, c: Int) = cells[r * n + c].checked
        var count = 0
        for (r in 0 until n) if ((0 until n).all { at(r, it) }) count++
        for (c in 0 until n) if ((0 until n).all { at(it, c) }) count++
        if ((0 until n).all { at(it, it) }) count++
        if ((0 until n).all { at(it, n - 1 - it) }) count++
        return count
    }
}

/** 내비 안내 목록과 경로 정보로 이번 길에 실제로 나올 칸만 골라 빙고판을 만든다. */
object BoardMaker {
    val GRANDMA_ASKS = listOf("어릴 때 제일 좋아한 간식은?", "이 길을 처음 와 본 건 언제?", "엄마 아빠 어릴 때 별명은?")
    val GRANDPA_ASKS = listOf("젊을 때 처음 탄 차는?", "제일 기억나는 여행지는?", "어릴 때 하던 놀이는?")

    /** 휴게소 음식 종류(이름, 도트 그림). */
    val FOODS = listOf(
        "호두과자" to "walnut", "소떡소떡" to "sotteok", "휴게소 감자" to "potato",
        "핫바" to "hotbar", "우동" to "udon", "아빠 커피" to "coffee",
    )

    fun make(route: List<TBTInfo>, routeKm: Int, routeMin: Int, seed: Int): Board {
        val size = if (routeMin >= 60) 4 else 3
        val picks = mutableListOf<Cell>()

        fun firstIndex(vararg types: TurnType, skip: (TBTInfo) -> Boolean = { false }) =
            route.indices.firstOrNull { route[it].type in types && !skip(route[it]) }

        route.indices.filter { route[it].type == TurnType.TURN_TOLL }.take(2).forEach {
            picks += Cell(route[it].description, "톨게이트를 지나면 채워져요", Trigger.Pass(it), Tone.TOLL, "toll")
        }
        route.indices.filter {
            route[it].type == TurnType.TURN_ENTER_SERVICEAREA && "휴게소" in route[it].description
        }.take(3).forEach {
            picks += Cell("${route[it].description} 지나기", "휴게소 앞을 지나면 채워져요", Trigger.Pass(it), Tone.REST, "rest")
        }
        firstIndex(TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_C)?.let {
            picks += Cell("고속도로 올라타기", route[it].description, Trigger.Pass(it), Tone.ROAD, "road")
        }
        route.indices.lastOrNull {
            route[it].type in listOf(TurnType.TURN_EXIT_IC_L, TurnType.TURN_EXIT_IC_R, TurnType.TURN_EXIT_IC_C)
        }?.let { picks += Cell("고속도로에서 내려오기", route[it].description, Trigger.Pass(it), Tone.ROAD, "exit") }
        firstIndex(TurnType.TURN_RESTAREA)?.let {
            picks += Cell("졸음쉼터 지나기", route[it].description, Trigger.Pass(it), Tone.REST, "moon")
        }
        route.indices.firstOrNull { route[it].type.name.startsWith("TURN_ENTER_ROUNDABOUT") }?.let {
            picks += Cell("회전교차로 돌기", route[it].description, Trigger.Pass(it), Tone.ROAD, "roundabout")
        }

        picks += Cell("비 만나기", "와이퍼가 움직이면 채워져요", Trigger.Car(CarEvent.RAIN), Tone.SKY, "rain")
        picks += Cell("터널 통과", "지도 속 터널을 지나면 채워져요", Trigger.Car(CarEvent.TUNNEL), Tone.SKY, "tunnel")
        picks += Cell("휴게소에서 쉬어 가기", "차를 세우고 문을 열면 채워져요", Trigger.Car(CarEvent.REST_STOP), Tone.REST, "cup")
        picks += Cell("깜빡이 10번", "아빠가 깜빡이를 켤 때마다 하나씩", Trigger.Blinks(10), Tone.FUN, "blink")
        val km = if (routeKm >= 150) 100 else 30
        if (routeKm > km) picks += Cell("${km}km 달리기", "지나온 거리로 채워져요", Trigger.Distance(km), Tone.FUN, "car")
        if (routeMin >= 80) picks += Cell("1시간 달리기", "출발하고 한 시간이 지나면", Trigger.Minutes(60), Tone.FUN, "clock")
        // 할머니·할아버지 칸 하나씩과 휴게소 음식 칸 하나는 늘 넣는다. 나머지는 무작위.
        val ask = Random(seed + 3)
        val must = mutableListOf(
            Cell("할머니께 여쭤보기", GRANDMA_ASKS.random(ask), Trigger.Tap, Tone.FAMILY, "grandma"),
            Cell("할아버지께 여쭤보기", GRANDPA_ASKS.random(ask), Trigger.Tap, Tone.FAMILY, "grandpa"),
        )
        if (route.any { it.type == TurnType.TURN_ENTER_SERVICEAREA && "휴게소" in it.description }) {
            val foods = FOODS.shuffled(Random(seed + 7)).map { (name, sprite) ->
                Cell("$name 사 먹기", "휴게소에서 먹고 나서 눌러요", Trigger.Tap, Tone.FOOD, sprite)
            }
            must += foods[0]
            picks += foods[1]
        }

        val fillers = listOf(
            Cell("창밖에서 소 찾기", "찾으면 눌러요", Trigger.Tap, Tone.FUN, "cow"),
            Cell("빨간 트럭 찾기", "찾으면 눌러요", Trigger.Tap, Tone.FUN, "truck"),
            Cell("다 같이 노래 한 곡", "부르고 나서 눌러요", Trigger.Tap, Tone.FAMILY, "note"),
        )
        val need = size * size - 1 // 마지막 칸은 도착
        val chosen = (must + picks.shuffled(Random(seed)).take(need - must.size)).toMutableList()
        fillers.forEach { if (chosen.size < need) chosen += it }
        chosen += Cell("도착!", "목적지에 도착하면 채워져요", Trigger.Car(CarEvent.ARRIVE), Tone.GOAL, "flag")
        return Board(size, chosen.shuffled(Random(seed + 1)))
    }
}
