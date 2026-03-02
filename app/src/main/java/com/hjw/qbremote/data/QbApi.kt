package com.hjw.qbremote.data

import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TransferInfo
import com.hjw.qbremote.data.model.SyncMainDataResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface QbApi {
    @FormUrlEncoded
    @POST("api/v2/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<String>

    @GET("api/v2/transfer/info")
    suspend fun transferInfo(): TransferInfo

    @GET("api/v2/torrents/info")
    suspend fun torrentsInfo(
        @Query("sort") sort: String = "added_on",
        @Query("reverse") reverse: Boolean = true,
    ): List<TorrentInfo>

    @GET("api/v2/sync/maindata")
    suspend fun syncMainData(
        @Query("rid") rid: Long = 0,
    ): SyncMainDataResponse

    @FormUrlEncoded
    @POST("api/v2/torrents/pause")
    suspend fun pauseTorrents(
        @Field("hashes") hashes: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/v2/torrents/resume")
    suspend fun resumeTorrents(
        @Field("hashes") hashes: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/v2/torrents/delete")
    suspend fun deleteTorrents(
        @Field("hashes") hashes: String,
        @Field("deleteFiles") deleteFiles: Boolean,
    ): Response<Unit>
}
