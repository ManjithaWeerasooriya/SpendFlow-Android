package com.example.spendflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * SpendFlow colour tokens, including the roles Material 3 has no slot for (income, hero, snackbar action…).
 * Read them in composables through `SpendFlowTheme.colors`.
 */
@Immutable
data class SpendFlowColors(
    val violet: Color,
    val violetText: Color,
    val violetTint: Color,
    val lavender: Color,
    val ink: Color,
    // Signal fills (bars, icons, badges): need 3:1 against their surface
    val income: Color,
    val overCap: Color,
    val warning: Color,
    val info: Color,
    // Signal text: meets WCAG AA 4.5:1 on the page and card surfaces
    val incomeText: Color,
    val overCapText: Color,
    val warningText: Color,
    val infoText: Color,
    val destructive: Color,
    val onDestructive: Color,
    val page: Color,
    val cardShell: Color,
    val insetCard: Color,
    val tile: Color,
    val outline: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroPill: Color,
    val snackbar: Color,
    val snackbarAction: Color,
    val textHeading: Color,
    val textBody: Color,
    val textMuted: Color,
    val textOnDark: Color,
    val textOnDarkMuted: Color,
)

// Values match values/colors.xml and values-night/colors.xml while the View UI still reads those;
// ThemeColorsSyncTest fails if they drift.
internal val SpendFlowLightPalette = SpendFlowColors(
    violet = Color(0xFF532CFA),
    violetText = Color(0xFF532CFA),
    violetTint = Color(0xFFEFEBFF),
    lavender = Color(0xFFBEB1FA),
    ink = Color(0xFF161E2C),
    income = Color(0xFF22B14C),
    overCap = Color(0xFFE5484D),
    warning = Color(0xFFF08424),
    info = Color(0xFF4C6EF0),
    incomeText = Color(0xFF177934),
    overCapText = Color(0xFFD01E23),
    warningText = Color(0xFFA5540B),
    infoText = Color(0xFF345BEE),
    destructive = Color(0xFFD01E23),
    onDestructive = Color(0xFFFFFFFF),
    page = Color(0xFFECEDF0),
    cardShell = Color(0xFFF7F7F9),
    insetCard = Color(0xFFFFFFFF),
    tile = Color(0xFFE6E7EB),
    outline = Color(0xFFD9DBE1),
    heroStart = Color(0xFF161E2C),
    heroEnd = Color(0xFF0B0E14),
    heroPill = Color(0xFF2A3140),
    snackbar = Color(0xFF161E2C),
    snackbarAction = Color(0xFF363D4C),
    textHeading = Color(0xFF161E2C),
    textBody = Color(0xFF363D4C),
    textMuted = Color(0xFF626772),
    textOnDark = Color(0xFFFFFFFF),
    textOnDarkMuted = Color(0xFF9DA2AE),
)

internal val SpendFlowDarkPalette = SpendFlowColors(
    violet = Color(0xFF6E4DFB),
    // Violet is too dark as text on dark surfaces
    violetText = Color(0xFFA894FC),
    violetTint = Color(0xFF2B2652),
    lavender = Color(0xFFBEB1FA),
    ink = Color(0xFF161E2C),
    income = Color(0xFF34C463),
    overCap = Color(0xFFF26369),
    warning = Color(0xFFF59A45),
    info = Color(0xFF6E8BF5),
    incomeText = Color(0xFF34C463),
    overCapText = Color(0xFFF26369),
    warningText = Color(0xFFF59A45),
    infoText = Color(0xFF6E8BF5),
    // White on #F26369 fails AA, ink passes
    destructive = Color(0xFFF26369),
    onDestructive = Color(0xFF161E2C),
    page = Color(0xFF0B0E14),
    cardShell = Color(0xFF151A24),
    insetCard = Color(0xFF1C2230),
    tile = Color(0xFF232A38),
    outline = Color(0xFF2E3646),
    heroStart = Color(0xFF232A38),
    heroEnd = Color(0xFF1C2230),
    heroPill = Color(0xFF2E3646),
    snackbar = Color(0xFF232A38),
    snackbarAction = Color(0xFF2E3646),
    textHeading = Color(0xFFF3F4F8),
    textBody = Color(0xFFD2D5DD),
    textMuted = Color(0xFF9DA2AE),
    textOnDark = Color(0xFFFFFFFF),
    textOnDarkMuted = Color(0xFF9DA2AE),
)

// Same role mapping as Base.Theme.SpendFlow in values/themes.xml.
internal fun SpendFlowColors.toColorScheme(dark: Boolean): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = violet,
        onPrimary = Color.White,
        primaryContainer = violetTint,
        onPrimaryContainer = textHeading,
        secondary = lavender,
        onSecondary = ink,
        secondaryContainer = tile,
        onSecondaryContainer = textHeading,
        tertiary = info,
        onTertiary = Color.White,
        tertiaryContainer = tile,
        onTertiaryContainer = textHeading,
        error = overCapText,
        onError = onDestructive,
        errorContainer = overCap,
        onErrorContainer = ink,
        background = page,
        onBackground = textHeading,
        surface = page,
        onSurface = textHeading,
        surfaceVariant = tile,
        onSurfaceVariant = textBody,
        surfaceTint = violet,
        surfaceDim = page,
        surfaceBright = insetCard,
        surfaceContainerLowest = insetCard,
        surfaceContainerLow = cardShell,
        surfaceContainer = cardShell,
        surfaceContainerHigh = insetCard,
        surfaceContainerHighest = tile,
        inverseSurface = snackbar,
        inverseOnSurface = textOnDark,
        inversePrimary = lavender,
        outline = textMuted,
        outlineVariant = outline,
        scrim = Color.Black,
    )
