package com.roadpractice.coach

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs

/**
 * 차량 신호를 받아 VehicleState 하나로 모읍니다. 차량 제어(set)는 호출하지 않습니다.
 * - 같은 값 재전송을 거릅니다(조향각은 약 3초마다 같은 값이 옵니다).
 * - 거리 항목(30미터 전 신호 등)을 위해 속도를 시간으로 적분합니다.
 * 호출과 콜백은 메인 스레드에서 처리합니다.
 */
class SignalHub(private val context: Context, private val onChange: (VehicleState) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var fused: FusedLocation? = null
    private var lastSpeedAt = 0L
    private var running = false
    var state = VehicleState()
        private set

    fun start() {
        if (running) return
        running = true
        val v = Vehicle(context).also { vehicle = it }
        v.initialize()
        // initialize 직후에는 호출이 실패할 수 있어 잠시 기다립니다(실측).
        main.postDelayed({ if (running) subscribeVehicle(v) }, VEHICLE_READY_DELAY_MS)
        startSpeed()
        startLocation()
        main.post(tick)
        Log.i(TAG, "SIGNALS_STARTED")
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        vehicle?.let { v ->
            guard("stop") {
                v.getGear().unregisterGearSelection(gearListener)
                v.getTurnSignal().unregisterTurnSignalState(turnListener)
                v.getSteeringWheel().unregisterSteeringWheelAngle(steerListener)
                v.getBrake().unregisterBrakePedalStatus(brakeListener)
                v.getSafety().unregisterTelltales(telltaleListener)
                doorListeners.forEach { (area, l) -> v.getDoor().unregisterDoorPosition(area, l) }
            }
        }
        guard("stop car") { car?.disconnect() }
        guard("stop location") { fused?.unregisterFusedLocationCallback(locationListener); fused?.release() }
        vehicle = null
        car = null
        fused = null
        Log.i(TAG, "SIGNALS_STOPPED")
    }

    private fun update(change: (VehicleState) -> VehicleState) {
        val next = change(state)
        if (next == state) return
        state = next.copy(timeMs = SystemClock.elapsedRealtime())
        onChange(state)
    }

    /** 10초 정차처럼 값이 안 바뀌어도 시간이 지나야 하는 판정을 위해 시각을 올립니다. */
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            update { it.copy(timeMs = SystemClock.elapsedRealtime()) }
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun subscribeVehicle(v: Vehicle) {
        guard("gear") { v.getGear().registerGearSelection(gearListener) }
        guard("turn") { v.getTurnSignal().registerTurnSignalState(turnListener) }
        guard("steer") { v.getSteeringWheel().registerSteeringWheelAngle(steerListener) }
        guard("brake") { v.getBrake().registerBrakePedalStatus(brakeListener) }
        guard("telltale") { v.getSafety().registerTelltales(telltaleListener) }
        doorListeners.forEach { (area, l) -> guard("door") { v.getDoor().registerDoorPosition(area, l) } }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { if (running) block() }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onMain { update { it.copy(gear = mapGear(gear)) } }
        override fun onFailed(e: Exception) = failed("gear", e)
    }

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = onMain { update { it.copy(turnSignal = mapSignal(value)) } }
        override fun onFailed(e: Exception) = failed("turn", e)
    }

    private val steerListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(value: Float?) = onMain { update { it.copy(steeringAbsDeg = value?.let(::abs)) } }
        override fun onFailed(e: Exception) = failed("steer", e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) = onMain { update { it.copy(braking = value) } }
        override fun onFailed(e: Exception) = failed("brake", e)
    }

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(info: Telltales?) = onMain {
            val on = info?.telltales.orEmpty().filter { it.state == TelltaleState.ON }.map { it.type.name }
            update {
                it.copy(
                    parkingBrakeOn = info?.let { _ -> on.any { n -> n == "PARKING_BRAKE" || n == "ELECTRONIC_PARKING_BRAKE" } },
                    seatBeltWarningOn = info?.let { _ -> "SEAT_BELT" in on },
                )
            }
        }
        override fun onFailed(e: Exception) = failed("telltale", e)
    }

    private val doorListeners: List<Pair<DoorArea, DoorPositionListener>> =
        listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT).map { door ->
            DoorArea(door) to object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) = onMain {
                    update {
                        val open = (pos ?: 0) > 0
                        it.copy(openDoors = if (open) it.openDoors + door.name else it.openDoors - door.name)
                    }
                }
                override fun onFailed(e: Exception) = failed("door ${door.name}", e)
            }
        }

    private fun startSpeed() = guard("speed") {
        val c = Car.createCar(context).also { car = it }
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        pm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val kmh = ((value.value as? Float) ?: return) * MPS_TO_KMH
                onMain { onSpeed(kmh) }
            }
            override fun onErrorEvent(propertyId: Int, zone: Int) {
                Log.w(TAG, "speed error")
            }
        }, VehiclePropertyIds.PERF_VEHICLE_SPEED, SPEED_RATE_HZ)
    }

    private fun onSpeed(kmh: Float) {
        val now = SystemClock.elapsedRealtime()
        update { s ->
            val before = s.speedKmh ?: kmh
            val added = if (lastSpeedAt > 0) distanceMeters(before, kmh, now - lastSpeedAt) else 0.0
            s.copy(speedKmh = kmh, distanceM = s.distanceM + added)
        }
        lastSpeedAt = now
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val l = location ?: return
            onMain { update { it.copy(latitude = l.latitude, longitude = l.longitude) } }
        }
        override fun onError(exception: Exception) {
            Log.w(TAG, "location error: ${exception.message}")
        }
    }

    private fun startLocation() = guard("location") {
        val f = FusedLocation(context).also { fused = it }
        f.initialize()
        f.registerFusedLocationCallback(locationListener)
    }

    private fun failed(what: String, e: Exception) {
        Log.w(TAG, "listener failed $what: ${e.javaClass.simpleName} ${e.message}")
    }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Exception) {
            Log.w(TAG, "$what failed: ${t.javaClass.simpleName} ${t.message}")
        }
    }

    companion object {
        private const val TAG = "RoadCoach"
        private const val VEHICLE_READY_DELAY_MS = 1500L
        private const val MPS_TO_KMH = 3.6f
        private const val SPEED_RATE_HZ = 10f
        private const val TICK_MS = 200L

        fun mapGear(g: VehicleGear?): Gear = when (g) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            VehicleGear.GEAR_DRIVE -> Gear.DRIVE
            else -> Gear.UNKNOWN
        }

        fun mapSignal(s: TurnSignalValue?): TurnSignal = when (s) {
            TurnSignalValue.OFF -> TurnSignal.OFF
            TurnSignalValue.LEFT -> TurnSignal.LEFT
            TurnSignalValue.RIGHT -> TurnSignal.RIGHT
            else -> TurnSignal.UNKNOWN
        }

        /** 두 속도 표본 사이의 거리(사다리꼴). 음수 시간은 0입니다. */
        fun distanceMeters(fromKmh: Float, toKmh: Float, dtMs: Long): Double {
            if (dtMs <= 0) return 0.0
            return (fromKmh + toKmh) / 2.0 / MPS_TO_KMH * dtMs / 1000.0
        }
    }
}
