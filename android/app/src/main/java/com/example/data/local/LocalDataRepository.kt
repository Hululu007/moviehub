package com.example.data.local

import com.example.data.model.CategoryTree
import com.example.data.model.MusicTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalDataRepository(private val dao: MovieHubDao) {
    val watchHistory: Flow<List<WatchHistoryEntity>> = dao.getWatchHistory()

    val favoriteVods: Flow<List<FavoriteVodEntity>> = dao.getFavoriteVods()

    val diagnosticLogs: Flow<List<SourceDiagnosticLogEntity>> = dao.getDiagnosticLogs()

    val videoDownloads: Flow<List<VideoDownloadEntity>> = dao.getVideoDownloads()

    val recentVodSearches: Flow<List<String>> = dao.getRecentSearches("vod")
        .map { list -> list.map { it.keyword } }

    val playlistTracks: Flow<List<MusicTrack>> = dao.getPlaylistTracks()
        .map { list ->
            list.map { e ->
                MusicTrack(
                    id = e.id,
                    platform = e.platform,
                    name = e.name,
                    singer = e.singer,
                    album = e.album,
                    duration = e.duration,
                    pic = e.pic,
                    hash = e.hash,
                    albumId = e.albumId,
                    rid = e.rid,
                    directUrl = e.directUrl
                )
            }
        }

    suspend fun saveWatchHistory(item: WatchHistoryEntity) {
        dao.upsertWatchHistory(item)
    }

    suspend fun removeWatchHistory(normKey: String) {
        dao.deleteWatchHistory(normKey)
    }

    suspend fun clearWatchHistory() {
        dao.clearAllWatchHistory()
    }

    suspend fun saveFavoriteVod(item: FavoriteVodEntity) {
        dao.upsertFavoriteVod(item)
    }

    suspend fun removeFavoriteVod(normKey: String) {
        dao.deleteFavoriteVod(normKey)
    }

    suspend fun addRecentSearch(keyword: String, scope: String = "vod") {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            dao.upsertRecentSearch(RecentSearchEntity(keyword = trimmed, scope = scope))
        }
    }

    suspend fun clearRecentSearches(scope: String = "vod") {
        dao.clearRecentSearches(scope)
    }

    suspend fun addTrackToPlaylist(track: MusicTrack) {
        dao.upsertPlaylistTrack(
            PlaylistTrackEntity(
                uniqueKey = track.uniqueKey,
                id = track.id,
                platform = track.platform,
                name = track.name,
                singer = track.singer,
                album = track.album,
                duration = track.duration,
                pic = track.pic,
                hash = track.hash,
                albumId = track.albumId,
                rid = track.rid,
                directUrl = track.directUrl
            )
        )
    }

    suspend fun removeTrackFromPlaylist(uniqueKey: String) {
        dao.deletePlaylistTrack(uniqueKey)
    }

    suspend fun clearPlaylist() {
        dao.clearPlaylist()
    }

    suspend fun getCachedCategoryTree(srcId: String): CategoryTree? {
        val entity = dao.getCategoryCache(srcId) ?: return null
        val sevenDaysMs = 7L * 24L * 3600L * 1000L
        if (System.currentTimeMillis() - entity.updatedAt > sevenDaysMs) return null
        return CategoryTree.fromJsonString(entity.treeJson)
    }

    suspend fun saveCategoryTreeCache(srcId: String, tree: CategoryTree) {
        dao.saveCategoryCache(
            CategoryCacheEntity(
                srcId = srcId,
                treeJson = tree.toJsonString(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getRemoteSourceBundleJson(): String? {
        return dao.getCategoryCache("__remote_sources_bundle__")?.treeJson
    }

    suspend fun saveRemoteSourceBundleJson(rawJson: String) {
        dao.saveCategoryCache(
            CategoryCacheEntity(
                srcId = "__remote_sources_bundle__",
                treeJson = rawJson,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getRemoteSourceUrl(): String? {
        return dao.getCategoryCache("__remote_sources_url__")?.treeJson
    }

    suspend fun saveRemoteSourceUrl(url: String) {
        dao.saveCategoryCache(
            CategoryCacheEntity(
                srcId = "__remote_sources_url__",
                treeJson = url.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun insertDiagnosticLog(log: SourceDiagnosticLogEntity) {
        dao.insertDiagnosticLog(log)
    }

    suspend fun insertDiagnosticLogs(logs: List<SourceDiagnosticLogEntity>) {
        dao.insertDiagnosticLogs(logs)
    }

    suspend fun clearDiagnosticLogs() {
        dao.clearDiagnosticLogs()
    }

    suspend fun getVideoDownloadById(taskId: String): VideoDownloadEntity? {
        return dao.getVideoDownloadById(taskId)
    }

    suspend fun upsertVideoDownload(task: VideoDownloadEntity) {
        dao.upsertVideoDownload(task)
    }

    suspend fun deleteVideoDownload(taskId: String) {
        dao.deleteVideoDownload(taskId)
    }

    suspend fun clearAllVideoDownloads() {
        dao.clearAllVideoDownloads()
    }
}
