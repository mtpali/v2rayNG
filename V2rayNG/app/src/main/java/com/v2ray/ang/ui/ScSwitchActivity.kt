package com.v2ray.ang.ui

import android.os.Bundle
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScSwitchActivity : BaseActivity() {
    private var permissionPending = false
    private var toggleJob: Job? = null
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionPending = false
        if (it.resultCode == RESULT_OK) {
            lifecycleScope.launch {
                LauncherManager.startVServiceFromToggle(this@ScSwitchActivity)
                LauncherManager.awaitStartDispatch()
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        permissionPending = savedInstanceState?.getBoolean("permissionPending") == true
        if (permissionPending) return

        toggleJob = LauncherManager.toggleVService(this,
            onStart = {
                val permission = if (SettingsManager.isVpnMode() && !SettingsManager.isRootMode()) VpnService.prepare(this) else null
                if (permission == null) {
                    LauncherManager.startVServiceFromToggle(this)
                    LauncherManager.awaitStartDispatch()
                } else {
                    permissionPending = true
                    requestVpnPermission.launch(permission)
                }
            },
            onComplete = { if (!permissionPending) finish() },
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionPending", permissionPending)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        toggleJob?.cancel()
        super.onDestroy()
    }
}
