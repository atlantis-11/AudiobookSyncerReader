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
import wseemann.media.FFmpegMediaMetadataRetriever
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

    private var currentBook: String? = null
    private var chaptersWithStartPositions: Map<String, Long>? = null

    init {
        viewModelScope.launch {
            repo.notificationData.collect {
                it ?: return@collect

                playbackState = it.playbackState

                handleBookChange(it.book)
                handleChapterAndPositionChange(it.chapter, it.chapterPosition)
            }
        }
    }

    private suspend fun handleBookChange(newBook: String) {
        if (currentBook == newBook) return

        syncFragments = null
        currentFragmentIndex = null

        currentBook = newBook
        val bookPath = "$audiobooksDir/$newBook"

        withContext(Dispatchers.IO) {
            Log.d(TAG, "Loading sync map...")

            val bookFile = File(bookPath)
            val isM4b = bookFile.isFile && bookFile.extension.equals("m4b", ignoreCase = true)

            // Determine sync map file based on whether the input is an M4B file or a folder
            val syncFile = if (isM4b) {
                File(bookFile.parentFile, "${bookFile.nameWithoutExtension}.json")
            } else {
                File("$bookPath/$syncMapFileName")
            }

            if (!syncFile.exists()) {
                Log.d(TAG, "Sync file not found at: ${syncFile.absolutePath}")
                return@withContext
            }

            val jsonString = syncFile.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            syncFragments = 0.until(jsonArray.length()).map { i ->
                SyncFragment.fromJSONObject(jsonArray.getJSONObject(i))
            }

            Log.d(TAG, "Sync map loaded")
            Log.d(TAG, "Loading audio files/chapters...")

            // Populate the map keys with either chapter titles (M4B) or file names (Folder)
            chaptersWithStartPositions = if (isM4b) {
                getM4bChaptersWithStartPositions(bookPath)
            } else {
                getAudioFilesWithStartPositions(bookPath)
            }

            Log.d(TAG, "Audio files/chapters loaded")
        }
    }

    private fun handleChapterAndPositionChange(newChapter: String, newPos: Long) {
        syncFragments ?: return

        val globalPos = chaptersWithStartPositions?.get(newChapter)?.plus(newPos)
        globalPos ?: return

        val currentFragment = currentFragmentIndex?.let { syncFragments?.get(it) }

        if (currentFragment != null &&
            globalPos >= currentFragment.begin &&
            globalPos <= currentFragment.end
        ) return

        currentFragmentIndex = findSyncFragmentIndex(globalPos) ?: return

        Log.d(TAG, "Fragment: $currentFragmentIndex")
    }

    private fun getM4bChaptersWithStartPositions(filePath: String): Map<String, Long> {
        val retriever = FFmpegMediaMetadataRetriever()
        val chapterMap = mutableMapOf<String, Long>()

        try {
            retriever.setDataSource(filePath)
            val chapterCountStr = retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_CHAPTER_COUNT)
            val chapterCount = chapterCountStr.toInt()

            for (i in 0 until chapterCount) {
                val title = retriever.extractMetadataFromChapter(
                    FFmpegMediaMetadataRetriever.METADATA_KEY_TITLE, i
                )

                val startTimeStr = retriever.extractMetadataFromChapter(
                    FFmpegMediaMetadataRetriever.METADATA_KEY_CHAPTER_START_TIME, i
                )

                val startTime = startTimeStr.toLong()
                chapterMap[title] = startTime
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract M4B chapter metadata", e)
        } finally {
            retriever.release()
        }

        return chapterMap
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