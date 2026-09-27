package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This forwarding activity has no content view. Complete import before finishing,
        // otherwise destroying the activity cancels its lifecycle-owned import job.
        lifecycleScope.launch {
            try {
                if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    parseUri(intent.getStringExtra(Intent.EXTRA_TEXT), null)
                } else if (intent.action == Intent.ACTION_VIEW) {
                    val uri = intent.data
                    when (uri?.host) {
                        "install-config", "install-sub" ->
                            parseUri(uri.getQueryParameter("url"), uri.fragment)
                        else -> toastError(R.string.toast_failure)
                    }
                }
                startActivity(Intent(this@UrlSchemeActivity, MainActivity::class.java))
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "URL scheme import failed", error)
                toastError(R.string.toast_failure)
                finish()
            }
        }
    }

    private suspend fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            return
        }

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
                decodedUrl += "#${fragment}"
            }
            val (count, countSub) = withContext(Dispatchers.IO) {
                AngConfigManager.importBatchConfig(decodedUrl, "", false)
            }
            if (count + countSub > 0) {
                toast(R.string.import_subscription_success)
            } else {
                toast(R.string.import_subscription_failure)
            }
        }
    }
}