package com.umuieme.videowebbrowser

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.umuieme.videowebbrowser.browser.PageScripts
import com.umuieme.videowebbrowser.browser.policy.AdBlocker
import com.umuieme.videowebbrowser.browser.policy.AdBlockerHolder
import com.umuieme.videowebbrowser.data.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency injection: the one place that knows how to build app-wide objects.
 * Created once by [VideoWebBrowserApp]; anything that needs a dependency asks the container
 * instead of constructing it, so every caller shares the same instance.
 */
class AppContainer(private val context: Context) {

    /**
     * Lives as long as the process. SupervisorJob: one failed child (e.g. a broken asset) doesn't
     * cancel the others. Never cancelled, because the Application is never destroyed.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    /** On-demand page scripts (scroll locator, close popup), shared by Back handling and scroll mode. */
    val pageScripts: PageScripts by lazy { PageScripts(context.assets) }

    /** Empty until [loadAdBlocklist] finishes; WebView clients treat empty as "block nothing". */
    val adBlockerHolder = AdBlockerHolder()

    /**
     * Parses the ad-domain list on the IO dispatcher (a thread pool for blocking file/network
     * work), so app start and the first frame aren't delayed by ~76k lines of parsing.
     */
    fun loadAdBlocklist() {
        appScope.launch(Dispatchers.IO) {
            val start = SystemClock.elapsedRealtime()
            try {
                val blocker = context.assets.open(BLOCKLIST_ASSET).bufferedReader().use { reader ->
                    AdBlocker.parse(reader, expectedSize = EXPECTED_DOMAINS)
                }
                adBlockerHolder.adBlocker = blocker
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Loaded ${blocker.size} ad domains in ${SystemClock.elapsedRealtime() - start} ms")
                }
            } catch (e: IOException) {
                // A missing list must not break browsing: navigation policy still works without it.
                Log.e(TAG, "Ad blocklist unavailable", e)
            }
        }
    }

    private companion object {
        const val TAG = "VWB-AdBlock"
        const val BLOCKLIST_ASSET = "blocklist/ad-domains.txt"
        const val EXPECTED_DOMAINS = 80_000
    }
}
