package com.weather.metro.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentColorTest {
    @Test
    fun `stored ARGB long is converted to an sRGB Compose color`() {
        val color = argbColor(0xFF1BA1E2)

        assertEquals(1f, color.alpha, 0.001f)
        assertEquals(0x1B / 255f, color.red, 0.001f)
        assertEquals(0xA1 / 255f, color.green, 0.001f)
        assertEquals(0xE2 / 255f, color.blue, 0.001f)
    }
}
