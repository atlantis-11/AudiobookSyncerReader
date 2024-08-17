package com.yevhenii.audiobooksyncer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationDataRepository {
    private val _notificationData = MutableStateFlow<NotificationData?>(null)
    val notificationData = _notificationData.asStateFlow()

    fun setNotificationData(newVal: NotificationData?) {
        _notificationData.value = newVal
    }
}