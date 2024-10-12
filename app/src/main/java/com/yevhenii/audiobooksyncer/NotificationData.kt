package com.yevhenii.audiobooksyncer

data class NotificationData(
    val folder: String,
    val file: String,
    val filePosition: Long,
    val playbackState: Int
)
