package com.marbledo.feature.calendar

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** The local asset and cache are usable even when every remote mirror is unavailable. */
enum class OccasionSource { BUNDLED, CACHED, NETWORK }

enum class OccasionRefreshResult { UPDATED, UNCHANGED, SKIPPED, OFFLINE, FAILED }

data class OccasionState(
    val catalog: OccasionCatalog = OccasionCatalog.EMPTY,
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val source: OccasionSource = OccasionSource.BUNDLED,
    val lastUpdatedEpochMillis: Long? = null,
    val lastError: Boolean = false,
    val lastRefreshWasOffline: Boolean = false,
) {
    val occasionCount: Int get() = catalog.occasions.size
    val isFromInternet: Boolean get() = source == OccasionSource.NETWORK
}

/**
 * Keeps the occasion catalog available offline and refreshes it from several mirrors of the
 * versioned catalog in this repository. A bad response never replaces a good cache or bundled copy.
 */
class OccasionRepository(private val context: Context) {
    private val _state = MutableStateFlow(OccasionState())
    val state: StateFlow<OccasionState> = _state.asStateFlow()

    private val loadMutex = Mutex()
    private val refreshMutex = Mutex()
    @Volatile private var loaded = false

    suspend fun load() {
        loadMutex.lock()
        try {
            if (loaded) return
            val bundled = readAsset()
            val cached = readCache()
            val catalog = cached?.first ?: bundled ?: OccasionCatalog.EMPTY
            _state.value = _state.value.copy(
                catalog = catalog,
                isLoaded = true,
                source = if (cached != null) OccasionSource.CACHED else OccasionSource.BUNDLED,
                lastUpdatedEpochMillis = cached?.second,
                lastError = false,
                lastRefreshWasOffline = false,
            )
            // Mark loaded only after both sources were examined. If an unexpected failure escapes,
            // the next caller can retry instead of remaining stuck with an empty catalog.
            loaded = true
        } finally {
            loadMutex.unlock()
        }
    }

    /** Fetches and validates mirrors in order. Never removes the currently visible catalog. */
    suspend fun refresh(): OccasionRefreshResult {
        load()
        if (!refreshMutex.tryLock()) return OccasionRefreshResult.SKIPPED
        try {
            if (!hasInternetConnection()) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    lastError = false,
                    lastRefreshWasOffline = true,
                )
                return OccasionRefreshResult.OFFLINE
            }

            _state.value = _state.value.copy(
                isRefreshing = true,
                lastError = false,
                lastRefreshWasOffline = false,
            )
            for (source in CATALOG_SOURCES) {
                val body = download(source) ?: continue
                val parsed = OccasionCatalog.parse(body) ?: continue
                // Reject short, partial, or accidentally returned error documents before they can
                // replace the much richer offline catalog.
                if (parsed.occasions.size !in MIN_REMOTE_OCCASIONS..MAX_REMOTE_OCCASIONS) continue

                val changed = parsed != _state.value.catalog
                val downloadedAt = System.currentTimeMillis()
                val cachedAt = writeCache(body) ?: downloadedAt
                _state.value = _state.value.copy(
                    catalog = parsed,
                    isLoaded = true,
                    isRefreshing = false,
                    source = OccasionSource.NETWORK,
                    lastUpdatedEpochMillis = cachedAt,
                    lastError = false,
                    lastRefreshWasOffline = false,
                )
                loaded = true
                return if (changed) OccasionRefreshResult.UPDATED else OccasionRefreshResult.UNCHANGED
            }

            _state.value = _state.value.copy(
                isRefreshing = false,
                lastError = true,
                lastRefreshWasOffline = false,
            )
            return OccasionRefreshResult.FAILED
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(isRefreshing = false)
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(
                isRefreshing = false,
                lastError = true,
                lastRefreshWasOffline = false,
            )
            return OccasionRefreshResult.FAILED
        } finally {
            refreshMutex.unlock()
        }
    }

    /** Background refresh which avoids a download while the last successful copy is still fresh. */
    suspend fun refreshIfStale(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): OccasionRefreshResult {
        load()
        val lastUpdated = _state.value.lastUpdatedEpochMillis
        val now = System.currentTimeMillis()
        if (lastUpdated != null && now >= lastUpdated && now - lastUpdated < maxAgeMillis) {
            return OccasionRefreshResult.SKIPPED
        }
        return refresh()
    }

    private fun hasInternetConnection(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

    /** Writes to a temporary file and atomically replaces the previous cache when supported. */
    private suspend fun writeCache(body: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val target = cacheFile()
            val directory = target.parentFile ?: error("Cache directory is unavailable")
            if (!directory.exists() && !directory.mkdirs()) error("Could not create cache directory")
            val temporary = File(directory, "$CATALOG_FILE_NAME.tmp")
            temporary.writeText(body)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val timestamp = System.currentTimeMillis()
            target.setLastModified(timestamp)
            timestamp
        }.getOrNull()
    }

    private fun cacheFile(): File = File(File(context.filesDir, CACHE_DIRECTORY), CATALOG_FILE_NAME)

    private suspend fun download(source: CatalogSource): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", source.accept)
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { reader -> readBounded(reader) }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads at most [MAX_CATALOG_CHARS] characters from any source. */
    private fun readBounded(reader: java.io.Reader): String {
        val builder = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            builder.append(buffer, 0, count)
            if (builder.length > MAX_CATALOG_CHARS) throw IOException("Catalog response is too large")
        }
        return builder.toString()
    }

    private data class CatalogSource(val url: String, val accept: String = "application/json")

    companion object {
        const val CACHE_DIRECTORY = "occasions"
        const val CATALOG_FILE_NAME = "occasions-catalog.json"
        private const val CATALOG_ASSET = "calendar/holidays-fa.json"
        private const val CONNECT_TIMEOUT_MILLIS = 3_000
        private const val READ_TIMEOUT_MILLIS = 4_000
        private const val MAX_CATALOG_CHARS = 3_000_000
        private const val MIN_REMOTE_OCCASIONS = 20
        private const val MAX_REMOTE_OCCASIONS = 5_000
        private const val DEFAULT_MAX_AGE_MILLIS = 20L * 60L * 60L * 1000L
        private const val USER_AGENT = "MarbleDo-Android"

        /**
         * Independent delivery paths for the same checked-in catalog: GitHub raw, two jsDelivr
         * edges and the GitHub Contents API raw media type. A mirror outage does not block the rest.
         */
        private val CATALOG_SOURCES = listOf(
            CatalogSource("https://raw.githubusercontent.com/marble098/MarbleDo/main/feature/calendar/src/main/assets/calendar/holidays-fa.json"),
            CatalogSource("https://cdn.jsdelivr.net/gh/marble098/MarbleDo@main/feature/calendar/src/main/assets/calendar/holidays-fa.json"),
            CatalogSource("https://fastly.jsdelivr.net/gh/marble098/MarbleDo@main/feature/calendar/src/main/assets/calendar/holidays-fa.json"),
            CatalogSource(
                url = "https://api.github.com/repos/marble098/MarbleDo/contents/feature/calendar/src/main/assets/calendar/holidays-fa.json?ref=main",
                accept = "application/vnd.github.raw+json",
            ),
        )

        /** Kept visible for diagnostics and tests without exposing the HTTP connection details. */
        val CATALOG_URLS: List<String> = CATALOG_SOURCES.map(CatalogSource::url)
    }
}
