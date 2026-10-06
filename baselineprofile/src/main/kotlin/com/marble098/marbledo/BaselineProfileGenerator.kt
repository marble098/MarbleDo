package com.marble098.marbledo.baselineprofile

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupAndPrimaryNavigation() = benchmarkRule.collectBaselineProfile(
        packageName = "com.marble098.marbledo",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
