package com.example.githubusers.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** ブラウザやメールなど、アプリの外で開く操作。 */
class ExternalActions(private val context: Context) {

    /** Web ページを Custom Tabs で開く。スキームがなければ https とみなす。 */
    fun openUrl(url: String) {
        val uri = Uri.parse(normalizeWebUrl(url))
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            startSafely(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    fun sendEmail(address: String) {
        startSafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))
    }

    fun shareText(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        startSafely(Intent.createChooser(send, title))
    }

    private fun startSafely(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 開けるアプリがない場合は何もしない
        }
    }
}

@Composable
fun rememberExternalActions(): ExternalActions {
    val context = LocalContext.current
    return remember(context) { ExternalActions(context) }
}

/** `example.com` のようにスキームがない URL に https を付ける。 */
fun normalizeWebUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

/** 表示用に、URL からスキームと末尾のスラッシュを取り除く。 */
fun displayUrl(url: String): String =
    url.trim().replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "").trimEnd('/')
