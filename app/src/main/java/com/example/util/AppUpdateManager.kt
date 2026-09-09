package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val isForceUpdate: Boolean,
    val changelog: String,
    val downloadUrl: String,
    val releaseDate: String = "۱۴۰۳/۰۶/۱۶"
)

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    // GitHub repository configuration:
    var githubOwner = "mahdi-mazloom"
    var githubRepo = "GMB-APP"
    var githubBranch = "main"

    // Primary: GitHub Raw URL (High speed, zero API rate limits)
    val GITHUB_RAW_VERSION_URL: String
        get() = "https://raw.githubusercontent.com/$githubOwner/$githubRepo/$githubBranch/version.json"

    // GitHub Releases API URL (Supports standard GitHub Releases with attached APK assets)
    val GITHUB_RELEASES_URL: String
        get() = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"

    // Backup / Custom server URL
    private const val DEFAULT_VERSION_URL = "https://mahdis-net.ir/api/app_version.json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Helper to compare semantic versions (e.g. "1.2.1" > "1.2.0").
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").trim()
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").trim()
            if (cleanRemote.isBlank() || cleanRemote.equals(cleanCurrent, ignoreCase = true)) {
                return false
            }

            val rParts = cleanRemote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val cParts = cleanCurrent.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(rParts.size, cParts.size)

            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Checks remote server or GitHub for available updates.
     */
    suspend fun checkUpdate(
        currentVersionCode: Int = com.example.BuildConfig.VERSION_CODE,
        currentVersionName: String = com.example.BuildConfig.VERSION_NAME,
        customUrl: String? = null
    ): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            // First priority: customUrl, then GitHub Raw URL
            val targetUrl = customUrl?.takeIf { it.isNotBlank() } 
                ?: GITHUB_RAW_VERSION_URL

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "GMB-NET-Android")
                .header("Accept", "application/vnd.github.v3+json, application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // If GitHub raw version.json returned 404, fallback to GitHub Releases API
                if (targetUrl == GITHUB_RAW_VERSION_URL) {
                    return@withContext checkUpdate(currentVersionCode, currentVersionName, GITHUB_RELEASES_URL)
                }
                // If GitHub Releases also failed, fallback to domain URL
                if (targetUrl == GITHUB_RELEASES_URL) {
                    return@withContext checkUpdate(currentVersionCode, currentVersionName, DEFAULT_VERSION_URL)
                }
                return@withContext Result.success(null)
            }

            val bodyString = response.body?.string() ?: return@withContext Result.success(null)
            val json = JSONObject(bodyString)

            // Supports both custom format and GitHub Releases API format
            var latestCode = json.optInt("latest_version_code", 0)
            var latestName = json.optString("latest_version_name", "")
            var isForce = json.optBoolean("is_force_update", false)
            var changelog = json.optString("changelog", "")
            var downloadUrl = json.optString("download_url", "")

            // If GitHub Releases API format (tag_name / assets)
            if (json.has("tag_name")) {
                val rawTag = json.optString("tag_name", "").trim()
                val tagName = rawTag.removePrefix("v").removePrefix("V").trim()
                latestName = if (tagName.isNotBlank()) tagName else rawTag
                if (latestCode <= 0) {
                    val numbersOnly = tagName.filter { it.isDigit() }
                    latestCode = numbersOnly.toIntOrNull() ?: 0
                }
                changelog = json.optString("body", "• بهبود عملکرد و سرعت برنامه\n• بهینه‌سازی ارتباط و رفع اشکالات")
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    // Try to find an .apk asset first, otherwise use the first asset
                    var foundApk = false
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            foundApk = true
                            break
                        }
                    }
                    if (!foundApk) {
                        downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    }
                }
            }

            // Accurate update check:
            // An update is ONLY valid if the remote version is strictly newer than the current version!
            val isRemoteNameNewer = isNewerVersion(latestName, currentVersionName)
            val isRemoteCodeNewer = latestCode > currentVersionCode && latestCode > 0
            val isSameOrOlderName = latestName.isNotBlank() && !isRemoteNameNewer

            val hasUpdate = if (isRemoteNameNewer) {
                true
            } else if (isRemoteCodeNewer && !isSameOrOlderName) {
                true
            } else {
                false
            }

            if (hasUpdate && downloadUrl.isNotBlank()) {
                val updateInfo = AppUpdateInfo(
                    latestVersionCode = latestCode,
                    latestVersionName = latestName.ifBlank { "1.2.1" },
                    isForceUpdate = isForce,
                    changelog = changelog.ifBlank { "• بهبود عملکرد و افزایش سرعت اتصال\n• رفع باگ‌های گزارش‌شده" },
                    downloadUrl = downloadUrl,
                    releaseDate = json.optString("release_date", "امروز")
                )
                Result.success(updateInfo)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check update: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Downloads APK with live progress (0.0 to 1.0) and prompts installation.
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if app has permission to install unknown apps on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(settingsIntent)
                    }
                }
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطا در دانلود فایل: کد ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("بدنه فایل دانلود خالی است"))
            val contentLength = body.contentLength()

            // Save APK in app external files dir so package installer can read via FileProvider
            val apkFile = File(context.getExternalFilesDir(null), "gmbnet.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L

            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                outputStream.write(buffer, 0, read)
                totalBytesRead += read

                if (contentLength > 0) {
                    val progress = (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            onProgress(1f)

            // Trigger Android Package Installer
            withContext(Dispatchers.Main) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(installIntent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download and install failed", e)
            Result.failure(e)
        }
    }
}
