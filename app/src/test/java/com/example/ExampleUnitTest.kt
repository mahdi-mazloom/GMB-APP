package com.example

import com.example.ui.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testThemeModeKeys() {
    assertEquals("light", AppThemeMode.LIGHT.key)
    assertEquals("dark", AppThemeMode.DARK.key)
    assertEquals("system", AppThemeMode.SYSTEM.key)
  }

  @Test
  fun testThemeModeTitles() {
    assertEquals("لایت مود", AppThemeMode.LIGHT.titleFa)
    assertEquals("دارک مود", AppThemeMode.DARK.titleFa)
    assertEquals("خودکار (سیستم)", AppThemeMode.SYSTEM.titleFa)
  }
}

