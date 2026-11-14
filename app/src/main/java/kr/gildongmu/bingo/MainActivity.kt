package kr.gildongmu.bingo

import ai.pleos.playground.navi.data.TBTInfo
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity(), SignalListener {
    private lateinit var signals: CarSignals
    private lateinit var voice: Voice
    private lateinit var rear: RearServer
    private lateinit var friends: FriendStore
    private lateinit var dex: Dex
    private lateinit var growth: Growth
    private lateinit var diaries: DiaryStore
    /** 이번 길 기록: 출발 시각, 출발 때 성장 점수·단계, 도장 찍은 휴게소. */
    private var tripStartMs = 0L
    private var tripPoints = 0
    private var tripLevel = 0
    private val tripRests = mutableListOf<String>()
    private var tripRain = false
    private val tripTunnels = mutableListOf<String>()
    /** 터널 지도(처음 켤 때 따로 읽는다). 지금 지나는 터널 이름, 터널 밖이면 null. */
    @Volatile private var tunnels: Tunnels? = null
    private var inTunnel: String? = null
    /** 가는 길 공부: 고른 주제 순서, 도착지 카드, 지금 보여 주는 카드. */
    private lateinit var study: StudyRepo
    private val studyOrder = mutableListOf<Topic>()
    private var studyCards: Map<Topic, List<StudyCard>> = emptyMap()
    private var studyAsked = false
    private var studyNow: Pair<StudyCard, String>? = null
    private var destName = ""
    /** 대본 모드(시연 영상): AI로 미리 만든 대사·카드 설명·일기를 쓴다. --ez script true / --ei diary 0..2 */
    private val scriptPrefs by lazy { getSharedPreferences("script", MODE_PRIVATE) }
    private val pack by lazy { ScriptPack(this) }
    private val scripted get() = scriptPrefs.getBoolean("on", false)
    private val shownCards = mutableListOf<String>()
    /** 지금 카드의 퀴즈와 아이가 고른 답(안 골랐으면 null). */
    private var quizNow: Quiz? = null
    private var quizChosen: Int? = null
    private var tripQuizTotal = 0
    private var tripQuizRight = 0
    /** 아이 부탁: 같은 부탁은 [ASK_GAP_MS] 안에 다시 알리지 않는다. 멀미면 [screenRestUntil]까지 뒷좌석 화면을 쉰다. */
    private val askedAt = mutableMapOf<String, Long>()
    private var screenRestUntil = 0L

    /** 대본 모드면 그 사건의 대사, 아니면 [plain]. */
    private fun line(key: String, plain: String, vararg vars: Pair<String, String>): String =
        if (scripted) pack.line(key, mapOf("friend" to (friends.friend?.name ?: "친구"), "dest" to (game?.routeName ?: destName)) + vars) ?: plain
        else plain
    /** 도착하면 채워지는 결과(일기 포함). */
    private var result: JSONObject? = null
    private var resultClosed = false
    /** 이번 경로에서 이미 성장에 더한 거리. */
    private var grownKm = 0.0
    private val main = Handler(Looper.getMainLooper())

    private var game: Game? = null
    private var pendingRoute: Triple<String, Int, Int>? = null
    private var parked = true
    private var wiperOn = false
    private var lowBeamOn = false
    private var rearGreeted = false
    private var beltThanked = false
    private var rearOccupied = false
    private var lastLines = 0
    /** 잠깐 보여 주는 표정(기쁨·냠냠)과 끝나는 시각. */
    private var flash = Mood.NORMAL
    private var flashUntil = 0L
    private var replaying = false
    private var replayAt = 0
    /** 시험 주행에서 안내 지점 하나를 지나는 간격. 영상 촬영 땐 --ei step_ms 5000 처럼 늘린다(대사가 겹치지 않게). */
    private var stepMs = STEP_MS
    /** 휴게소에 세우고 문을 연 뒤 다시 D에 넣기 전까지. 시험 주행도 이동 동안 멈춘다. */
    private var resting = false
    private var lastSaid = ""
    private var lastTbt = ""
    private var staleTbt = ""

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var progress: TextView
    private lateinit var grid: GridLayout
    private lateinit var driving: LinearLayout
    private lateinit var drivingCount: TextView
    private lateinit var drivingLast: TextView
    private lateinit var drivingDots: GridLayout
    private lateinit var buttons: LinearLayout
    private lateinit var empty: TextView
    private lateinit var rearPanel: LinearLayout
    private lateinit var rearQr: ImageView
    private lateinit var rearUrl: TextView
    private lateinit var headerFriend: PixelView
    private lateinit var drivingFriend: PixelView
    private lateinit var picker: LinearLayout
    private lateinit var pickerCards: LinearLayout
    private lateinit var pickerColors: LinearLayout
    private lateinit var pickerOk: Button
    private var pickFriend: Friend? = null
    private var pickColor = ""
    private lateinit var dexPanel: LinearLayout
    private lateinit var dexTitle: TextView
    private lateinit var dexGrid: GridLayout
    private lateinit var room: LinearLayout
    private lateinit var resultPanel: LinearLayout
    private lateinit var resultHead: TextView
    private lateinit var resultSub: TextView
    private lateinit var resultBoard: GridLayout
    private lateinit var resultStats: LinearLayout
    private lateinit var resultDate: TextView
    private lateinit var resultLines: LinearLayout
    private lateinit var resultFriend: PixelView
    private lateinit var roomFriend: PixelView
    private lateinit var roomTitle: TextView
    private lateinit var roomNext: TextView
    private lateinit var roomBar: View
    private lateinit var roomBarBox: FrameLayout
    private lateinit var roomStats: TextView
    private lateinit var roomCalendar: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        friends = FriendStore(this)
        Thread { tunnels = Tunnels(applicationContext) }.start()
        study = StudyRepo(applicationContext, getSharedPreferences("study", MODE_PRIVATE).getString("key", null) ?: BuildConfig.TOUR_KEY)
        dex = Dex(this)
        growth = Growth(this)
        diaries = DiaryStore(this)
        buildUi()
        voice = Voice(this).also { it.start() }
        signals = CarSignals(this, this).also { it.start() }
        rear = RearServer(this,
            onTap = { i -> main.post { tapCell(i) } },
            onQuiz = { a -> main.post { answerQuiz(a) } },
            onAsk = { k -> main.post { askFromRear(k) } },
        ).also { it.start() }
        friends.color?.let { signals.setAmbient(Color.parseColor(it)) }
        if (friends.friend == null) openPicker()
        render()
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** 시연용 adb 명령: --ez demo_route true / --ez replay true */
    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra("demo_route", false) == true) requestDemoRoute()
        if (intent?.getBooleanExtra("replay", false) == true) startReplay()
        if (intent?.getBooleanExtra("to_rest", false) == true) skipToRest()
        if (intent?.hasExtra("script") == true) scriptPrefs.edit().putBoolean("on", intent.getBooleanExtra("script", false)).apply()
        if (intent?.hasExtra("step_ms") == true) stepMs = intent.getIntExtra("step_ms", STEP_MS.toInt()).toLong()
        if (intent?.hasExtra("diary") == true) scriptPrefs.edit().putInt("diary", intent.getIntExtra("diary", 0)).apply()
        // 관광정보 키를 adb로 넣을 때: --es tour_key "..."
        intent?.getStringExtra("tour_key")?.let {
            getSharedPreferences("study", MODE_PRIVATE).edit().putString("key", it).apply()
            study = StudyRepo(applicationContext, it)
        }
        // 시연용: 실제 차 대신 위치를 넣는다. 예) --es at "37.2794,127.0153"
        intent?.getStringExtra("at")?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 2 }?.let {
            onLocation(it[0], it[1])
        }
    }

    // ---- 신호 ----

    override fun onRouteStarted(name: String, km: Int, min: Int) {
        Log.i(TAG, "route $name $km km $min min")
        pendingRoute = Triple(name, km, min)
        staleTbt = lastTbt
        game = null
        replaying = false
        main.postDelayed({ signals.refreshTbt() }, 1500)
        signals.requestDestination()
        studyCards = emptyMap()
        studyNow = null
        studyAsked = false
        // 같은 목적지를 다시 고른 경우엔 목록이 그대로라서, 5초 뒤엔 받은 목록을 그대로 쓴다.
        main.postDelayed({ if (pendingRoute != null) { staleTbt = ""; signals.refreshTbt() } }, 5000)
        render()
    }

    override fun onTbt(list: List<TBTInfo>) {
        val sig = signature(list)
        lastTbt = sig
        if (replaying) return
        // 새 경로가 시작된 직후 내비가 이전 경로 목록을 한 번 더 보낼 때가 있다(에뮬레이터에서 확인). 그 목록은 건너뛴다.
        if (pendingRoute != null && sig == staleTbt) return
        feedTbt(list)
    }

    private fun signature(list: List<TBTInfo>) = list.joinToString("|") { "${it.type}:${it.description}" }

    private fun feedTbt(list: List<TBTInfo>) {
        val g = game
        val p = pendingRoute
        if (g == null && p != null && list.isNotEmpty()) {
            grownKm = 0.0
            tripStartMs = System.currentTimeMillis()
            tripPoints = growth.points
            tripLevel = growth.levelIndex
            tripRests.clear()
            tripRain = false
            tripTunnels.clear()
            tripQuizTotal = 0
            tripQuizRight = 0
            quizNow = null
            result = null
            resultClosed = false
            val board = BoardMaker.make(list, p.second, p.third, p.first.hashCode())
            game = Game(list, p.first, p.second, p.third, board)
            pendingRoute = null
            shownCards.clear()
            say(listOf(line("board", "${p.first}까지 가는 빙고판을 만들었어요. ${board.cells.size}칸 중 몇 칸을 채울 수 있을까?", "dest" to p.first)))
            if (!studyAsked) { studyAsked = true; openStudyPicker() }
        } else if (g != null) {
            say(g.onTbt(list))
        }
        render()
    }

    override fun onArrived() {
        game?.let { say(it.onCar(CarEvent.ARRIVE)) }
        render()
    }

    override fun onGear(parked: Boolean) {
        this.parked = parked
        if (!parked) resting = false
        render()
    }

    /** 친구 표정: 잠깐 표정 > 비 오면 우산 > 뒷좌석이 비면 졸림. */
    private fun mood() = when {
        System.currentTimeMillis() < flashUntil -> flash
        wiperOn -> Mood.UMBRELLA
        !rearOccupied -> Mood.SLEEPY
        else -> Mood.NORMAL
    }

    private fun flashMood(m: Mood) {
        flash = m
        flashUntil = System.currentTimeMillis() + FLASH_MS
        main.postDelayed({ render() }, FLASH_MS + 50)
    }

    override fun onWiper(on: Boolean) {
        if (on && !wiperOn) game?.let {
            tripRain = true
            say((if (scripted) listOfNotNull(pack.line("rain", emptyMap())) else emptyList()) + it.onCar(CarEvent.RAIN))
        }
        wiperOn = on
        render()
    }

    override fun onLowBeam(on: Boolean) {
        lowBeamOn = on
        render()
    }

    /** 도착지 좌표가 오면 그 근처 관광정보를 미리 받아 둔다(가는 길 공부). */
    override fun onDestination(lat: Double, lon: Double, name: String) {
        if (lat == 0.0 && lon == 0.0) return // 경로가 아직 없을 때 오는 빈 값
        destName = name
        Log.i(TAG, "destination $name $lat,$lon key=${study.hasKey}")
        Thread {
            val cards = study.load(lat, lon)
            main.post {
                // 대본 모드면 카드 설명을 아이 말투(AI로 미리 다듬은 문장)로 바꾼다
                studyCards = if (!scripted) cards else cards.mapValues { (_, l) -> l.map { c -> pack.card(c.title)?.let { c.copy(text = it) } ?: c } }
                Log.i(TAG, "study ${cards.mapValues { it.value.size }}")
                if (studyPanel.visibility == View.VISIBLE) renderStudyPicker()
                render()
            }
        }.start()
    }

    /** 현재 위치가 터널 지도 위에 들어가면 터널을 지나는 것으로 본다. 밤에도 된다. */
    override fun onLocation(lat: Double, lon: Double) {
        val map = tunnels ?: return
        val name = map.at(lat, lon)
        if (name != null && inTunnel == null) {
            Log.i(TAG, "tunnel in: $name ($lat, $lon)")
            val g = game
            if (g != null) {
                tripTunnels += name
                val title = name.ifBlank { "터널" }
                say(listOf(line("tunnel", "지금 ${title}${DiaryWriter.josa(title, "을", "를")} 지나고 있어!", "tunnel" to title)) + g.onCar(CarEvent.TUNNEL))
                render()
            }
        }
        inTunnel = name
    }

    override fun onDoor(anyOpen: Boolean) {
        val g = game ?: return
        if (anyOpen && parked && !g.arrived && g.passedIndex >= 0) {
            resting = true
            say(g.onCar(CarEvent.REST_STOP))
            g.nearServiceArea()?.let { stampRest(it) }
        }
        render()
    }

    override fun onBlink() {
        game?.let { say(it.onBlink()) }
        render()
    }

    /** 뒷좌석에 앉으면 친구가 깨어나 인사하고, 무드등이 친구 색으로 바뀐다. */
    override fun onRearSeat(occupied: Boolean) {
        if (occupied != rearOccupied) { rearOccupied = occupied; render() }
        if (!occupied || rearGreeted) return
        rearGreeted = true
        val f = friends.friend
        if (f == null) {
            say(listOf("뒷좌석 친구, 반가워! 같이 갈 친구를 골라 줘."))
            openPicker()
            return
        }
        friends.color?.let { signals.setAmbient(Color.parseColor(it)) }
        flashMood(Mood.JOY)
        val lines = mutableListOf(line("greet", "${f.name}가 일어났어요! 안녕, 오늘도 같이 가자. 벨트 꼭 매 줘."))
        val before = growth.levelIndex
        growth.attend()?.let { n -> lines += "오늘 출석 도장 쾅! 이번 달 ${nth(n)} 출석이야." }
        if (growth.levelIndex > before) lines += levelUpLine()
        say(lines)
        render()
    }

    override fun onRearBelt(buckled: Boolean) {
        if (!buckled || beltThanked) return
        beltThanked = true
        val name = friends.friend?.name ?: return
        say(listOf("벨트 딸깍! ${name}도 꽉 맸어. 이제 출발 준비 끝!"))
    }

    /** 뒷좌석 아이가 퀴즈 답을 골랐다. 맞히면 친구가 기뻐하고 성장 점수를 받는다. */
    private fun answerQuiz(a: Int) {
        val q = quizNow ?: return
        if (quizChosen != null || a !in q.options.indices) return
        quizChosen = a
        tripQuizTotal++
        val name = friends.friend?.name ?: "친구"
        if (a == q.answer) {
            tripQuizRight++
            flashMood(Mood.JOY)
            val up = growth.addBonus(Growth.QUIZ_BONUS)
            say(listOf(line("quiz_right", "딩동댕! 맞았어. 성장 점수 ${Growth.QUIZ_BONUS}점!", "friend" to name)) + listOfNotNull(up?.let { levelUpLine() }))
        } else {
            say(listOf(line("quiz_wrong", "아깝다! 정답은 '${q.options[q.answer]}'.", "answer" to q.options[q.answer])))
        }
        render()
    }

    /**
     * 뒷좌석 아이의 부탁(화장실·배고파·멀미)을 운전자에게 소리로 전한다. 경로의 다음 휴게소까지 거리를 같이 알려 줘서
     * 운전자는 화면을 안 봐도 되고, 아이는 언제쯤 쉬는지 안다. 멀미면 뒷좌석 화면을 2분 쉬게 한다.
     */
    private fun askFromRear(k: String) {
        val now = System.currentTimeMillis()
        if (now - (askedAt[k] ?: 0L) < ASK_GAP_MS) {
            lastSaid = "아빠한테 벌써 말했어! 조금만 기다려 줘."
            render()
            return
        }
        askedAt[k] = now
        val g = game?.takeUnless { it.arrived }
        val rest = g?.nextServiceArea()
        val left = g?.let { (it.routeKm - it.passedKm).toInt().coerceAtLeast(0) }
        val where = when {
            rest != null -> "다음 ${rest.first}까지 ${rest.second}킬로미터예요."
            left != null -> "가는 길에 휴게소는 더 없어요. 도착까지 ${left}킬로미터예요."
            else -> ""
        }
        val (driver, kid) = when (k) {
            "toilet" -> "뒷자리에서 화장실이 급하대요. $where" to
                (rest?.let { "아빠한테 말했어! ${it.second}킬로미터만 가면 휴게소야. 조금만 참자." } ?: "아빠한테 말했어! 쉴 곳을 찾아 줄 거야.")
            "hungry" -> "뒷자리에서 배고프대요. $where" to
                (rest?.let { "${it.first}에 가면 맛있는 게 있을 거야. 음식 칸도 채우자!" } ?: "아빠한테 말했어! 조금만 기다리자.")
            else -> {
                screenRestUntil = now + SCREEN_REST_MS
                main.postDelayed({ render() }, SCREEN_REST_MS + 100)
                "뒷자리 아이가 멀미가 난대요. 창문을 조금 열고 부드럽게 달려 주세요. $where" to
                    "창밖 멀리 있는 산을 보면서 크게 숨 쉬어 보자. 화면은 잠깐 쉴게."
            }
        }
        say(listOf(driver.trim(), kid))
        render()
    }

    /** 휴게소 도감에 도장을 찍는다. */
    private fun stampRest(name: String) {
        val count = dex.stamps.size
        if (name !in tripRests) tripRests += name // 일기에는 도장이 새것이든 아니든 적는다
        when (dex.visit(name)) {
            Dex.Visit.NEW -> say(listOf(line("rest", "$name 도장 쾅! 도감에 ${nth(count + 1)} 휴게소가 생겼어.", "rest" to name)))
            Dex.Visit.AGAIN -> say(listOf("$name 또 왔다! ${nth(dex.stamps.first { it.name == name }.visits)} 방문이야."))
            Dex.Visit.SAME_STOP -> return
        }
        flashMood(Mood.JOY)
        if (dexPanel.visibility == View.VISIBLE) renderDex()
    }

    /** 1 → "첫 번째", 2 → "두 번째" … 소리 내어 읽기 좋게. */
    private fun nth(n: Int) = when (n) {
        1 -> "첫 번째"; 2 -> "두 번째"; 3 -> "세 번째"; 4 -> "네 번째"; 5 -> "다섯 번째"
        else -> "${n}번째"
    }

    private fun say(lines: List<String>) {
        if (lines.isEmpty()) return
        lastSaid = lines.last()
        voice.say(lines)
    }

    // ---- 시연 ----

    private fun requestDemoRoute() {
        // 시연: 판교에서 전주한옥마을(큰집)까지
        signals.requestRoute(35.8150, 127.1530, "전주 큰집")
    }

    /** 실제 차 없이 시연할 때, 받은 안내 목록을 앞에서부터 하나씩 지나간 것처럼 넣는다. 차량 값은 실제 SDK(가상 콘솔)로 받는다. */
    private fun startReplay() {
        val g = game ?: return
        if (replaying) return
        replaying = true
        val route = g.route
        replayAt = g.passedIndex + 1
        val step = object : Runnable {
            override fun run() {
                if (!replaying) return
                if (resting) { main.postDelayed(this, stepMs); return }
                val k = ++replayAt
                if (k >= route.size) {
                    onArrived()
                    replaying = false
                    render()
                    return
                }
                feedTbt(route.drop(k))
                main.postDelayed(this, stepMs)
            }
        }
        render()
        main.postDelayed(step, stepMs)
    }

    /** 시연용: 시험 주행 중 다음 휴게소 바로 앞까지 건너뛴다. */
    private fun skipToRest() {
        val g = game ?: return
        val next = g.serviceAreas.firstOrNull { it > g.passedIndex } ?: return
        replayAt = next + 1
        feedTbt(g.route.drop(next + 1))
        resting = true // 휴게소에 들어왔으니 다시 D에 넣을 때까지 시험 주행을 멈춘다
    }

    // ---- 화면 ----

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 30, 44, 26)
        }
        title = text(38f, INK, bold = true).apply { text = "길동무 빙고" }
        subtitle = text(20f, SUB)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(PixelView(context).apply { set("car", CORAL_DEEP) }, LinearLayout.LayoutParams(56, 56).apply { rightMargin = 16 })
            addView(title)
        }
        progress = text(24f, Color.WHITE, bold = true).apply {
            setPadding(30, 14, 30, 14)
            background = pill(CORAL_DEEP)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val left = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleRow)
                addView(subtitle.apply { setPadding(4, 4, 0, 0) })
            }
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            addView(progress)
            headerFriend = PixelView(context).apply { setOnClickListener { toggleRoom() } }
            addView(headerFriend, LinearLayout.LayoutParams(80, 120).apply { leftMargin = 20 })
        }
        column.addView(header)

        grid = GridLayout(this).apply { useDefaultMargins = false; clipChildren = false; clipToPadding = false }
        empty = text(28f, SUB).apply {
            gravity = Gravity.CENTER
            text = "내비에 목적지를 넣으면\n이번 길로 빙고판을 만들어요"
        }
        val body = FrameLayout(this).apply { clipChildren = false }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        body.addView(empty, FrameLayout.LayoutParams(-1, -1))
        column.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 20 })

        buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("시연: 전주 큰집 가기", BLUE) { requestDemoRoute() })
            addView(button("시험 주행", GREEN) { startReplay() })
            addView(button("뒷좌석 화면 연결", CORAL_DEEP) { toggleRearPanel() })
            addView(button("가는 길 공부", VIOLET) { openStudyPicker() })
            addView(button("휴게소 도감", GREEN) { toggleDex() })
            addView(button("친구 바꾸기", PINK) { openPicker() })
        }
        column.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18 })
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        drivingCount = text(96f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER }
        val ring = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(CORAL_DEEP); setStroke(18, CORAL_SOFT) }
            addView(drivingCount, FrameLayout.LayoutParams(-1, -1))
        }
        drivingDots = GridLayout(this)
        drivingLast = text(30f, INK, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(40, 22, 40, 22)
            background = pill(Color.WHITE)
            elevation = 6f
        }
        driving = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            addView(text(26f, SUB).apply { gravity = Gravity.CENTER; text = "달리는 동안은 소리로 알려 드려요" })
            addView(ring, LinearLayout.LayoutParams(360, 360).apply { topMargin = 30; bottomMargin = 26; gravity = Gravity.CENTER })
            addView(drivingDots, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER; bottomMargin = 30 })
            drivingFriend = PixelView(context)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(drivingFriend, LinearLayout.LayoutParams(128, 192).apply { rightMargin = 18 })
                addView(drivingLast, LinearLayout.LayoutParams(-2, -2))
            }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })
            addView(text(20f, SUB).apply { gravity = Gravity.CENTER; text = "빙고판은 차를 세우면 보여요"; setPadding(0, 30, 0, 0) })
        }
        root.addView(driving, FrameLayout.LayoutParams(-1, -1))

        rearQr = ImageView(this)
        rearUrl = text(26f, INK, bold = true).apply { gravity = Gravity.CENTER }
        rearPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 50, 60, 50)
            background = GradientDrawable().apply { cornerRadius = 36f; setColor(Color.WHITE) }
            elevation = 24f
            visibility = View.GONE
            addView(text(32f, INK, bold = true).apply { gravity = Gravity.CENTER; text = "뒷좌석 화면 연결" })
            addView(text(20f, SUB).apply {
                gravity = Gravity.CENTER
                text = "아이 태블릿이나 휴대폰을 차와 같은 와이파이에 연결하고 QR을 찍으면, 달리는 동안에도 뒷좌석에서 빙고판이 보여요."
                setPadding(0, 10, 0, 24)
            })
            addView(rearQr, LinearLayout.LayoutParams(360, 360).apply { gravity = Gravity.CENTER })
            addView(rearUrl.apply { setPadding(0, 20, 0, 24) })
            addView(button("닫기", INK) { toggleRearPanel() }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
        }
        root.addView(rearPanel, FrameLayout.LayoutParams(820, -2, Gravity.CENTER))
        root.addView(buildDex(), FrameLayout.LayoutParams(-1, -1))
        root.addView(buildRoom(), FrameLayout.LayoutParams(-1, -1))
        root.addView(buildResult(), FrameLayout.LayoutParams(-1, -1))
        root.addView(buildStudyPicker(), FrameLayout.LayoutParams(-1, -1))
        root.addView(buildPicker(), FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun render() {
        val g = game
        subtitle.text = when {
            g != null -> "${g.routeName}까지 ${g.routeKm}km  ·  지나온 길 ${"%.0f".format(g.passedKm)}km" +
                (if (replaying) "  ·  시험 주행(시뮬레이션 입력)" else "")
            pendingRoute != null -> "경로를 받는 중…"
            else -> "가족이 함께 채우는 길 위의 빙고"
        }
        progress.text = g?.let { "${it.board.checkedCount}/${it.board.cells.size}칸  ·  빙고 ${it.board.lines()}줄" } ?: ""
        progress.visibility = if (g == null) View.GONE else View.VISIBLE
        empty.visibility = if (g == null) View.VISIBLE else View.GONE
        buttons.visibility = if (parked || replaying) View.VISIBLE else View.GONE

        grid.removeAllViews()
        drivingDots.removeAllViews()
        if (g != null) {
            val n = g.board.size
            grid.columnCount = n
            grid.rowCount = n
            drivingDots.columnCount = n
            g.board.cells.forEachIndexed { i, cell ->
                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(i / n, 1f), GridLayout.spec(i % n, 1f)
                ).apply { width = 0; height = 0; setMargins(9, 9, 9, 9) }
                grid.addView(cellView(cell), lp)
                drivingDots.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 10f
                        setColor(if (cell.checked) toneColor(cell.tone) else DOT_EMPTY)
                    }
                }, GridLayout.LayoutParams().apply { width = 40; height = 40; setMargins(6, 6, 6, 6) })
            }
        }

        val showDriving = !parked && g != null
        driving.visibility = if (showDriving) View.VISIBLE else View.GONE
        if (g != null) {
            drivingCount.text = "${g.board.checkedCount}"
            drivingLast.text = lastSaid.ifEmpty { "출발! 첫 칸을 기다리는 중" }
        }
        // 함께 달린 거리만큼 친구가 자란다.
        if (g != null && g.passedKm > grownKm) {
            val up = growth.addKm(g.passedKm - grownKm)
            grownKm = g.passedKm
            if (up != null) { flashMood(Mood.JOY); say(listOf(levelUpLine())) }
        }
        if (g != null && g.arrived && result == null) finishTrip(g)
        updateStudy(g)
        val lines = g?.board?.lines() ?: 0
        if (lines > lastLines) flashMood(Mood.JOY)
        lastLines = lines
        val f = friends.friend
        val friendColor = Color.parseColor(friends.color ?: "#FFFFFF")
        listOf(headerFriend, drivingFriend).forEach {
            it.visibility = if (f == null) View.GONE else View.VISIBLE
            if (f != null) it.setRows(Friends.rows(f, mood(), headRoom = true, hat = growth.level.hat), friendColor)
        }
        if (room.visibility == View.VISIBLE) renderRoom()
        resultPanel.visibility = if (result != null && parked && !resultClosed) View.VISIBLE else View.GONE
        rear.publish(stateJson())
    }

    // ---- 가는 길 공부 ----

    private lateinit var studyPanel: LinearLayout
    private lateinit var studyTiles: LinearLayout
    private lateinit var studyHint: TextView

    private fun buildStudyPicker(): View {
        studyTiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        studyHint = text(20f, SUB).apply { gravity = Gravity.CENTER; setPadding(0, 22, 0, 22) }
        studyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            visibility = View.GONE
            isClickable = true
            addView(text(38f, INK, bold = true).apply { gravity = Gravity.CENTER; text = "가는 길에 뭘 알아볼까?" })
            addView(text(20f, SUB).apply {
                gravity = Gravity.CENTER
                text = "보고 싶은 순서대로 눌러 주세요. 가는 동안 뒷좌석 화면에 도착지 이야기가 그 순서로 나와요."
                setPadding(0, 8, 0, 30)
            })
            addView(studyTiles)
            addView(studyHint)
            addView(text(15f, SUB).apply { gravity = Gravity.CENTER; text = "관광정보: 한국관광공사 TourAPI(공공누리)  ·  터널 지도: © OpenStreetMap contributors (ODbL)"; setPadding(0, 0, 0, 18) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(button("이 순서로 출발!", CORAL_DEEP) { closeStudyPicker() })
                addView(button("이번엔 안 할래요", SUB) { studyOrder.clear(); closeStudyPicker() })
            })
        }
        return studyPanel
    }

    private fun openStudyPicker() {
        studyPanel.visibility = View.VISIBLE
        renderStudyPicker()
    }

    private fun closeStudyPicker() {
        studyPanel.visibility = View.GONE
        if (studyOrder.isNotEmpty()) say(listOf("좋아! ${studyOrder.joinToString(", ") { it.label }} 순서로 뒷자리 화면에 보여 줄게."))
        render()
    }

    private fun renderStudyPicker() {
        studyTiles.removeAllViews()
        Topic.entries.forEach { t ->
            val n = studyOrder.indexOf(t) + 1
            val color = Color.parseColor(t.color)
            // 받아 온 뒤 내용이 없는 주제(예: 지금 열리는 축제가 없음)는 흐리게, 못 고르게
            val empty = studyCards.isNotEmpty() && studyCards[t].isNullOrEmpty()
            if (empty) studyOrder.remove(t)
            studyTiles.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 36f
                    setColor(if (n > 0) color else Color.WHITE)
                    setStroke(4, color)
                }
                elevation = if (n > 0) 10f else 4f
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(FrameLayout(context).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (n > 0) Color.argb(70, 255, 255, 255) else Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))) }
                        setPadding(26, 26, 26, 26)
                        addView(PixelView(context).apply { set(t.sprite, if (n > 0) android.graphics.Color.argb(255, (Color.red(color) + 255 * 2) / 3, (Color.green(color) + 255 * 2) / 3, (Color.blue(color) + 255 * 2) / 3) else color) }, FrameLayout.LayoutParams(-1, -1))
                    }, LinearLayout.LayoutParams(150, 150))
                    addView(text(34f, if (n > 0) Color.WHITE else INK, bold = true).apply { gravity = Gravity.CENTER; text = t.label; setPadding(0, 16, 0, 0) })
                    if (empty) addView(text(17f, SUB).apply { gravity = Gravity.CENTER; text = if (t == Topic.FESTIVAL) "지금 열리는 축제가 없어요" else "이번엔 없어요" })
                }, FrameLayout.LayoutParams(-1, -1))
                if (n > 0) addView(text(30f, color, bold = true).apply {
                    text = "$n"
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                }, FrameLayout.LayoutParams(64, 64, Gravity.TOP or Gravity.END).apply { topMargin = 14; rightMargin = 14 })
                alpha = if (empty) 0.45f else 1f
                if (!empty) setOnClickListener {
                    if (t in studyOrder) studyOrder.remove(t) else studyOrder += t
                    renderStudyPicker()
                }
            }, LinearLayout.LayoutParams(270, 300).apply { setMargins(16, 0, 16, 0) })
        }
        val dest = destName.ifBlank { game?.routeName ?: pendingRoute?.first ?: "도착지" }
        studyHint.text = when {
            studyOrder.isEmpty() -> "아무것도 안 고르면 빙고만 해요."
            !study.hasKey && studyCards.isEmpty() -> "고른 순서: ${studyOrder.joinToString(" → ") { it.label }}  (관광정보 키를 넣으면 $dest 이야기가 나와요)"
            else -> "고른 순서: ${studyOrder.joinToString(" → ") { it.label }}  ·  $dest 근처 이야기"
        }
    }

    /** 지나온 비율에 맞춰 카드를 바꾸고, 바뀌면 한 번 알려 준다. */
    private fun updateStudy(g: Game?) {
        val plan = StudyPlan(studyOrder, studyCards)
        val progress = if (g == null || g.routeKm <= 0) 0.0 else g.passedKm / g.routeKm
        val next = if (g == null || g.arrived) null else plan.at(progress)
        if (next != null && next.first != studyNow?.first) {
            val c = next.first
            if (c.title !in shownCards) shownCards += c.title
            // 퀴즈: 시연은 AI로 미리 만든 문제, 실서비스는 카드 설명으로 그때그때 만든다.
            quizNow = if (scripted) pack.quiz(c.title) else null
            quizChosen = null
            say(listOf(if (scripted) "${c.topic.label} 카드! ${c.title} 이야기야. ${c.text}"
                else "${c.topic.label} 카드가 왔어! 이번엔 ${c.title} 이야기야. 뒷자리 화면을 봐."))
        }
        studyNow = next
    }

    // ---- 도착: 최종 결과와 일기 ----

    /** 도착하면 이번 길을 정리해 일기를 쓰고 결과 화면을 띄운다. */
    private fun finishTrip(g: Game) {
        val f = friends.friend
        val minutes = if (replaying || tripStartMs == 0L) g.routeMin
        else ((System.currentTimeMillis() - tripStartMs) / 60000).toInt().coerceAtLeast(1)
        val foods = g.board.cells.filter { it.tone == Tone.FOOD && it.checked }.map { it.title.removeSuffix(" 사 먹기") }
        val trip = Trip(
            dest = g.routeName, friend = f?.name ?: "친구", board = g.board, checkedOrder = g.checkedOrder.toList(),
            km = g.routeKm, minutes = minutes, rests = tripRests.toList(), foods = foods,
            grewPoints = growth.points - tripPoints,
            rained = tripRain, tunnels = tripTunnels.toList(),
            newLevel = if (growth.levelIndex > tripLevel) growth.level.name else null,
            quizTotal = tripQuizTotal, quizRight = tripQuizRight,
        )
        val lines = if (scripted) pack.diary(scriptPrefs.getInt("diary", 0), diaryVars(trip)) else DiaryWriter.write(trip)
        result = diaries.save(trip, lines, ai = if (scripted) pack.diaryName(scriptPrefs.getInt("diary", 0)) else "")
            .put("rests", JSONArray(trip.rests)).put("foods", JSONArray(foods)).put("grew", trip.grewPoints)
        resultClosed = false
        say(listOf(line("arrive", "오늘의 일기가 완성됐어요. 차를 세우면 결과를 보여 줄게요.")))
        renderResult(trip, lines)
    }

    /** 대본 일기의 빈칸: 이번 길에서 실제로 일어난 일. */
    private fun diaryVars(t: Trip): Map<String, String> {
        val passed = t.checkedOrder.filter { it.trigger is Trigger.Pass }.map { it.title.removeSuffix(" 지나기") }
            .filter { it.endsWith("톨게이트") || it.endsWith("휴게소") }.take(2)
        return mapOf(
            "friend" to t.friend, "dest" to t.dest,
            "passed" to passed.joinToString(", "),
            "tunnel" to t.tunnels.firstOrNull { it.isNotBlank() }.orEmpty().ifEmpty { if (t.tunnels.isNotEmpty()) "터널" else "" },
            "rest" to t.rests.firstOrNull().orEmpty(),
            "food" to t.foods.firstOrNull().orEmpty(),
            "rain" to if (t.rained) "y" else "",
            "grandma" to t.checkedOrder.firstOrNull { it.title.startsWith("할머니") }?.hint.orEmpty(),
            "card" to shownCards.firstOrNull().orEmpty(),
            "lines" to "${t.board.lines()}", "checked" to "${t.board.checkedCount}", "total" to "${t.board.cells.size}",
            "level" to t.newLevel.orEmpty(),
            "quiz" to if (t.quizTotal > 0) "${t.quizTotal}" else "", "quizRight" to "${t.quizRight}",
        )
    }

    private fun buildResult(): View {
        resultHead = text(46f, CORAL_DEEP, bold = true).apply { text = "도착!" }
        resultSub = text(22f, SUB)
        resultBoard = GridLayout(this)
        resultStats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resultDate = text(22f, INK, bold = true)
        resultLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultFriend = PixelView(this)
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(resultHead)
            addView(resultSub.apply { setPadding(4, 0, 0, 20) })
            addView(resultBoard)
            addView(resultStats, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 22 })
        }
        // 공책처럼 줄 그은 일기장
        val notebook = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 30, 44, 30)
            background = GradientDrawable().apply { cornerRadius = 30f; setColor(Color.WHITE); setStroke(3, Color.parseColor("#F1E3CF")) }
            elevation = 8f
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(30f, INK, bold = true).apply { text = "오늘의 일기" })
                    addView(resultDate.apply { setTextColor(SUB); textSize = 18f; typeface = Typeface.DEFAULT })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(resultFriend, LinearLayout.LayoutParams(96, 144))
            })
            addView(resultLines, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        }
        resultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(56, 34, 56, 26)
            visibility = View.GONE
            isClickable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(left, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 44 })
                addView(notebook, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                addView(button("닫기", INK) { resultClosed = true; render() })
            })
        }
        return resultPanel
    }

    private fun renderResult(t: Trip, lines: List<String>) {
        resultSub.text = "${t.dest} 가는 길  ·  ${t.friend}${DiaryWriter.josa(t.friend, "이랑", "랑")} 함께"
        val n = t.board.size
        resultBoard.removeAllViews()
        resultBoard.columnCount = n
        val cellPx = if (n == 4) 112 else 148
        t.board.cells.forEach { c ->
            resultBoard.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(if (c.checked) toneColor(c.tone) else Color.WHITE)
                    if (!c.checked) setStroke(3, DOT_EMPTY)
                }
                setPadding(22, 22, 22, 22)
                alpha = if (c.checked) 1f else 0.6f
                addView(PixelView(context).apply { set(c.sprite, if (c.checked) toneSoft(c.tone) else DOT_EMPTY) }, FrameLayout.LayoutParams(-1, -1))
            }, GridLayout.LayoutParams().apply { width = cellPx; height = cellPx; setMargins(6, 6, 6, 6) })
        }
        resultStats.removeAllViews()
        listOf(
            "빙고 ${t.board.lines()}줄" to CORAL_DEEP,
            "${t.board.checkedCount}/${t.board.cells.size}칸" to BLUE,
            "${t.km}km" to GREEN,
            (if (t.minutes >= 60) "${t.minutes / 60}시간 ${t.minutes % 60}분" else "${t.minutes}분") to VIOLET,
        ).plus(if (t.quizTotal > 0) listOf("퀴즈 ${t.quizRight}/${t.quizTotal}" to ORANGE) else emptyList()).forEach { (label, color) ->
            resultStats.addView(text(20f, Color.WHITE, bold = true).apply {
                text = label
                setPadding(22, 10, 22, 10)
                background = pill(color)
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 10 })
        }
        resultDate.text = java.text.SimpleDateFormat("yyyy년 M월 d일 EEEE", java.util.Locale.KOREA).format(java.util.Date()) +
            if (scripted) "  ·  AI가 쓴 일기 (${pack.diaryName(scriptPrefs.getInt("diary", 0))})" else ""
        friends.friend?.let { resultFriend.setRows(Friends.rows(it, Mood.JOY, headRoom = true, hat = growth.level.hat), Color.parseColor(friends.color ?: "#FFFFFF")) }
        resultLines.removeAllViews()
        lines.forEach { line ->
            resultLines.addView(text(22f, INK).apply {
                text = line
                setPadding(4, 12, 4, 12)
                setLineSpacing(0f, 1.1f)
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            })
            resultLines.addView(View(this).apply { setBackgroundColor(Color.parseColor("#F1E3CF")) }, LinearLayout.LayoutParams(-1, 2))
        }
    }

    // ---- 친구 방(키우기) ----

    private fun levelUpLine(): String {
        val name = friends.friend?.name ?: "친구"
        return when (growth.level.hat) {
            "sprout" -> "와! ${name} 머리에 새싹이 났어. 이제 새싹 ${name}야!"
            "star" -> "${name}가 반짝별이 됐어! 머리에 별이 떴어."
            "cap" -> "${name}가 탐험가 모자를 썼어! 어디든 같이 가자."
            "crown" -> "${name}가 길동무 대장이 됐어! 왕관 받았다!"
            else -> "${name}가 한 뼘 자랐어!"
        }
    }

    private fun buildRoom(): View {
        roomFriend = PixelView(this)
        roomTitle = text(36f, INK, bold = true)
        roomNext = text(20f, SUB).apply { setPadding(0, 6, 0, 14) }
        roomBar = View(this).apply { background = pill(CORAL_DEEP) }
        roomBarBox = FrameLayout(this).apply {
            background = pill(DOT_EMPTY)
            addView(roomBar, FrameLayout.LayoutParams(0, -1))
        }
        roomStats = text(20f, INK).apply { setPadding(0, 18, 0, 0) }
        roomCalendar = GridLayout(this).apply { columnCount = 7 }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(roomTitle)
            addView(roomNext)
            addView(roomBarBox, LinearLayout.LayoutParams(620, 30))
            addView(roomStats)
            addView(text(24f, INK, bold = true).apply { text = "이번 달 출석"; setPadding(0, 28, 0, 10) })
            addView(roomCalendar)
        }
        room = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(60, 40, 60, 30)
            visibility = View.GONE
            isClickable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                addView(button("닫기", INK) { toggleRoom() })
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(roomFriend, LinearLayout.LayoutParams(320, 480).apply { rightMargin = 70 })
                addView(info)
            }, LinearLayout.LayoutParams(-2, 0, 1f).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        return room
    }

    private fun toggleRoom() {
        room.visibility = if (room.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (room.visibility == View.VISIBLE) renderRoom()
    }

    private fun renderRoom() {
        val f = friends.friend ?: return
        roomFriend.setRows(Friends.rows(f, mood(), headRoom = true, hat = growth.level.hat), Color.parseColor(friends.color ?: "#FFFFFF"))
        roomTitle.text = "Lv.${growth.levelIndex + 1} ${growth.level.name} ${f.name}"
        val next = growth.next
        val from = growth.level.points
        roomNext.text = if (next == null) "가장 높은 단계예요!" else "${next.name}까지 ${next.points - growth.points}걸음 남았어요"
        val ratio = if (next == null) 1f else (growth.points - from).toFloat() / (next.points - from)
        roomBar.layoutParams = (roomBar.layoutParams as FrameLayout.LayoutParams).apply { width = (620 * ratio.coerceIn(0.03f, 1f)).toInt() }
        roomStats.text = "함께 달린 길 ${"%.0f".format(growth.km)}km  ·  출석 ${growth.days.size}일  ·  휴게소 도장 ${dex.stamps.size}곳\n" +
            "1km = 1걸음, 출석 하루 = ${Growth.DAY_BONUS}걸음"
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val last = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val attended = growth.daysThisMonth()
        roomCalendar.removeAllViews()
        for (day in 1..last) {
            val on = day in attended
            roomCalendar.addView(text(16f, if (on) Color.WHITE else if (day > today) DOT_EMPTY else SUB, bold = on).apply {
                text = "$day"
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (on) setColor(STAMP) else setStroke(2, DOT_EMPTY)
                }
            }, GridLayout.LayoutParams().apply { width = 64; height = 64; setMargins(6, 6, 6, 6) })
        }
    }

    // ---- 휴게소 도감 ----

    private fun buildDex(): View {
        dexTitle = text(34f, INK, bold = true)
        dexGrid = GridLayout(this).apply { columnCount = 4 }
        dexPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(60, 40, 60, 30)
            visibility = View.GONE
            isClickable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(PixelView(context).apply { set("rest", STAMP) }, LinearLayout.LayoutParams(60, 60).apply { rightMargin = 16 })
                addView(dexTitle, LinearLayout.LayoutParams(0, -2, 1f))
                addView(button("닫기", INK) { toggleDex() })
            })
            addView(text(19f, SUB).apply {
                text = "휴게소에 차를 세우고 문을 열면 도장을 찍어요. 거기서 먹은 음식도 같이 남아요."
                setPadding(0, 6, 0, 24)
            })
            addView(android.widget.ScrollView(context).apply { addView(dexGrid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return dexPanel
    }

    private fun toggleDex() {
        dexPanel.visibility = if (dexPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (dexPanel.visibility == View.VISIBLE) renderDex()
    }

    private fun renderDex() {
        dexTitle.text = "휴게소 도감  ·  ${dex.stamps.size}곳"
        dexGrid.removeAllViews()
        if (dex.stamps.isEmpty()) {
            dexGrid.addView(text(24f, SUB).apply { text = "아직 도장이 없어요. 첫 휴게소에서 쾅!" })
            return
        }
        val stamps = dex.stamps.sortedByDescending { it.lastAt }
        stamps.forEach { dexGrid.addView(stampView(it), GridLayout.LayoutParams().apply { width = 360; setMargins(12, 12, 12, 12) }) }
        // 다음에 채울 빈 칸: 4칸 단위로, 적어도 8칸까지
        repeat(maxOf(8, (stamps.size / 4 + 1) * 4) - stamps.size) {
            dexGrid.addView(emptyStamp(), GridLayout.LayoutParams().apply { width = 360; setMargins(12, 12, 12, 12) })
        }
    }

    private fun emptyStamp() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(20, 24, 20, 20)
        background = GradientDrawable().apply { cornerRadius = 30f; setColor(Color.argb(120, 255, 255, 255)); setStroke(3, DOT_EMPTY, 14f, 10f) }
        addView(text(56f, DOT_EMPTY, bold = true).apply {
            text = "?"
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(5, DOT_EMPTY, 12f, 9f) }
        }, LinearLayout.LayoutParams(150, 150))
        addView(text(18f, SUB).apply { text = "다음 휴게소"; gravity = Gravity.CENTER; setPadding(0, 14, 0, 2) })
    }

    /** 도장 하나: 빨간 테 동그라미 안에 휴게소 그림, 아래에 이름·방문·먹은 음식. */
    private fun stampView(s: Stamp) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(20, 24, 20, 20)
        background = GradientDrawable().apply { cornerRadius = 30f; setColor(Color.WHITE) }
        elevation = 4f
        val ring = FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(STAMP_SOFT); setStroke(7, STAMP) }
            rotation = -8f
            setPadding(30, 30, 30, 30)
            addView(PixelView(context).apply { set("rest", STAMP) }, FrameLayout.LayoutParams(-1, -1))
        }
        addView(ring, LinearLayout.LayoutParams(150, 150))
        addView(text(24f, INK, bold = true).apply { text = s.name; gravity = Gravity.CENTER; setPadding(0, 14, 0, 2) })
        addView(text(16f, SUB).apply {
            text = (if (s.visits > 1) "${s.visits}번 방문  ·  " else "") + "처음 ${s.first}"
            gravity = Gravity.CENTER
        })
        if (s.foods.isNotEmpty()) addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
            s.foods.forEach { f -> addView(PixelView(context).apply { set(f, PINK) }, LinearLayout.LayoutParams(44, 44).apply { setMargins(4, 0, 4, 0) }) }
        })
    }

    // ---- 친구 고르기 ----

    private fun buildPicker(): View {
        pickerCards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        pickerColors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        pickerOk = button("", CORAL_DEEP) { confirmPick() }.apply { textSize = 26f; setPadding(64, 22, 64, 22) }
        picker = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            visibility = View.GONE
            isClickable = true
            addView(text(38f, INK, bold = true).apply { gravity = Gravity.CENTER; text = "같이 갈 친구를 골라 줘" })
            addView(text(20f, SUB).apply {
                gravity = Gravity.CENTER
                text = "고른 친구는 차에 탈 때마다 깨어나서 인사해요. 고른 색으로 차 무드등도 바뀌어요."
                setPadding(0, 8, 0, 30)
            })
            addView(pickerCards)
            addView(pickerColors, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER; topMargin = 26; bottomMargin = 26 })
            addView(pickerOk, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })
        }
        return picker
    }

    private fun openPicker() {
        val f = friends.friend ?: Friends.ALL.first()
        pickFriend = f
        pickColor = friends.color ?: f.colors.first()
        picker.visibility = View.VISIBLE
        renderPicker()
    }

    private fun renderPicker() {
        val chosen = pickFriend ?: return
        pickerCards.removeAllViews()
        Friends.ALL.forEach { f ->
            val on = f == chosen
            val body = if (on) pickColor else f.colors.first()
            pickerCards.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(30, 26, 30, 22)
                background = GradientDrawable().apply {
                    cornerRadius = 36f
                    setColor(Color.WHITE)
                    setStroke(if (on) 8 else 3, if (on) CORAL_DEEP else DOT_EMPTY)
                }
                elevation = if (on) 10f else 4f
                addView(PixelView(context).apply { setRows(Friends.rows(f), Color.parseColor(body)) }, LinearLayout.LayoutParams(192, 216))
                addView(text(30f, INK, bold = true).apply { gravity = Gravity.CENTER; text = f.name; setPadding(0, 12, 0, 0) })
                addView(text(18f, SUB).apply { gravity = Gravity.CENTER; text = f.kind })
                setOnClickListener {
                    if (pickFriend != f) { pickFriend = f; pickColor = f.colors.first() }
                    renderPicker()
                }
            }, LinearLayout.LayoutParams(300, -2).apply { setMargins(18, 0, 18, 0) })
        }
        pickerColors.removeAllViews()
        chosen.colors.forEach { col ->
            val on = col == pickColor
            pickerColors.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(col))
                    setStroke(if (on) 8 else 3, if (on) INK else DOT_EMPTY)
                }
                setOnClickListener { pickColor = col; renderPicker() }
            }, LinearLayout.LayoutParams(72, 72).apply { setMargins(14, 0, 14, 0) })
        }
        pickerOk.text = "${chosen.name}랑 갈래!"
    }

    private fun confirmPick() {
        val f = pickFriend ?: return
        friends.save(f, pickColor)
        picker.visibility = View.GONE
        flashMood(Mood.JOY)
        signals.setAmbient(Color.parseColor(pickColor))
        say(listOf("안녕, 나는 ${f.name}! 오늘부터 같이 다니자."))
        render()
    }

    private fun tapCell(i: Int) {
        val g = game ?: return
        val cell = g.board.cells.getOrNull(i) ?: return
        if (cell.tone == Tone.FOOD && !cell.checked) {
            flashMood(Mood.YUM)
            dex.ate(cell.sprite)
            if (scripted) { say(listOfNotNull(pack.line("food", mapOf("food" to cell.title.removeSuffix(" 사 먹기")))) + g.onTap(cell).drop(1)); render(); return }
        }
        say(g.onTap(cell))
        render()
    }

    /** 뒷좌석 화면(rear.html)이 그리는 데 쓰는 지금 상태. */
    private fun stateJson(): String {
        val o = JSONObject()
        o.put("palette", JSONObject().apply { Sprites.PALETTE.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("logo", JSONArray(Smooth.rows("car"))).put("last", lastSaid)
        o.put("dex", dex.json())
        result?.let { o.put("result", it) }
        studyNow?.let { (c, step) ->
            o.put("study", c.json().put("label", c.topic.label).put("color", c.topic.color).put("step", step)
                .put("sprite", JSONArray(Smooth.rows(c.topic.sprite)))
                .apply {
                    quizNow?.let { q ->
                        put("quiz", JSONObject().put("q", q.question).put("o", JSONArray(q.options))
                            .put("chosen", quizChosen ?: -1).put("a", if (quizChosen != null) q.answer else -1))
                    }
                })
        }
        o.put("screenRest", (screenRestUntil - System.currentTimeMillis()).coerceAtLeast(0) / 1000)
        o.put("diaries", JSONArray().apply {
            val all = diaries.all()
            for (i in all.length() - 1 downTo maxOf(0, all.length() - 20)) put(all.getJSONObject(i))
        })
        o.put("dexSprites", JSONObject().apply {
            (listOf("rest") + dex.stamps.flatMap { it.foods }).distinct().forEach { put(it, JSONArray(Smooth.rows(it))) }
        })
        friends.friend?.let { f ->
            o.put("friend", JSONObject().put("name", f.name).put("color", friends.color).put("rows", JSONArray(Friends.rows(f, mood(), headRoom = true, hat = growth.level.hat)))
                .put("level", growth.level.name).put("toNext", growth.next?.let { it.points - growth.points } ?: -1))
        }
        val g = game ?: return o.toString()
        o.put("route", g.routeName).put("km", g.routeKm).put("passedKm", g.passedKm.toInt())
        o.put("checked", g.board.checkedCount).put("total", g.board.cells.size).put("lines", g.board.lines())
        o.put("size", g.board.size).put("last", lastSaid)
        o.put("cells", JSONArray().apply {
            g.board.cells.forEach { c ->
                put(JSONObject()
                    .put("title", c.title).put("hint", c.hint).put("checked", c.checked)
                    .put("tap", c.trigger == Trigger.Tap)
                    .put("color", hex(toneColor(c.tone))).put("soft", hex(toneSoft(c.tone)))
                    .put("sprite", JSONArray(Smooth.rows(c.sprite))))
            }
        })
        return o.toString()
    }

    private fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    private fun toggleRearPanel() {
        if (rearPanel.visibility == View.VISIBLE) { rearPanel.visibility = View.GONE; return }
        val url = "http://${RearServer.address() ?: "192.168.0.10"}:${RearServer.PORT}"
        rearUrl.text = url
        rearQr.setImageBitmap(qr(url, 360))
        rearPanel.visibility = View.VISIBLE
    }

    private fun qr(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val px = IntArray(size * size) { if (m.get(it % size, it / size)) INK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun cellView(cell: Cell): View {
        val tone = toneColor(cell.tone)
        val box = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 26f
                setColor(if (cell.checked) tone else Color.WHITE)
                if (!cell.checked) setStroke(3, toneSoft(cell.tone))
            }
            elevation = if (cell.checked) 2f else 5f
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(14, 10, 14, 10)
        }
        val badge = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (cell.checked) Color.argb(70, 255, 255, 255) else toneSoft(cell.tone))
            }
            setPadding(12, 12, 12, 12)
            addView(PixelView(context).apply {
                set(cell.sprite, if (cell.checked) toneSoft(cell.tone) else tone)
            }, FrameLayout.LayoutParams(-1, -1))
        }
        inner.addView(badge, LinearLayout.LayoutParams(84, 84).apply { bottomMargin = 8 })
        inner.addView(text(24f, if (cell.checked) Color.WHITE else INK, bold = true).apply {
            gravity = Gravity.CENTER; text = cell.title; maxLines = 1
        })
        inner.addView(text(16f, if (cell.checked) Color.argb(230, 255, 255, 255) else SUB).apply {
            gravity = Gravity.CENTER
            text = if (cell.checked) "채웠어요!" else cell.hint
            maxLines = 1
            setPadding(0, 4, 0, 0)
        })
        box.addView(inner, FrameLayout.LayoutParams(-1, -1))
        if (cell.checked) {
            box.addView(text(26f, tone, bold = true).apply {
                text = "✓"
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            }, FrameLayout.LayoutParams(48, 48, Gravity.TOP or Gravity.END).apply { topMargin = 12; rightMargin = 12 })
        }
        if (cell.trigger == Trigger.Tap) box.setOnClickListener {
            game?.let { tapCell(it.board.cells.indexOf(cell)) }
        }
        return box
    }

    private fun toneColor(t: Tone) = when (t) {
        Tone.ROAD -> BLUE; Tone.REST -> GREEN; Tone.TOLL -> VIOLET; Tone.SKY -> SKY
        Tone.FAMILY -> CORAL_DEEP; Tone.FUN -> ORANGE; Tone.FOOD -> PINK; Tone.GOAL -> GOLD
    }

    private fun toneSoft(t: Tone) = when (t) {
        Tone.ROAD -> Color.parseColor("#E6EEFF"); Tone.REST -> Color.parseColor("#E1F6EA")
        Tone.TOLL -> Color.parseColor("#EEEAFF"); Tone.SKY -> Color.parseColor("#DFF4FC")
        Tone.FAMILY -> Color.parseColor("#FFE6E1"); Tone.FUN -> Color.parseColor("#FFF0DC")
        Tone.FOOD -> Color.parseColor("#FFE3EE")
        Tone.GOAL -> Color.parseColor("#FFF4CC")
    }

    private fun pill(color: Int) = GradientDrawable().apply { cornerRadius = 999f; setColor(color) }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 21f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        stateListAnimator = null
        background = pill(color)
        setPadding(40, 16, 40, 16)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 16 }
    }

    override fun onDestroy() {
        replaying = false
        signals.release()
        rear.stop()
        voice.release()
        super.onDestroy()
    }

    companion object {
        const val TAG = "GILDONGMU"
        const val STEP_MS = 3000L
        const val FLASH_MS = 6000L
        const val ASK_GAP_MS = 60_000L
        const val SCREEN_REST_MS = 120_000L
        val BG = Color.parseColor("#FFF8EF")
        val INK = Color.parseColor("#1F2433")
        val SUB = Color.parseColor("#7A7F8C")
        val BLUE = Color.parseColor("#3D7BFF")
        val GREEN = Color.parseColor("#1DB271")
        val VIOLET = Color.parseColor("#7B61FF")
        val SKY = Color.parseColor("#12A5DB")
        val CORAL_DEEP = Color.parseColor("#FF6B57")
        val CORAL_SOFT = Color.parseColor("#FFD3CB")
        val ORANGE = Color.parseColor("#FF9A1F")
        val GOLD = Color.parseColor("#F2B705")
        val PINK = Color.parseColor("#F0508C")
        val DOT_EMPTY = Color.parseColor("#ECE4D8")
        val STAMP = Color.parseColor("#E5484D")
        val STAMP_SOFT = Color.parseColor("#FFF0EE")
    }
}
