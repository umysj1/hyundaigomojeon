package kr.gildongmu.bingo

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 앱이 받는 신호. 모든 콜백은 메인 스레드에서 온다. */
interface SignalListener {
    fun onRouteStarted(name: String, km: Int, min: Int)
    fun onTbt(list: List<TBTInfo>)
    fun onArrived()
    fun onGear(parked: Boolean)
    fun onWiper(on: Boolean)
    fun onLowBeam(on: Boolean)
    fun onDoor(anyOpen: Boolean)
    fun onBlink()
    fun onRearSeat(occupied: Boolean)
    fun onRearBelt(buckled: Boolean)
    fun onLocation(lat: Double, lon: Double)
    fun onDestination(lat: Double, lon: Double, name: String)
}

/** Pleos Vehicle·NaviHelper 신호를 모아 [SignalListener]로 넘긴다. */
class CarSignals(context: Context, private val out: SignalListener) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(app)
    private val navi = NaviHelper(app)
    private val openDoors = mutableSetOf<DoorValue>()
    private val rearSeats = mutableMapOf<SeatValue, Boolean>()
    private val rearBelts = mutableMapOf<SeatValue, Boolean>()
    private var lastTurn: TurnSignalValue? = null
    private val seatSettled = Runnable { out.onRearSeat(rearSeats.values.any { it }) }

    fun start() {
        safe("vehicle") { vehicle.initialize() }
        safe("gear") {
            vehicle.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) = post { out.onGear(gear == VehicleGear.GEAR_PARK) }
                override fun onFailed(e: Exception) = log(e)
            })
        }
        safe("wiper") {
            vehicle.getWiper().registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), object : WiperStateListener {
                override fun onWiperStateUpdated(state: WiperState?) = post { out.onWiper(state == WiperState.ON) }
                override fun onFailed(e: Exception) = log(e)
            })
        }
        safe("lowbeam") {
            vehicle.getLight().registerLowBeamLightState(object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) = post { out.onLowBeam(state == LightState.ON) }
                override fun onFailed(e: Exception) = log(e)
            })
        }
        listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT).forEach { door ->
            safe("door $door") {
                vehicle.getDoor().registerDoorPosition(DoorArea(door), object : DoorPositionListener {
                    override fun onPositionUpdated(position: Int?) = post {
                        if ((position ?: 0) > 0) openDoors += door else openDoors -= door
                        out.onDoor(openDoors.isNotEmpty())
                    }
                    override fun onFailed(e: Exception) = log(e)
                })
            }
        }
        safe("turn") {
            vehicle.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = post {
                    val on = state == TurnSignalValue.LEFT || state == TurnSignalValue.RIGHT
                    if (on && lastTurn != state) out.onBlink()
                    lastTurn = state
                }
                override fun onFailed(e: Exception) = log(e)
            })
        }
        listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).forEach { seat ->
            safe("seat $seat") {
                vehicle.getSeat().registerSeatOccupancy(SeatArea(seat), object : SeatOccupancyListener {
                    // Vehicle 2.3.2는 앉으면 false를 준다(에뮬레이터에서 확인). 그래서 false를 착석으로 읽는다.
                    override fun onSeatOccupancyUpdated(occupancy: Boolean?) = post {
                        rearSeats[seat] = occupancy == false
                        // 등록 직후 한 번 '앉음'이 잘못 오고 곧바로 실제 값이 온다(에뮬레이터 확인). 1초 그대로일 때만 넘긴다.
                        main.removeCallbacks(seatSettled)
                        main.postDelayed(seatSettled, 1000)
                    }
                    override fun onFailed(e: Exception) = log(e)
                })
            }
        }
        listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).forEach { seat ->
            safe("belt $seat") {
                vehicle.getSeat().registerSeatBeltBuckledState(SeatArea(seat), object : SeatBeltBuckledStateListener {
                    override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) = post {
                        rearBelts[seat] = buckled == true
                        out.onRearBelt(rearBelts.values.any { it })
                    }
                    override fun onFailed(e: Exception) = log(e)
                })
            }
        }
        safe("navi") {
            navi.initialize({ navi.addListener(naviListener); navi.getTBTInfo(); main.post(pollLocation) }, { log(it) })
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = post {
            out.onRouteStarted(info.name, (info.distance / 1000).toInt(), (info.duration / 60).toInt())
        }
        override fun onTBTInfo(list: List<TBTInfo>) = post { out.onTbt(list) }
        override fun onDestinationArrived(info: DestinationArrivedInfo) = post { out.onArrived() }
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = post { out.onLocation(info.latitude, info.longitude) }
        override fun onDestinationInfo(info: DestinationInfo) = post { out.onDestination(info.latitude, info.longitude, info.name) }
    }

    fun requestRoute(lat: Double, lon: Double, name: String) = safe("route") {
        navi.requestRoute(
            RouteInfo(
                latitude = lat, longitude = lon, displayLatitude = null, displayLongitude = null,
                poiName = name, address = "", poiId = "", poiSubId = "",
                routeOption = RouteOption.RECOMMENDED, poiProvider = "", isMobileRequest = false,
            )
        )
    }

    /** 무드등을 친구 색으로. 차가 지원하는 모든 구역에 같은 색을 넣는다. */
    fun setAmbient(rgb: Int) = safe("ambient") {
        val light = vehicle.getLight()
        light.checkAmbientLightBrightnessColorCapabilities({ _, areas ->
            Log.i(TAG, "ambient areas $areas")
            areas.forEach { area ->
                light.setAmbientLightBrightnessColor(area, rgb and 0xFFFFFF,
                    { a, v -> Log.i(TAG, "ambient $a ${"%06X".format(v)}") }, { log(it) })
            }
        }, { log(it) })
    }

    /** 터널 판정용 현재 위치: 2초마다 묻는다(답은 onCurrentLocationInfo로 온다). */
    private val pollLocation = object : Runnable {
        override fun run() {
            safe("location") { navi.getCurrentLocationInfo() }
            main.postDelayed(this, LOCATION_MS)
        }
    }

    fun requestDestination() = safe("destination") { navi.getDestinationInfo() }

    fun refreshTbt() = safe("tbt") { navi.getTBTInfo() }

    fun release() = safe("release") { main.removeCallbacks(pollLocation); vehicle.release(); navi.release() }

    private fun post(block: () -> Unit) { main.post(block) }
    private inline fun safe(what: String, block: () -> Unit) = runCatching(block).onFailure { Log.w(TAG, "$what: ${it.message}") }
    private fun log(e: Throwable) { Log.w(TAG, "signal: ${e.message}") }

    companion object {
        const val TAG = "GILDONGMU"
        const val LOCATION_MS = 2000L
    }
}
