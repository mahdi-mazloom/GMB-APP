package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.ShahanPanelClient
import com.example.util.PersianDateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GMB NET", appName)
  }

  @Test
  fun `test PersianDateHelper conversions`() {
    val shamsiFromGregorian = PersianDateHelper.parseGregorianToShamsi("2026-09-11")
    assertEquals("1405/06/20", shamsiFromGregorian)

    val shamsiFromSlash = PersianDateHelper.parseGregorianToShamsi("1405/06/20")
    assertEquals("1405/06/20", shamsiFromSlash)

    val daysFromShamsi = PersianDateHelper.calculateRemainingDays("1405/06/20")
    // September 11, 2026 vs September 5, 2026 -> 6 days
    assertTrue("Days remaining should be positive", daysFromShamsi >= 0)

    val days = PersianDateHelper.calculateRemainingDays("2099-01-01")
    assertEquals(999, days)
  }

  @Test
  fun `test ShahanPanelClient parseSshConfig fallback`() {
    val config = ShahanPanelClient.parseSshConfig(null, "testuser", "testpass")
    assertEquals("ip.connection-net.ir", config.host)
    assertEquals(2280, config.port)
    assertEquals("testuser", config.username)
    assertEquals("testpass", config.password)
    assertEquals(7300, config.udpgwPort)
  }
}

