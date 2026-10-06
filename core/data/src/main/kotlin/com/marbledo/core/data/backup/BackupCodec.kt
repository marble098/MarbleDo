package com.marbledo.core.data.backup

import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val tasks: List<Task> = emptyList(),
) {
    companion object { const val CURRENT_SCHEMA = 1 }
}

object BackupCodec {
    private const val MAGIC = "MARBLEDO"
    private const val ITERATIONS = 210_000
    private const val MAX_COMPRESSED_BYTES = 64 * 1024 * 1024
    private const val MAX_JSON_BYTES = 128 * 1024 * 1024
    private val random = SecureRandom()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; prettyPrint = true }

    fun encode(
        tasks: List<Task>,
        settings: AppSettings,
        passphrase: CharArray? = null,
    ): ByteArray {
        require(passphrase == null || passphrase.size >= 8) { "Use a passphrase of at least eight characters" }
        val envelope = BackupEnvelope(tasks = tasks, settings = settings)
        val jsonBytes = json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
        require(jsonBytes.size <= MAX_JSON_BYTES) { "Backup is too large" }
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(jsonBytes) }
            output.toByteArray()
        }
        require(compressed.size <= MAX_COMPRESSED_BYTES) { "Compressed backup is too large" }
        val salt = if (passphrase != null) ByteArray(16).also(random::nextBytes) else byteArrayOf()
        val nonce = if (passphrase != null) ByteArray(12).also(random::nextBytes) else byteArrayOf()
        val payload = if (passphrase != null) encrypt(compressed, passphrase, salt, nonce) else compressed
        val mode = if (passphrase == null) "plain" else "aesgcm"
        val checksum = sha256(payload)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = listOf(
            MAGIC,
            BackupEnvelope.CURRENT_SCHEMA.toString(),
            mode,
            checksum,
            if (salt.isEmpty()) "-" else encoder.encodeToString(salt),
            if (nonce.isEmpty()) "-" else encoder.encodeToString(nonce),
        ).joinToString("|") + "\n"
        return header.toByteArray(Charsets.US_ASCII) + payload
    }

    fun isEncrypted(bytes: ByteArray): Boolean {
        val newline = bytes.indexOf('\n'.code.toByte())
        if (newline !in 1..1024) return false
        return String(bytes, 0, newline, Charsets.US_ASCII).split('|').getOrNull(2) == "aesgcm"
    }

    fun decode(bytes: ByteArray, passphrase: CharArray? = null): BackupEnvelope {
        require(bytes.size <= MAX_COMPRESSED_BYTES + 4096) { "Backup file is too large" }
        val newline = bytes.indexOf('\n'.code.toByte())
        require(newline in 1..1024) { "Backup header is missing or invalid" }
        val parts = String(bytes, 0, newline, Charsets.US_ASCII).split('|')
        require(parts.size == 6 && parts[0] == MAGIC) { "This is not a MarbleDo backup" }
        val headerSchema = parts[1].toIntOrNull() ?: error("Invalid backup version")
        require(headerSchema in 1..BackupEnvelope.CURRENT_SCHEMA) { "Unsupported backup version: $headerSchema" }
        val payload = bytes.copyOfRange(newline + 1, bytes.size)
        require(MessageDigest.isEqual(sha256(payload).toByteArray(Charsets.US_ASCII), parts[3].toByteArray(Charsets.US_ASCII))) { "Backup checksum does not match" }
        val decoder = Base64.getUrlDecoder()
        val compressed = when (parts[2]) {
            "plain" -> payload
            "aesgcm" -> {
                val secret = passphrase ?: error("This backup is encrypted; enter its passphrase")
                decrypt(payload, secret, decoder.decode(parts[4]), decoder.decode(parts[5]))
            }
            else -> error("Unknown backup encryption mode")
        }
        require(compressed.size <= MAX_COMPRESSED_BYTES) { "Compressed backup is too large" }
        val jsonBytes = GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_JSON_BYTES) { "Decompressed backup exceeds the safety limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val envelope = json.decodeFromString(BackupEnvelope.serializer(), jsonBytes.toString(Charsets.UTF_8))
        require(envelope.schemaVersion == headerSchema && envelope.schemaVersion in 1..BackupEnvelope.CURRENT_SCHEMA) { "Unsupported or inconsistent backup schema" }
        require(envelope.tasks.all { it.title.isNotBlank() }) { "Backup contains a task without a title" }
        return envelope
    }

    private fun encrypt(input: ByteArray, passphrase: CharArray, salt: ByteArray, nonce: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, passphrase, salt, nonce).doFinal(input)

    private fun decrypt(input: ByteArray, passphrase: CharArray, salt: ByteArray, nonce: ByteArray): ByteArray =
        try {
            cipher(Cipher.DECRYPT_MODE, passphrase, salt, nonce).doFinal(input)
        } catch (exception: Exception) {
            throw IOException("Incorrect passphrase or damaged encrypted backup", exception)
        }

    private fun cipher(mode: Int, passphrase: CharArray, salt: ByteArray, nonce: ByteArray): Cipher {
        require(salt.size == 16 && nonce.size == 12) { "Invalid encryption parameters" }
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
            keyBytes.fill(0)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
