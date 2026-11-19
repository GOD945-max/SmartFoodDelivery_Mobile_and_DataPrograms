package com.group.smartfood

import com.group.smartfood.utils.AppConfig
import com.group.smartfood.utils.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreHelpersTest {

    @Test
    fun `fallback to District 1 HCMC when emulator returns US coordinates`() {
        val result = LocationHelper.resolveSafeVietnamLocation(37.421998, -122.084000)
        assertEquals(AppConfig.DEFAULT_LAT, result.first, 0.0001)
        assertEquals(AppConfig.DEFAULT_LNG, result.second, 0.0001)
    }

    @Test
    fun `preserve valid Vietnam coordinates`() {
        val result = LocationHelper.resolveSafeVietnamLocation(10.7738, 106.7041)
        assertEquals(10.7738, result.first, 0.0001)
        assertEquals(106.7041, result.second, 0.0001)
    }
}
