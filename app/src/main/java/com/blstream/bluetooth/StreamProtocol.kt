package com.blstream.bluetooth

import java.nio.ByteBuffer

// Shared between host and receiver so both sides agree on the payload layouts
object StreamProtocol {
    const val CHUNK_SIZE = 4 * 1024
    const val WINDOW_SIZE = 64 * 1024L // Host never gets further than this ahead of the receiver's ack
    const val ACK_EVERY = 16 * 1024L
    const val ACK_TIMEOUT_MS = 3000L
    const val MAX_LYRICS_SIZE = FrameCodec.MAX_PAYLOAD - 8

    class SongInfo(val sessionid: Long, val size: Long, val durationms: Long, val crc: Long, val name: String)

    fun EncodeSongInfo(info: SongInfo): ByteArray {
        val name = info.name.toByteArray(Charsets.UTF_8).take(255).toByteArray()
        return ByteBuffer.allocate(8 * 4 + 4 + name.size)
            .putLong(info.sessionid).putLong(info.size).putLong(info.durationms).putLong(info.crc)
            .putInt(name.size).put(name)
            .array()
    }

    fun DecodeSongInfo(payload: ByteArray): SongInfo? {
        if (payload.size < 36) return null
        val buffer = ByteBuffer.wrap(payload)
        val sessionid = buffer.long
        val size = buffer.long
        val durationms = buffer.long
        val crc = buffer.long
        val namelength = buffer.int
        if (namelength < 0 || namelength > buffer.remaining() || size <= 0) return null
        val name = ByteArray(namelength).also { buffer.get(it) }
        return SongInfo(sessionid, size, durationms, crc, String(name, Charsets.UTF_8))
    }

    // Every session scoped frame starts with the session id, so stale frames can be ignored
    fun WithSession(sessionid: Long, data: ByteArray = ByteArray(0), length: Int = data.size): ByteArray {
        return ByteBuffer.allocate(8 + length).putLong(sessionid).put(data, 0, length).array()
    }

    fun SessionOf(payload: ByteArray): Long? = if (payload.size >= 8) ByteBuffer.wrap(payload).long else null
}
