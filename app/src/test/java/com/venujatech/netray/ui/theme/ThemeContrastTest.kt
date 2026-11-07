package com.venujatech.netray.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun semanticTextAndAccentPairsMeetContrastTargetsForEveryAccentAndMode() {
        listOf("blue", "green", "purple", "orange").forEach { accent ->
            listOf(true, false).forEach { dark ->
                val colors = paletteForAccent(accent, dark)
                assertTrue("$accent dark=$dark primary text", contrastRatio(colors.textPrimary, colors.background) >= 4.5f)
                assertTrue("$accent dark=$dark secondary text", contrastRatio(colors.textSecondary, colors.background) >= 4.5f)
                assertTrue("$accent dark=$dark button label", contrastRatio(colors.onPrimary, colors.primary) >= 4.5f)
                assertTrue("$accent dark=$dark XRAY badge", contrastRatio(colors.onBadge, colors.badgeXray) >= 4.5f)
                assertTrue("$accent dark=$dark NEW badge", contrastRatio(colors.onBadge, colors.badgeNew) >= 4.5f)
                assertTrue("$accent dark=$dark error", contrastRatio(colors.error, colors.background) >= 4.5f)
                assertTrue("$accent dark=$dark success", contrastRatio(colors.success, colors.background) >= 4.5f)
            }
        }
    }

    @Test
    fun themeModeKeepsAccentButChangesSurfaceTokens() {
        val dark = paletteForAccent("purple", true)
        val light = paletteForAccent("purple", false)
        assertEqualsColor(dark.primary, light.primary)
        assertNotEquals(dark.background, light.background)
        assertNotEquals(dark.surfaceVariant, light.surfaceVariant)
    }

    private fun assertEqualsColor(first: androidx.compose.ui.graphics.Color, second: androidx.compose.ui.graphics.Color) {
        assertTrue(first == second)
    }
}
