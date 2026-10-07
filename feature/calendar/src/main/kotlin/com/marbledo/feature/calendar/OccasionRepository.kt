package com.marbledo.feature.calendar

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class OccasionSource { BUNDLED, CACHED, NETWORK }

enum class OccasionRefreshResult { UPDATED, UNCHANGED, SKIPPED, FAILED }

data class OccasionState(
    val catalog: OccasionCatalog = OccasionCatalog.EMPTY,
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val source: OccasionSource = OccasionSource.BUNDLED,
    val lastUpdatedEpochMillis: Long? = null,
    val lastError: Boolean = false,
) {
    val occasionCount: Int get() = catalog.occasions.size
    val isFromInternet: Boolean get() = source != OccasionSource.BUNDLED
}

/**
 * Keeps the occasion catalog in sync: the bundled asset is the offline baseline, the cached copy is
 * what the last successful download produced, and the remote file is this repository's asset.
 */
class OccasionRepository(private val context: Context) {
    private val _state = MutableStateFlow(OccasionState())
    val state: StateFlow<OccasionState> = _state.asStateFlow()

    @Volatile private var loaded = false
    @Volatile private var refreshing = false

    suspend fun load() {
        if (loaded) return
        loaded = true
        val bundled = readAsset()
        val cached = readCache()
        val catalog = cached?.first ?: bundled ?: OccasionCatalog.EMPTY
        val source = when {
            cached != null -> OccasionSource.CACHED
            bundled != null -> OccasionSource.BUNDLED
            else -> OccasionSource.BUNDLED
        }
        _state.value = _state.value.copy(
            catalog = catalog,
            isLoaded = true,
            source = source,
            lastUpdatedEpochMillis = cached?.second,
            lastError = false,
        )
    }

    /** Fetches the catalog from the internet. Never throws: the previous catalog always survives. */
    suspend fun refresh(): OccasionRefreshResult {
        if (refreshing) return OccasionRefreshResult.SKIPPED
        refreshing = true
        _state.value = _state.value.copy(isRefreshing = true, lastError = false)
        return try {
            var outcome = OccasionRefreshResult.FAILED
            for (url in CATALOG_URLS) {
                val body = download(url)
                val parsed = body?.let(OccasionCatalog::parse)
                if (parsed == null) continue
                val current = _state.value.catalog
                if (current.occasions.isNotEmpty() && parsed.occasions.size == current.occasions.size && parsed.dataVersion == current.dataVersion) {
                    outcome = OccasionRefreshResult.UNCHANGED
                    writeCache(body)
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        source = OccasionSource.NETWORK,
                        lastUpdatedEpochMillis = cacheFile().lastModified(),
                        lastError = false,
                    )
                    break
                }
                writeCache(body)
                _state.value = OccasionState(
                    catalog = parsed,
                    isLoaded = true,
                    isRefreshing = false,
                    source = OccasionSource.NETWORK,
                    lastUpdatedEpochMillis = cacheFile().lastModified(),
                    lastError = false,
                )
                outcome = OccasionRefreshResult.UPDATED
                break
            }
            if (outcome == OccasionRefreshResult.FAILED) {
                _state.value = _state.value.copy(isRefreshing = false, lastError = true)
            }
            outcome
        } catch (_: Exception) {
            _state.value = _state.value.copy(isRefreshing = false, lastError = true)
            OccasionRefreshResult.FAILED
        } finally {
            refreshing = false
        }
    }

    /** Background-friendly refresh that skips the download while the cached copy is still fresh. */
    suspend fun refreshIfStale(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): OccasionRefreshResult {
        load()
        val lastUpdated = _state.value.lastUpdatedEpochMillis
        if (lastUpdated != null && System.currentTimeMillis() - lastUpdated < maxAgeMillis) return OccasionRefreshResult.SKIPPED
        return refresh()
    }

    private suspend fun readAsset(): OccasionCatalog? = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.let(OccasionCatalog::parse)
    }

    private suspend fun readCache(): Pair<OccasionCatalog, Long>? = withContext(Dispatchers.IO) {
        val file = cacheFile()
        if (!file.exists()) return@withContext null
        val text = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        OccasionCatalog.parse(text)?.let { it to file.lastModified() }
    }

    private suspend fun writeCache(body: String) = withContext(Dispatchers.IO) {
        runCatching {
            val file = cacheFile()
            file.parentFile?.mkdirs()
            file.writeText(body)
        }
    }

    private fun cacheFile(): File = File(File(context.filesDir, CACHE_DIRECTORY), CATALOG_FILE_NAME)

    private suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MarbleDo-Android")
            }
            try {
                if (connection.responseCode !in 200..299) throw IOException("Unexpected status ${connection.responseCode}")
                connection.inputStream.bufferedReader().use { reader -> readBounded(reader) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Reads at most [MAX_CATALOG_BYTES] characters so a hostile or broken response cannot exhaust memory. */
    private fun readBounded(reader: java.io.Reader): String {
        val builder = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            builder.append(buffer, 0, count)
            if (builder.length > MAX_CATALOG_BYTES) throw IOException("Catalog response is too large")
        }
        return builder.toString()
    }

    companion object {
        const val CACHE_DIRECTORY = "occasions"
        const val CATALOG_FILE_NAME = "occasions-catalog.json"
        private const val CATALOG_ASSET = "calendar/holidays-fa.json"
        private const val CONNECT_TIMEOUT_MILLIS = 8_000
        private const val READ_TIMEOUT_MILLIS = 12_000
        private const val MAX_CATALOG_BYTES = 1_500_000
        private const val DEFAULT_MAX_AGE_MILLIS = 20L * 60L * 60L * 1000L

        /** The primary source is the project's checked-in catalog; the CDN copy is the fallback. */
        val CATALOG_URLS = listOf(
            "https://raw.githubusercontent.com/marble098/MarbleDo/main/feature/calendar/src/main/assets/calendar/holidays-fa.json",
            "https://cdn.jsdelivr.net/gh/marble098/MarbleDo@main/feature/calendar/src/main/assets/calendar/holidays-fa.json",
        )
    }
}
