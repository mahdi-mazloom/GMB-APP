package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val titleFa: String,
    val titleNative: String,
    val subtitle: String,
    val flagEmoji: String,
    val isRtl: Boolean,
    val locale: Locale
) {
    FA(
        code = "fa",
        titleFa = "فارسی",
        titleNative = "فارسی",
        subtitle = "زبان پیش‌فرض برنامه",
        flagEmoji = "🇮🇷",
        isRtl = true,
        locale = Locale("fa")
    ),
    EN(
        code = "en",
        titleFa = "انگلیسی",
        titleNative = "English",
        subtitle = "US / International",
        flagEmoji = "🇺🇸",
        isRtl = false,
        locale = Locale.US
    ),
    AR_IQ(
        code = "ar-IQ",
        titleFa = "عربی عراق",
        titleNative = "العربية (العراق)",
        subtitle = "اللهجة العراقية",
        flagEmoji = "🇮🇶",
        isRtl = true,
        locale = Locale("ar", "IQ")
    );

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: FA
        }
    }
}

object LanguagePreferences {
    private const val PREFS_NAME = "gmb_language_prefs"
    private const val KEY_LANGUAGE = "key_app_language"

    private val _currentLanguage = MutableStateFlow(AppLanguage.FA)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedCode = prefs?.getString(KEY_LANGUAGE, AppLanguage.FA.code)
            _currentLanguage.value = AppLanguage.fromCode(savedCode)
        }
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.apply()
    }
}
