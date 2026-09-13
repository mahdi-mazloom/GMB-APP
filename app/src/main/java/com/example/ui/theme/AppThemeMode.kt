package com.example.ui.theme

enum class AppThemeMode(val key: String, val titleFa: String) {
    SYSTEM("system", "خودکار (سیستم)"),
    DARK("dark", "دارک مود"),
    LIGHT("light", "لایت مود");

    companion object {
        fun fromKey(key: String?): AppThemeMode {
            return entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }
}
