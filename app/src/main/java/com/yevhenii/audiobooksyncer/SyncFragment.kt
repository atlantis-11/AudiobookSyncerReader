package com.yevhenii.audiobooksyncer

import org.json.JSONObject

data class SyncFragment(
    val src: String,
    val tgt: String,
    val begin: Long,
    val end: Long
) {
    companion object {
        fun fromJSONObject(obj: JSONObject): SyncFragment {
            return SyncFragment(
                src = obj.getString("src"),
                tgt = obj.getString("tgt"),
                begin = obj.getLong("begin"),
                end = obj.getLong("end")
            )
        }
    }
}
