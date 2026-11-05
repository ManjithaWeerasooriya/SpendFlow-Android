package com.example.spendflow.core.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/** [route] identifies the tab and is also its test tag, as "tab_<route>". */
data class BottomBarTab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int)

/**
 * The tabs either side of a raised Add button (02a); the first half of [tabs] sits left of it.
 * Reselecting the current tab still calls [onSelect]; ignore it there to keep the screen.
 */
@Composable
fun SpendFlowBottomBar(
    tabs: List<BottomBarTab>,
    selected: BottomBarTab?,
    onSelect: (BottomBarTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpendFlowTheme.colors
    val elevation = dimensionResource(R.dimen.sf_bottom_bar_elevation)
    val corner = dimensionResource(R.dimen.sf_corner_shell)
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.textHeading,
        selectedTextColor = colors.textHeading,
        unselectedIconColor = colors.textMuted,
        unselectedTextColor = colors.textMuted,
        disabledTextColor = colors.violetText,
        indicatorColor = Color.Transparent,
    )
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
    Box(modifier.fillMaxWidth()) {
        Surface(
            Modifier
                .padding(top = dimensionResource(R.dimen.sf_add_button_overhang))
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = corner, topEnd = corner),
            color = colors.insetCard,
            shadowElevation = elevation,
        ) {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, tab ->
                    if (i == tabs.size / 2) {
                        // Reserves the slot under the Add button and labels it; screen readers reach the button instead
                        NavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = { Spacer(Modifier.size(dimensionResource(R.dimen.sf_icon_size))) },
                            label = { Text(stringResource(R.string.nav_add), style = labelStyle) },
                            enabled = false,
                            colors = itemColors,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    val isSelected = tab == selected
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null, Modifier.size(dimensionResource(R.dimen.sf_icon_size))) },
                        label = {
                            Text(
                                stringResource(tab.label),
                                style = if (isSelected) labelStyle.copy(fontWeight = FontWeight.SemiBold) else labelStyle,
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(dimensionResource(R.dimen.sf_add_ring_size))
                .shadow(elevation, CircleShape)
                .background(colors.insetCard, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.size(dimensionResource(R.dimen.sf_add_button_size)),
                shape = CircleShape,
                containerColor = colors.violet,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_plus),
                    contentDescription = stringResource(R.string.nav_add_transaction),
                    Modifier.size(dimensionResource(R.dimen.sf_add_icon_size)),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SpendFlowBottomBarPreview() = SpendFlowTheme {
    val tabs = listOf(
        BottomBarTab("home", R.string.nav_home, R.drawable.ic_home_mono),
        BottomBarTab("transactions", R.string.nav_transactions, R.drawable.ic_transactions_mono),
        BottomBarTab("analytics", R.string.nav_analytics, R.drawable.ic_analytics_mono),
        BottomBarTab("more", R.string.nav_more, R.drawable.ic_more_mono),
    )
    var selected by remember { mutableStateOf(tabs.first()) }
    Box(Modifier.background(SpendFlowTheme.colors.page)) {
        SpendFlowBottomBar(tabs, selected, onSelect = { selected = it }, onAdd = {})
    }
}
