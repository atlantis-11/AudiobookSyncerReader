package com.yevhenii.audiobooksyncer

import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class NotificationViewModel : ViewModel() {
    var syncFragments by mutableStateOf<List<SyncFragment>?>(null)
        private set
    var currentFragmentIndex by mutableStateOf<Int?>(null)
        private set

    private val repo = NotificationDataRepository
    private val audiobooksDir = "${Environment.getExternalStorageDirectory().absolutePath}/Audiobooks"

    private var currentFolder: String? = null
    private var audioFilesWithStartPositions: Map<String, Long>? = null

    init {
        viewModelScope.launch {
            repo.notificationData.collect {
                it ?: return@collect

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
            Log.d("asd", "Loading json...")

            val syncFile = File("$bookDir/final_result.json")
            if (!syncFile.exists()) {
                return@withContext
            }

            val jsonString = syncFile.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            syncFragments = 0.until(jsonArray.length()).map { i ->
                SyncFragment.fromJSONObject(jsonArray.getJSONObject(i))
            }

            Log.d("asd", "Loading mp3 files...")

            audioFilesWithStartPositions =
                getAudioFilesWithStartPositions(bookDir)

            Log.d("asd", "mp3 files loaded")
        }
    }

    private fun handleFileAndPositionChange(newFile: String, newPos: Long) {
        syncFragments ?: return

        val globalPos = audioFilesWithStartPositions?.get(newFile)?.plus(newPos)
        globalPos ?: return

        val currentFragment = currentFragmentIndex?.let { syncFragments?.get(it) }

        if (currentFragment != null &&
            globalPos >= currentFragment.begin &&
            globalPos <= currentFragment.end) return

        currentFragmentIndex = findSyncFragmentIndex(globalPos) ?: return

        Log.d("asd", currentFragmentIndex.toString())
    }

    private fun getAudioFilesWithStartPositions(directoryPath: String): Map<String, Long> {
        val audioFilesWithStartPositions: MutableMap<String, Long> = mutableMapOf()
        var cumulativeDuration: Long = 0

        val directory = File(directoryPath)
        val files = directory.listFiles()?.sortedBy { it.name }

        if (files != null) {
            for (file in files) {
                if (file.isFile && file.name.endsWith(".mp3")) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val duration = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLong() ?: 0
                    retriever.release()

                    // Store the file name with its starting position
                    audioFilesWithStartPositions[file.name] = cumulativeDuration

                    // Update the cumulative duration
                    cumulativeDuration += duration
                }
            }
        }

        return audioFilesWithStartPositions
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