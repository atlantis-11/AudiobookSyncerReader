package com.yevhenii.audiobooksyncer

import android.media.MediaMetadataRetriever
import android.media.session.PlaybackState
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

private const val TAG = "NotificationViewModel"

class NotificationViewModel : ViewModel() {
    var syncFragments by mutableStateOf<List<SyncFragment>?>(null)
        private set
    var currentFragmentIndex by mutableStateOf<Int?>(null)
        private set
    var playbackState by mutableIntStateOf(PlaybackState.STATE_NONE)
        private set

    private val repo = NotificationDataRepository
    private val audiobooksDir = "${Environment.getExternalStorageDirectory().absolutePath}/Audiobooks"
    private val syncMapFileName = "sync_map.json"

    private var currentFolder: String? = null
    private var audioFilesWithStartPositions: Map<String, Long>? = null

    init {
        viewModelScope.launch {
            repo.notificationData.collect {
                it ?: return@collect

                playbackState = it.playbackState

                handleFolderChange(it.folder)
                handleFileAndPositionChange(it.file, it.filePosition)
            }
        }
    }

    private suspend fun handleFolderChange(newFolder: String) {
        if (currentFolder == newFolder) return

        syncFragments = null
        currentFragmentIndex = null

        currentFolder = newFolder
        val bookDir = "$audiobooksDir/$newFolder"

        withContext(Dispatchers.IO) {
            Log.d(TAG, "Loading sync map...")

            val syncFile = File("$bookDir/$syncMapFileName")
            if (!syncFile.exists()) {
                return@withContext
            }

            val jsonString = syncFile.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            syncFragments = 0.until(jsonArray.length()).map { i ->
                SyncFragment.fromJSONObject(jsonArray.getJSONObject(i))
            }

            Log.d(TAG, "Sync map loaded")
            Log.d(TAG, "Loading audio files...")

            audioFilesWithStartPositions =
                getAudioFilesWithStartPositions(bookDir)

            Log.d(TAG, "Audio files loaded")
        }
    }

    private fun handleFileAndPositionChange(newFile: String, newPos: Long) {
        syncFragments ?: return

        val globalPos = audioFilesWithStartPositions?.get(newFile)?.plus(newPos)
        globalPos ?: return

        val currentFragment = currentFragmentIndex?.let { syncFragments?.get(it) }

        if (currentFragment != null &&
            globalPos >= currentFragment.begin &&
            globalPos <= currentFragment.end
        ) return

        currentFragmentIndex = findSyncFragmentIndex(globalPos) ?: return

        Log.d(TAG, "Fragment: $currentFragmentIndex")
    }

    private fun getAudioFilesWithStartPositions(directoryPath: String): Map<String, Long> {
        val directory = File(directoryPath)
        val files = directory.listFiles()?.sortedBy { it.name } ?: return emptyMap()

        var cumulativeDuration: Long = 0

        return files
            .filter { it.isFile && it.isAudioFile() }
            .associateWith { file ->
                val startPosition = cumulativeDuration
                cumulativeDuration += file.getDuration()
                startPosition
            }
            .mapKeys { it.key.name }
    }

    private fun File.isAudioFile(): Boolean {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(this.extension)
        return mimeType != null && mimeType.startsWith("audio")
    }

    private fun File.getDuration(): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this.absolutePath)
        val duration = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLong() ?: 0
        retriever.release()
        return duration
    }

    private fun findSyncFragmentIndex(position: Long): Int? {
        syncFragments ?: return null

        var left = 0
        var right = syncFragments!!.size - 1

        while (left <= right) {
            val mid = (left + right) / 2
            val fragment = syncFragments!![mid]

            when {
                position < fragment.begin -> right = mid - 1
                position > fragment.end -> left = mid + 1
                else -> return mid
            }
        }

        return null
    }
}