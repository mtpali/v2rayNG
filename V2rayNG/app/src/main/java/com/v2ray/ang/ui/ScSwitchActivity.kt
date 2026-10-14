package com.v2ray.ang.ui

import android.os.Bundle
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager

class ScSwitchActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (LauncherManager.hasDaemon(this)) {
            LauncherManager.stopVService(this)
        } else {
            LauncherManager.startVServiceFromToggle(this)
        }
        finish()
    }
}
