package com.hjw.qbremote.data.model

import com.google.gson.annotations.SerializedName

data class TransferInfo(
    @SerializedName("dl_info_speed") val downloadSpeed: Long = 0,
    @SerializedName("up_info_speed") val uploadSpeed: Long = 0,
    @SerializedName("dl_info_data") val downloadedTotal: Long = 0,
    @SerializedName("up_info_data") val uploadedTotal: Long = 0,
    @SerializedName("dht_nodes") val dhtNodes: Int = 0,
)

data class TorrentInfo(
    @SerializedName("hash") val hash: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("progress") val progress: Float = 0f,
    @SerializedName("state") val state: String = "",
    @SerializedName("dlspeed") val downloadSpeed: Long = 0,
    @SerializedName("upspeed") val uploadSpeed: Long = 0,
    @SerializedName("downloaded") val downloaded: Long = 0,
    @SerializedName("uploaded") val uploaded: Long = 0,
    @SerializedName("added_on") val addedOn: Long = 0,
    @SerializedName("last_activity") val lastActivity: Long = 0,
    @SerializedName("eta") val eta: Long = 0,
    @SerializedName("num_seeds") val seeders: Int = 0,
    @SerializedName("num_leechs") val leechers: Int = 0,
    @SerializedName("num_complete") val numComplete: Int = 0,
    @SerializedName("num_incomplete") val numIncomplete: Int = 0,
    @SerializedName("tracker") val tracker: String = "",
    @SerializedName("save_path") val savePath: String = "",
    @SerializedName("tags") val tags: String = "",
)

data class DashboardData(
    val transferInfo: TransferInfo,
    val torrents: List<TorrentInfo>,
)

data class SyncMainDataResponse(
    @SerializedName("rid") val rid: Long = 0,
    @SerializedName("full_update") val fullUpdate: Boolean = false,
    @SerializedName("server_state") val serverState: TransferInfo = TransferInfo(),
    @SerializedName("torrents") val torrents: Map<String, TorrentInfo> = emptyMap(),
    @SerializedName("torrents_removed") val torrentsRemoved: List<String> = emptyList(),
)
