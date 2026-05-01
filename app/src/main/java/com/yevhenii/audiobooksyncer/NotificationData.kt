package com.yevhenii.audiobooksyncer

data class NotificationData(
    val book: String,
    val chapter: String,
    val chapterPosition: Long,
    val playbackState: Int
)
