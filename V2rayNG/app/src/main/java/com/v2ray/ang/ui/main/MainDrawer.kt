package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.verticalScrollbar

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    RenameConfigs(R.drawable.ic_edit_24dp, R.string.title_rename_configs),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    VpnHotspot(R.drawable.ic_share_24dp, R.string.title_root_lan_sharing)
}

@Composable
fun MainDrawerContent(drawerState: DrawerState, vpnHotspotEnabled: Boolean, onNavigate: (MainDestination) -> Unit) {
    val scrollState = rememberScrollState()
    val hotspotState = stringResource(if (vpnHotspotEnabled) R.string.vpn_hotspot_on else R.string.vpn_hotspot_off)
    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.75f),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.verticalScroll(scrollState).verticalScrollbar(scrollState)) {
            MainDestination.entries.forEachIndexed { index, item ->
                if (index > 0) AppDivider()
                NavigationDrawerItem(
                    label = { Text(stringResource(item.labelRes)) },
                    selected = item == MainDestination.VpnHotspot && vpnHotspotEnabled,
                    onClick = { onNavigate(item) },
                    icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).semantics {
                        if (item == MainDestination.VpnHotspot) stateDescription = hotspotState
                    }
                )
            }
        }
    }
}
