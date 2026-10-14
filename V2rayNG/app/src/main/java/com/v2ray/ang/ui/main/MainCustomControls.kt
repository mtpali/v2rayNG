package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R

// PullToRefreshBox is experimental in the pinned Material 3 BOM. Keep its state
// inside this presentation boundary; remove the opt-in when that API becomes stable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionRefreshContainer(
    isRefreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        PullToRefreshBox(isRefreshing, onRefresh, modifier = Modifier.fillMaxSize()) { content() }
    } else {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
internal fun RenameConfigsDialog(
    prefix: String,
    onPrefixChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_rename_configs)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .consumeWindowInsets(WindowInsets.navigationBars).imePadding()
            ) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = onPrefixChange,
                    label = { Text(stringResource(R.string.hint_profile_name_prefix)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.title_rename_configs)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
