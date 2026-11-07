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

/** The bundled seed and the cache are usable even when every remote source is unavailable. */
enum class OccasionSource { BUNDLED, CACHED, NETWORK }

enum class OccasionRefreshResult { UPDATED, UNCHANGED, SKIPPED, OFFLINE, FAILED }

/** One online endpoint and whether it answered on the last refresh. */
data class OccasionSourceStatus(
    val id: String,
    val tier: String,
    val isCatalog: Boolean,
    val ok: Boolean,
)

data class OccasionState(
    val catalog: OccasionCatalog = OccasionCatalog.EMPTY,
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val source: OccasionSource = OccasionSource.BUNDLED,
    val lastUpdatedEpochMillis: Long? = null,
    val lastError: Boolean = false,
    val lastRefreshWasOffline: Boolean = false,
    val lastDeliveryUrl: String? = null,
    val sourceStatus: List<OccasionSourceStatus> = emptyList(),
    val crossCheck: HolidayCrossCheck = HolidayCrossCheck(),
) {
    val occasionCount: Int get() = catalog.occasions.size
    val isFromInternet: Boolean get() = source == OccasionSource.NETWORK
    /** True when the catalog carries the official lunar month table, not just arithmetic dates. */
    val hasOfficialLunarAnchors: Boolean get() = catalog.lunarAnchors != null
    val respondingSources: Int get() = sourceStatus.count { it.ok }
}

/**
 * Keeps the occasion catalog available offline and refreshes it online.
 *
 * The app ships only a cold-start seed (statutory holidays plus the official lunar month
 * table); the descriptions come from the aggregated feed that CI builds from the sources in
 * `calendar_sources/registry.json`. Several delivery paths for that feed are tried in order,
 * and three independent community holiday lists are fetched directly as a cross-check, so a
 * single mirror outage or a single wrong calendar never decides what the app shows.
 */
class OccasionRepository(private val context: Context) {
    private val _state = MutableStateFlow(OccasionState())
    val state: StateFlow<OccasionState> = _state.asStateFlow()

    private val loadMutex = Mutex()
    private val refreshMutex = Mutex()
    @Volatile private var loaded = false
    @Volatile private var lastAutoRefreshAttemptMillis = 0L

    suspend fun load() {
        loadMutex.lock()
        try {
            if (loaded) return
            val bundled = readAsset()
            val cached = readCache()?.takeIf { CatalogUpdatePolicy.accepts(it.first, bundled ?: OccasionCatalog.EMPTY) }
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

    /** Fetches the aggregated feed from its mirrors and re-checks it against independent lists. */
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

            var result = OccasionRefreshResult.FAILED
            for (source in OccasionFeeds.catalogMirrors) {
                val body = download(source.url, source.accept) ?: continue
                val parsed = withContext(Dispatchers.Default) { OccasionCatalog.parse(body) } ?: continue
                // Reject short, partial, or accidentally returned error documents before they can
                // replace the much richer offline catalog.
                if (!CatalogUpdatePolicy.accepts(parsed, _state.value.catalog)) continue

                val changed = parsed != _state.value.catalog
                val downloadedAt = System.currentTimeMillis()
                val cachedAt = writeCache(body) ?: downloadedAt
                _state.value = _state.value.copy(
                    catalog = parsed,
                    isLoaded = true,
                    source = OccasionSource.NETWORK,
                    lastDeliveryUrl = source.url,
                    lastUpdatedEpochMillis = cachedAt,
                    lastError = false,
                    lastRefreshWasOffline = false,
                )
                loaded = true
                result = if (changed) OccasionRefreshResult.UPDATED else OccasionRefreshResult.UNCHANGED
                break
            }

            val dayFeeds = crossCheck()
            val statuses = buildList {
                addAll(OccasionFeeds.catalogMirrors.map { source ->
                    OccasionSourceStatus(source.id, source.tier.key, true, source.url == _state.value.lastDeliveryUrl)
                })
                addAll(dayFeeds.statuses)
            }
            _state.value = _state.value.copy(
                isRefreshing = false,
                lastError = result == OccasionRefreshResult.FAILED,
                sourceStatus = statuses,
                crossCheck = dayFeeds.check,
            )
            return result
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
        if (lastAutoRefreshAttemptMillis > 0L && now >= lastAutoRefreshAttemptMillis &&
            now - lastAutoRefreshAttemptMillis < AUTO_REFRESH_COOLDOWN_MILLIS
        ) {
            return OccasionRefreshResult.SKIPPED
        }
        lastAutoRefreshAttemptMillis = now
        return refresh()
    }

    private data class DayFetch(val check: HolidayCrossCheck, val statuses: List<OccasionSourceStatus>)

    /**
     * Reads the independent holiday lists for the current Jalali year directly from the internet
     * and compares them with the catalog's national holidays.
     */
    private suspend fun crossCheck(): DayFetch {
        val year = PersianDateUtils.today().year
        val parsed = mutableListOf<Set<Int>>()
        val statuses = mutableListOf<OccasionSourceStatus>()
        for (source in OccasionFeeds.holidayDayFeeds(year)) {
            val body = download(source.url, source.accept)
            val days = body?.let { withContext(Dispatchers.Default) { HolidayDayParser.parse(it) } }.orEmpty()
            if (days.isNotEmpty()) parsed.add(days)
            statuses.add(OccasionSourceStatus(source.id, source.tier.key, false, days.isNotEmpty()))
        }
        val index = OccasionIndex.build(
            catalog = _state.value.catalog,
            jalaliYears = listOf(year),
            lunarOffsetDays = 0,
        )
        val nationalDays = index.forYear(year).filter { it.occasion.isHoliday }.map { it.month * 100 + it.day }.toSet()
        return DayFetch(HolidayCrossChecker.check(nationalDays, parsed), statuses)
    }

    private fun hasInternetConnection(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun readAsset(): OccasionCatalog? {
        cachedBundledCatalog?.let { return it }
        return withContext(Dispatchers.IO) {
            cachedBundledCatalog ?: runCatching {
                context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
            }.getOrNull()?.let(OccasionCatalog::parse)?.also { cachedBundledCatalog = it }
        }
    }

    private suspend fun readCache(): Pair<OccasionCatalog, Long>? = withContext(Dispatchers.IO) {
        val file = cacheFile()
        if (!file.exists()) return@withContext null
        val mtime = file.lastModified()
        val path = file.absolutePath
        if (cachedDiskPath == path && cachedDiskMtime == mtime && cachedDiskCatalog != null) {
            return@withContext cachedDiskCatalog!! to mtime
        }
        val text = runCatching { file.bufferedReader().use(::readBounded) }.getOrNull() ?: return@withContext null
        OccasionCatalog.parse(text)?.also { parsed ->
            cachedDiskPath = path
            cachedDiskMtime = mtime
            cachedDiskCatalog = parsed
        }?.let { it to mtime }
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

    private suspend fun download(url: String, accept: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", accept)
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

    companion object {
        const val CACHE_DIRECTORY = "occasions"
        const val CATALOG_FILE_NAME = "occasions-catalog.json"
        const val CATALOG_ASSET = "calendar/occasions-v6-fa.json"
        private const val CONNECT_TIMEOUT_MILLIS = 3_000
        private const val READ_TIMEOUT_MILLIS = 4_000
        private const val MAX_CATALOG_CHARS = 3_000_000
        private const val DEFAULT_MAX_AGE_MILLIS = 20L * 60L * 60L * 1000L
        private const val AUTO_REFRESH_COOLDOWN_MILLIS = 30L * 60L * 1000L
        private const val USER_AGENT = "MarbleDo-Android"

        @Volatile private var cachedBundledCatalog: OccasionCatalog? = null
        @Volatile private var cachedDiskPath: String? = null
        @Volatile private var cachedDiskMtime: Long = -1L
        @Volatile private var cachedDiskCatalog: OccasionCatalog? = null

        /**
         * The delivery paths for the aggregated feed. The independent community day lists are
         * read as a cross-check only and are listed by [OccasionFeeds.holidayDayFeeds].
         */
        val CATALOG_URLS: List<String>
            get() = OccasionFeeds.catalogMirrors.map(RemoteOccasionFeed::url)
    }
}
