package com.jarm.app.ui.remote

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarm.app.R
import com.jarm.app.remote.RemoteUsage
import com.jarm.app.remote.RemoteViewModel
import com.jarm.app.ui.motion.LocalJARMMotionPolicy
import com.jarm.app.ui.motion.MotionAwareModalBottomSheet
import com.jarm.app.ui.motion.MotionAwareCircularProgressIndicator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteUsageSheet(vm: RemoteViewModel, onDismiss: () -> Unit) {
    var usage by remember(vm) { mutableStateOf<RemoteUsage?>(null) }
    var loading by remember(vm) { mutableStateOf(true) }
    var appeared by remember { mutableStateOf(false) }
    val opacity by animateFloatAsState(if (appeared) 1f else 0f, tween(250), label = "usageAppearance")
    val spatialMotion = LocalJARMMotionPolicy.current.allowSpatialTransitions
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(Unit) { appeared = true }
    LaunchedEffect(vm) { usage = vm.usage(); loading = false }
    MotionAwareModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        com.jarm.app.ui.components.DialogWindowEdgeToEdge()
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(24.dp).graphicsLayer { alpha = opacity },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.remote_usage), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            AnimatedContent(
                targetState = loading,
                transitionSpec = {
                    (fadeIn(tween(250)) togetherWith fadeOut(tween(250))).using(
                        SizeTransform { _, _ -> tween(if (spatialMotion) 250 else 0) },
                    )
                },
                label = "usageContent",
            ) { isLoading ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                            MotionAwareCircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 4.dp)
                        }
                    } else if (usage?.limits.isNullOrEmpty()) {
                        Text(stringResource(R.string.remote_usage_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        usage?.limits?.forEach { limit ->
                            Text(limit.name.ifBlank { limit.id }, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            listOfNotNull(limit.primary, limit.secondary).forEach { window ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.remote_usage_remaining, window.remainingPercent),
                                        style = MaterialTheme.typography.bodyMedium)
                                    LinearProgressIndicator(
                                        progress = { window.remainingPercent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                    )
                                    window.windowDurationMins?.let { minutes ->
                                        Text(stringResource(R.string.remote_usage_window, usageWindowDuration(minutes, locale)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    window.resetsAt?.let { seconds ->
                                        Text(stringResource(R.string.remote_usage_resets,
                                            android.text.format.DateUtils.getRelativeTimeSpanString(seconds * 1000).toString()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Preserve partial hours/days and use the active locale's units and plural rules. */
internal fun usageWindowDuration(minutes: Long, locale: Locale): String {
    val measures = buildList {
        if (minutes >= 1440) add(Measure(minutes / 1440, MeasureUnit.DAY))
        if (minutes >= 60 && minutes % 1440 / 60 > 0) add(Measure(minutes % 1440 / 60, MeasureUnit.HOUR))
        if (minutes % 60 != 0L || isEmpty()) add(Measure(minutes % 60, MeasureUnit.MINUTE))
    }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).formatMeasures(*measures.toTypedArray())
}
