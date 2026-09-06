package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object BatteryOptimizationHelper {

    /**
     * Checks if the app is already exempt from Android battery saver / Doze mode restrictions.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * Requests the user to disable battery restrictions so VPN won't be killed in the background.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (!isIgnoringBatteryOptimizations(context)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "بهینه‌سازی باتری از قبل برای برنامه غیرفعال است (عملکرد پایدار)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Fallback to standard battery saver settings
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "لطفاً محدودیت باتری را از تنظیمات برنامه بردارید.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
