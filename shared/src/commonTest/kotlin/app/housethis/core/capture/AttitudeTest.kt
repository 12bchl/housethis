package app.housethis.core.capture

import app.housethis.core.vision.LineSegment
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttitudeTest {
    private fun assertNear(expected: Double, actual: Double, tol: Double = 1e-6) =
        assertTrue(abs(expected - actual) < tol, "expected $expected, got $actual")

    @Test
    fun uprightPortraitIsLevel() {
        val a = Attitude(Vec3(0.0, -1.0, 0.0), 0)
        assertNear(0.0, a.pitchDeg)
        assertNear(0.0, a.rollDeg)
        assertNear(1.0, a.downInImage.y.toDouble())
    }

    @Test
    fun landscapeLeftSideUpMapsDeviceXToImageDown() {
        val a = Attitude(Vec3(1.0, 0.0, 0.0), 90)
        assertNear(0.0, a.downInImage.x.toDouble())
        assertNear(1.0, a.downInImage.y.toDouble())
        assertNear(0.0, a.rollDeg)
    }

    @Test
    fun landscapeRightSideUpMapsDeviceMinusXToImageDown() {
        val a = Attitude(Vec3(-1.0, 0.0, 0.0), 270)
        assertNear(1.0, a.downInImage.y.toDouble())
    }

    @Test
    fun cameraTiltedUpHasPositivePitch() {
        val a = Attitude(Vec3(0.0, -0.866, 0.5), 0)
        assertNear(30.0, a.pitchDeg, 0.01)
    }

    @Test
    fun classifiesPlumbAndLevelLines() {
        val a = Attitude(Vec3(0.0, -1.0, 0.0), 0)
        assertEquals(LineOrientation.PLUMB, a.classify(LineSegment(10f, 0f, 11f, 100f, 1f, 5f)))
        assertEquals(LineOrientation.LEVEL, a.classify(LineSegment(0f, 10f, 100f, 12f, 1f, 5f)))
        assertEquals(LineOrientation.OTHER, a.classify(LineSegment(0f, 0f, 100f, 100f, 1f, 5f)))
    }

    @Test
    fun quantizesOrientationEventDegrees() {
        assertEquals(0, Attitude.quantizeRotation(10))
        assertEquals(90, Attitude.quantizeRotation(100))
        assertEquals(270, Attitude.quantizeRotation(300))
        assertEquals(0, Attitude.quantizeRotation(350))
    }
}
