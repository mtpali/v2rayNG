package com.v2ray.ang.core

import com.v2ray.ang.handler.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class EnglishLocaleTest {
    @Test fun keepsEnglishEvenWhenSystemLocaleIsPersian() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa"))
            assertEquals(Locale.ENGLISH, SettingsManager.getLocale())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
