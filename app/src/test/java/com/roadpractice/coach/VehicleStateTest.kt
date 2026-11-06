package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateTest {
    @Test
    fun unknownSpeedIsNotMoving() {
        assertFalse(VehicleState().isMoving)
    }

    @Test
    fun movingStartsAtThreshold() {
        assertFalse(VehicleState(speedKmh = 0.9f).isMoving)
        assertTrue(VehicleState(speedKmh = VehicleState.MOVING_KMH).isMoving)
    }

    @Test
    fun distanceUsesAverageSpeed() {
        // 평균 36km/h(10m/s)로 2초면 20m
        assertEquals(20.0, SignalHub.distanceMeters(36f, 36f, 2000), 0.001)
        assertEquals(10.0, SignalHub.distanceMeters(0f, 36f, 2000), 0.001)
    }

    @Test
    fun noDistanceWithoutElapsedTime() {
        assertEquals(0.0, SignalHub.distanceMeters(36f, 36f, 0), 0.0)
        assertEquals(0.0, SignalHub.distanceMeters(36f, 36f, -5), 0.0)
    }
}
