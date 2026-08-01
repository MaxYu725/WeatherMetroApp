package com.weather.metro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weather.metro.domain.WeatherLoadState
import com.weather.metro.ui.components.MetroProgress
import com.weather.metro.ui.screens.CurrentScreen
import com.weather.metro.ui.screens.ForecastScreen
import com.weather.metro.ui.screens.HourlyScreen
import com.weather.metro.ui.screens.SettingsScreen
import com.weather.metro.ui.screens.ToolsScreen
import com.weather.metro.ui.theme.LocalMetroSubText
import com.weather.metro.ui.theme.LocalReduceMotion
import com.weather.metro.ui.theme.WeatherMetroTheme

private val pages = listOf("current", "hourly", "forecast", "tools", "settings")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherMetroRoot(
    viewModel: WeatherViewModel,
    requestLocationPermission: () -> Unit,
    requestNotificationPermission: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (settings.preciseLocation && !viewModel.hasLocationPermission()) {
            requestLocationPermission()
        } else {
            requestNotificationPermission()
        }
    }

    WeatherMetroTheme(settings) {
        val alignedInitialPage = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % pages.size)
        val pagerState = rememberPagerState(initialPage = alignedInitialPage) { Int.MAX_VALUE }
        val pageIndex = pagerState.currentPage.mod(pages.size)
        val reduceMotion = LocalReduceMotion.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (
                loadState is WeatherLoadState.Loading ||
                (loadState as? WeatherLoadState.Ready)?.refreshing == true
            ) {
                MetroProgress()
            } else {
                Spacer(Modifier.height(4.dp))
            }

            PivotHeader(
                current = pages[pageIndex],
                next = pages[(pageIndex + 1) % pages.size],
                reduceMotion = reduceMotion,
            )

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier.fillMaxSize(),
            ) { virtualPage ->
                val index = virtualPage.mod(pages.size)
                when (val state = loadState) {
                    WeatherLoadState.Loading -> LoadingPage()
                    is WeatherLoadState.Error -> ErrorPage(
                        message = state.message,
                        retry = viewModel::refresh,
                    )
                    is WeatherLoadState.Ready -> when (index) {
                        0 -> CurrentScreen(
                            snapshot = state.snapshot,
                            accent = MaterialTheme.colorScheme.primary,
                            onRefresh = viewModel::refresh,
                            onRequestLocation = requestLocationPermission,
                        )
                        1 -> HourlyScreen(state.snapshot.hourly)
                        2 -> ForecastScreen(state.snapshot)
                        3 -> ToolsScreen()
                        else -> SettingsScreen(
                            settings = settings,
                            onAccentChange = viewModel::setAccent,
                            onTextScaleChange = viewModel::setTextScale,
                            onPatternIntensityChange = viewModel::setPatternIntensity,
                            onReduceMotionChange = viewModel::setReduceMotion,
                            onHighContrastChange = viewModel::setHighContrast,
                            onPreciseLocationChange = viewModel::setPreciseLocation,
                            onNotificationsChange = { enabled ->
                                viewModel.setNotificationsEnabled(enabled)
                                if (enabled) requestNotificationPermission()
                            },
                            onClearCache = viewModel::clearCache,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PivotHeader(current: String, next: String, reduceMotion: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(start = 22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedContent(
            targetState = current to next,
            transitionSpec = {
                if (reduceMotion) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                else fadeIn(tween(260)) togetherWith fadeOut(tween(180))
            },
            label = "pivot header",
        ) { (active, upcoming) ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = active,
                    color = Color.White,
                    fontSize = 52.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.5).sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    text = upcoming,
                    color = Color(0xFF3D3D3D),
                    fontSize = 47.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun LoadingPage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在取得香港天文台資料…", color = LocalMetroSubText.current, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun ErrorPage(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.TopStart) {
        Column {
            Text("資料暫時無法更新", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(10.dp))
            Text(message, color = LocalMetroSubText.current)
            Spacer(Modifier.height(18.dp))
            Text(
                "retry",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable(onClick = retry)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
