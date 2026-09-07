package com.v2ray.ang.ui.main

import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MainImportMenuTest {

    @Test
    fun mainMoreMenuKeepsRealPingAndRemovesTcpOnlyTest() {
        assertEquals(
            listOf(
                MainMoreMenuAction.DeleteAll,
                MainMoreMenuAction.DeleteDuplicate,
                MainMoreMenuAction.DeleteInvalid,
                MainMoreMenuAction.ExportAll,
                MainMoreMenuAction.SortByTestResults,
                MainMoreMenuAction.TestAllRealPing,
                MainMoreMenuAction.UpdateSubscriptions,
            ),
            MainMoreMenuAction.entries,
        )
    }

    @Test
    fun drawerExposesRenameConfigsAction() {
        assertEquals(
            listOf(MainDrawerAction.RenameSubscriptionProfiles),
            drawerActions,
        )
        assertEquals(
            R.string.title_rename_configs,
            MainDrawerAction.RenameSubscriptionProfiles.labelRes,
        )
    }

    @Test
    fun drawerContainsOnlyCoreSettingsAndBackup() {
        assertEquals(
            listOf(
                MainDestination.Subscriptions,
                MainDestination.PerAppProxy,
                MainDestination.Routing,
                MainDestination.UserAssets,
                MainDestination.Settings,
                MainDestination.BackupRestore,
            ),
            drawerItems,
        )
    }

    @Test
    fun regularShareMenuContainsOnlyShareActions() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = false, includeManagementActions = false))
    }

    @Test
    fun regularMoreMenuContainsEveryActionInDisplayOrder() {
        assertEquals(
            ServerMenuAction.entries,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true),
        )
    }

    @Test
    fun complexShareMenuContainsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false),
        )
    }

    @Test
    fun complexMoreMenuRetainsManagementActions() {
        val expected = listOf(
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Delete,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = true, includeManagementActions = true))
    }
}
