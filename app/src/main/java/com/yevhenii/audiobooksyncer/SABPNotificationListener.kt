package com.yevhenii.audiobooksyncer

import android.app.Notification.EXTRA_MEDIA_SESSION
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TITLE
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState.STATE_PLAYING
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast

private const val TAG = "SABPNotificationListener"

class SABPNotificationListener : NotificationListenerService() {
    companion object {
        private const val POLLING_INTERVAL_MS = 250L
        private val TARGET_PACKAGES = listOf("mdmt.sabp", "mdmt.sabp.free")

        private var mediaController: MediaController? = null

        fun togglePlayback() {
            mediaController?.let {
                Log.d(TAG, "Toggling playback state from: ${it.playbackState?.state}")

                it.dispatchMediaButtonEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                )
            }
        }
    }

    private lateinit var notificationFolder: String
    private lateinit var notificationFile: String

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isPolling = false

    private val updateNotificationDataRunnable = object : Runnable {
        override fun run() {
            updateNotificationData()
            uiHandler.postDelayed(this, POLLING_INTERVAL_MS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Toast.makeText(this, "Listening for SABP notifications!", Toast.LENGTH_SHORT).show()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!TARGET_PACKAGES.contains(sbn.packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getString(EXTRA_TITLE)
        val text = extras.getString(EXTRA_TEXT)

        if (title == null || text == null) return

        notificationFolder = text
        notificationFile = title

        @Suppress("DEPRECATION")
        val mediaSessionToken = extras.get(EXTRA_MEDIA_SESSION) as? MediaSession.Token ?: return
        mediaController = MediaController(applicationContext, mediaSessionToken)

        mediaController?.playbackState?.let {
            if (it.state == STATE_PLAYING) {
                if (!isPolling) startPollingPlaybackPosition()
            } else {
                if (isPolling) stopPollingPlaybackPosition()
                updateNotificationData()
            }

            Log.d(TAG, "Playback state: ${it.state}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!TARGET_PACKAGES.contains(sbn.packageName)) return

        stopPollingPlaybackPosition()
        NotificationDataRepository.setNotificationData(null)
        mediaController = null
    }

    private fun startPollingPlaybackPosition() {
        isPolling = true
        uiHandler.post(updateNotificationDataRunnable)
    }

    private fun stopPollingPlaybackPosition() {
        isPolling = false
        uiHandler.removeCallbacks(updateNotificationDataRunnable)
    }

    private fun updateNotificationData() {
        mediaController?.playbackState?.position?.let {
            NotificationDataRepository.setNotificationData(
                NotificationData(
                    folder = notificationFolder,
                    file = notificationFile,
                    filePosition = it
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPollingPlaybackPosition()
    }
}