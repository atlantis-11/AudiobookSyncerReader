package com.yevhenii.audiobooksyncer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yevhenii.audiobooksyncer.SABPNotificationListener.SeekAmount
import com.yevhenii.audiobooksyncer.SABPNotificationListener.SeekDirection
import com.yevhenii.audiobooksyncer.ui.theme.AudiobookSyncerTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val notificationViewModel: NotificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasNotificationAccess()) {
            requestNotificationAccess()
        } else {
            toggleNotificationListenerService()
        }

        if (!Environment.isExternalStorageManager()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        setContent {
            KeepScreenOn()

            AudiobookSyncerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        MainScreen(notificationViewModel)
                    }
                }
            }
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val componentName = getNotificationListenerComponentName()
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners").let {
            return it != null && it.contains(componentName.flattenToString())
        }
    }

    private fun requestNotificationAccess() {
        val componentName = getNotificationListenerComponentName()
        val intent = Intent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS))
        intent.putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            componentName.flattenToString()
        )
        startActivity(intent)
    }

    private fun toggleNotificationListenerService() {
        val componentName = getNotificationListenerComponentName()
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun getNotificationListenerComponentName(): ComponentName {
        return ComponentName(this, SABPNotificationListener::class.java)
    }
}

@Composable
fun MainScreen(viewModel: NotificationViewModel) {
    val syncFragments = viewModel.syncFragments
    val currentFragmentIndex = viewModel.currentFragmentIndex
    val playbackState = viewModel.playbackState

    syncFragments ?: return
    currentFragmentIndex ?: return

    val listState = rememberLazyListState()

    // Scroll to the current index whenever it changes
    LaunchedEffect(currentFragmentIndex) {
        listState.animateScrollAndCentralizeItem(currentFragmentIndex)
    }

    Box {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 15.dp)
        ) {
            itemsIndexed(syncFragments) { index, item ->
                FragmentElement(item, index == currentFragmentIndex)
            }
        }

        PlaybackControlButton(
            playbackState = playbackState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
                .size(100.dp),
            onSeek = { direction, amount ->
                SABPNotificationListener.seek(direction, amount)
            },
            onTogglePlayback = {
                SABPNotificationListener.togglePlayback()
            }
        )
    }
}

@Composable
fun PlaybackControlButton(
    playbackState: Int,
    modifier: Modifier = Modifier,
    onSeek: (direction: SeekDirection, amount: SeekAmount) -> Unit,
    onTogglePlayback: () -> Unit
) {
    val vibratorManager = LocalContext.current.getSystemService(VibratorManager::class.java)
    val vibrator = vibratorManager.defaultVibrator

    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    // thresholds
    val smallDragThreshold = 100f
    val largeDragThreshold = 300f

    fun handleSeek(dragAmount: Float) {
        val absAmount = abs(dragAmount)
        val seekAmount = when {
            absAmount > largeDragThreshold -> SeekAmount.LARGE
            absAmount > smallDragThreshold -> SeekAmount.SMALL
            else -> return
        }

        val vibrationEffect = when (seekAmount) {
            SeekAmount.LARGE -> VibrationEffect.EFFECT_CLICK
            SeekAmount.SMALL -> VibrationEffect.EFFECT_TICK
        }

        val direction = when {
            dragAmount > 0 -> SeekDirection.FORWARD
            else -> SeekDirection.BACKWARD
        }

        vibrator.vibrate(VibrationEffect.createPredefined(vibrationEffect))
        onSeek(direction, seekAmount)
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = {
                totalDragAmount = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                totalDragAmount += dragAmount
            },
            onDragEnd = {
                handleSeek(totalDragAmount)
            }
        )
    }

    IconButton(
        modifier = modifier.then(dragModifier),
        onClick = { onTogglePlayback() }
    ) {
        if (playbackState == PlaybackState.STATE_PAUSED) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        } else {
            Icon(Icons.Default.Pause, contentDescription = "Pause")
        }
    }
}

@Composable
fun FragmentElement(syncFragment: SyncFragment, highlighted: Boolean) {
    var showTgt by remember { mutableStateOf(false) }

    val regularColor = LocalContentColor.current.copy(alpha = 0.8f)
    val fadedColor = LocalContentColor.current.copy(alpha = 0.3f)

    val textColor = if (highlighted) regularColor else fadedColor
    val fontSize = 17.sp
    val toggleContent = { showTgt = !showTgt }

    Column(
        modifier = Modifier
            .clickable(onClick = toggleContent)
            .fillMaxWidth()
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = syncFragment.src, color = textColor, fontSize = fontSize)

        if (showTgt) {
            Text(text = syncFragment.tgt, color = textColor, fontSize = fontSize)
        }
    }
}

suspend fun LazyListState.animateScrollAndCentralizeItem(index: Int) {
    val getItemInfo = {
        this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    }

    var animate = true
    var itemInfo = getItemInfo()

    if (itemInfo == null) {
        animate = false
        scrollToItem(index)
        itemInfo = getItemInfo()
    }

    if (itemInfo != null) {
        val center = layoutInfo.viewportEndOffset / 2
        val childCenter = itemInfo.offset + itemInfo.size / 2

        (childCenter - center).toFloat().let {
            if (animate) animateScrollBy(it)
            else scrollBy(it)
        }
    }
}

@Composable
fun KeepScreenOn() {
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }
}
