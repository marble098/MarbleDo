package com.marbledo.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<MarbleDoSettingsProto> {
    override val defaultValue: MarbleDoSettingsProto = MarbleDoSettingsProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): MarbleDoSettingsProto = try {
        MarbleDoSettingsProto.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Unable to decode MarbleDo settings", exception)
    }

    override suspend fun writeTo(t: MarbleDoSettingsProto, output: OutputStream) {
        t.writeTo(output)
    }
}
