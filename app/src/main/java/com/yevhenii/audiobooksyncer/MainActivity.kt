package com.yevhenii.audiobooksyncer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yevhenii.audiobooksyncer.ui.theme.AudiobookSyncerTheme

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
fun FragmentElement(syncFragment: SyncFragment, highlighted: Boolean) {
    var showTgt by remember { mutableStateOf(false) }

    val fadedColor = LocalContentColor.current.copy(alpha = 0.3f)

    val textColor = if (highlighted) LocalContentColor.current else fadedColor
    val toggleContent = { showTgt = !showTgt }

    Column(
        modifier = Modifier
            .clickable(onClick = toggleContent)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = syncFragment.src, color = textColor)

        if (showTgt) {
            Text(text = syncFragment.tgt, color = textColor)
        }
    }
}

@Composable
fun MainScreen(viewModel: NotificationViewModel) {
    val syncFragments = viewModel.syncFragments
    val currentFragmentIndex = viewModel.currentFragmentIndex

    syncFragments ?: return
    currentFragmentIndex ?: return

    val listState = rememberLazyListState()

    // Scroll to the current index whenever it changes
    LaunchedEffect(currentFragmentIndex) {
        listState.animateScrollAndCentralizeItem(currentFragmentIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(horizontal = 15.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
        contentPadding = PaddingValues(vertical = 30.dp)
    ) {
        itemsIndexed(syncFragments) { index, item ->
            FragmentElement(item, index == currentFragmentIndex)
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
