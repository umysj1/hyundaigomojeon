package kr.gildongmu.bingo

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo

/** 차량·내비 신호를 받아 빙고 칸을 채우고, 말할 문장을 돌려준다. */
class Game(
    val route: List<TBTInfo>,
    val routeName: String,
    val routeKm: Int,
    val routeMin: Int,
    val board: Board,
    private val narrator: Narrator = TemplateNarrator,
) {
    var passedIndex = -1; private set
    var blinks = 0; private set
    var arrived = false; private set
    private var lines = 0
    /** 채운 순서대로의 칸(도착 일기에 쓴다). */
    val checkedOrder = mutableListOf<Cell>()

    val passedKm: Double
        get() = route.take(passedIndex + 1).sumOf { it.distance } / 1000.0
    val passedMin: Int
        get() = if (routeKm <= 0) 0 else (passedKm / routeKm * routeMin).toInt()

    /** 경로 안내 목록에 있는 휴게소 진입 지점(인덱스). */
    val serviceAreas: List<Int>
        get() = route.indices.filter { route[it].type == TurnType.TURN_ENTER_SERVICEAREA && "휴게소" in route[it].description }

    /**
     * 지금 서 있는 곳 근처(3km 안)의 휴게소 이름. 휴게소 진입 지점까지의 누적 거리를 지나온 거리와 비교한다.
     * 차 위치 대신 안내 목록만 쓰는 근사라서, 실제 차에서는 GPS 좌표 확인을 더하면 좋다.
     */
    fun nearServiceArea(): String? {
        val cum = route.runningFold(0.0) { km, t -> km + t.distance / 1000.0 }.drop(1)
        return serviceAreas.minByOrNull { kotlin.math.abs(cum[it] - passedKm) }
            ?.takeIf { kotlin.math.abs(cum[it] - passedKm) <= 3.0 }
            ?.let { route[it].description }
    }

    /** 아직 안 지난 다음 휴게소 이름과 남은 거리(km). 경로에 더 없으면 null. */
    fun nextServiceArea(): Pair<String, Int>? {
        val cum = route.runningFold(0.0) { km, t -> km + t.distance / 1000.0 }.drop(1)
        val i = serviceAreas.firstOrNull { it > passedIndex && cum[it] > passedKm } ?: return null
        return route[i].description to Math.round(cum[i] - passedKm).toInt().coerceAtLeast(1)
    }

    /** 새 안내 목록이 오면 그 앞에 있던 지점은 지나온 것으로 본다. 빈 목록이면 도착. */
    fun onTbt(list: List<TBTInfo>): List<String> {
        if (list.isEmpty()) return onCar(CarEvent.ARRIVE)
        val head = list.first()
        val idx = (passedIndex + 1 until route.size).firstOrNull {
            route[it].type == head.type && route[it].description == head.description
        } ?: return emptyList()
        if (idx - 1 <= passedIndex) return emptyList()
        val said = mutableListOf<String>()
        for (i in passedIndex + 1 until idx) {
            passedIndex = i
            board.cells.filter { (it.trigger as? Trigger.Pass)?.index == i }.forEach { said += check(it) }
        }
        said += checkProgress()
        return said
    }

    fun onCar(event: CarEvent): List<String> {
        if (event == CarEvent.ARRIVE) {
            if (arrived) return emptyList()
            arrived = true
            passedIndex = route.lastIndex
        }
        val said = board.cells.filter { (it.trigger as? Trigger.Car)?.event == event }.flatMap { check(it) }.toMutableList()
        if (event == CarEvent.ARRIVE) {
            said += checkProgress()
            said += narrator.arrive(board.checkedCount, board.cells.size, board.lines())
        }
        return said
    }

    fun onBlink(): List<String> {
        blinks++
        return board.cells.filter { (it.trigger as? Trigger.Blinks)?.let { t -> blinks >= t.count } == true }.flatMap { check(it) }
    }

    fun onTap(cell: Cell): List<String> =
        if (cell.trigger == Trigger.Tap) check(cell) else emptyList()

    private fun checkProgress(): List<String> = board.cells.filter {
        when (val t = it.trigger) {
            is Trigger.Distance -> passedKm >= t.km
            is Trigger.Minutes -> passedMin >= t.min
            else -> false
        }
    }.flatMap { check(it) }

    private fun check(cell: Cell): List<String> {
        if (cell.checked) return emptyList()
        cell.checked = true
        checkedOrder += cell
        val said = mutableListOf(narrator.checked(cell))
        val now = board.lines()
        if (now > lines) {
            lines = now
            said += narrator.bingo(now)
        }
        return said
    }
}

/** 칸이 채워질 때 할 말. 지금은 템플릿, 나중에 생성형 AI로 바꾼다. */
interface Narrator {
    fun checked(cell: Cell): String
    fun bingo(lines: Int): String
    fun arrive(checked: Int, total: Int, lines: Int): String
}

object TemplateNarrator : Narrator {
    override fun checked(cell: Cell): String = when (val t = cell.trigger) {
        is Trigger.Pass -> when {
            cell.title.endsWith("톨게이트") -> "${cell.title} 통과! 빙고 한 칸."
            "휴게소" in cell.title -> "방금 ${cell.title.removeSuffix(" 지나기")}를 지났어. 한 칸 채웠어!"
            else -> "${cell.title} 성공! 한 칸 채웠어."
        }
        is Trigger.Car -> when (t.event) {
            CarEvent.RAIN -> "와이퍼가 움직였어. 비 만나기 칸 완성!"
            CarEvent.TUNNEL -> "터널 칸을 채웠어."
            CarEvent.REST_STOP -> "휴게소에서 쉬어 가기 성공!"
            CarEvent.ARRIVE -> "도착 칸까지 채웠어!"
        }
        is Trigger.Distance -> "벌써 ${t.km}킬로미터를 달렸어!"
        is Trigger.Minutes -> "출발하고 한 시간이 지났어. 한 칸 채웠어."
        is Trigger.Blinks -> "깜빡이 ${t.count}번! 아빠 운전 잘하네."
        Trigger.Tap -> when (cell.tone) {
            Tone.FAMILY -> "${cell.title.removeSuffix("께 여쭤보기")} 이야기 칸 완성!"
            Tone.FOOD -> "냠냠! ${cell.title.removeSuffix(" 사 먹기")} 칸 완성!"
            else -> "${cell.title} 칸 완성!"
        }
    }

    override fun bingo(lines: Int) = if (lines == 1) "빙고! 한 줄 완성!" else "빙고! 벌써 ${lines}줄이야!"

    override fun arrive(checked: Int, total: Int, lines: Int) =
        "도착했어요. 오늘은 ${total}칸 중 ${checked}칸, 빙고 ${lines}줄을 만들었어요."
}
