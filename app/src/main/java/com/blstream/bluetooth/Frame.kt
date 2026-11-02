package com.blstream.bluetooth

import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

// Wire format: [magic 2][type 1][seq 4][offset 8][length 4][payload][crc32 4]
// The crc covers everything from type to the end of the payload
object FrameType {
    const val HELLO: Byte = 1
    const val HELLO_ACK: Byte = 2
    const val PING: Byte = 3

    const val SONG_INFO: Byte = 10 // Host -> Receiver, starts or resumes a session
    const val READY: Byte = 11 // Receiver -> Host, "send from this offset"
    const val LYRICS: Byte = 12
    const val AUDIO_CHUNK: Byte = 13
    const val ACK: Byte = 14 // offset = bytes received contiguously
    const val RESEND: Byte = 15 // offset = resend from here

    const val PLAY: Byte = 20
    const val PAUSE: Byte = 21
    const val STOP: Byte = 22
    const val CONTROL_REQUEST: Byte = 23 // Receiver asks the host to play/pause/stop
    const val STATUS: Byte = 24 // Receiver -> Host, playback state + position
}

class Frame(val type: Byte, val offset: Long = 0, val payload: ByteArray = ByteArray(0), val seq: Int = 0)

class CorruptFrameException(message: String) : IOException(message)

object FrameCodec {
    const val MAGIC_1 = 0xB5
    const val MAGIC_2 = 0x7A
    const val HEADER_SIZE = 1 + 4 + 8 + 4
    const val MAX_PAYLOAD = 512 * 1024
}

class FrameWriter(outputStream: OutputStream) {
    private val out = DataOutputStream(BufferedOutputStream(outputStream, 8 * 1024))
    private var seq = 0

    @Synchronized
    @Throws(IOException::class)
    fun Write(type: Byte, offset: Long = 0, payload: ByteArray = ByteArray(0)) {
        require(payload.size <= FrameCodec.MAX_PAYLOAD) { "Payload too large: ${payload.size}" }
        val header = ByteBuffer.allocate(FrameCodec.HEADER_SIZE)
            .put(type)
            .putInt(seq++)
            .putLong(offset)
            .putInt(payload.size)
            .array()
        val crc = CRC32()
        crc.update(header)
        crc.update(payload)

        out.write(FrameCodec.MAGIC_1)
        out.write(FrameCodec.MAGIC_2)
        out.write(header)
        out.write(payload)
        out.writeInt(crc.value.toInt())
        out.flush()
    }
}

class FrameReader(inputStream: InputStream) {
    // No extra buffering here, so a later reader on the same stream doesn't lose bytes
    private val input = DataInputStream(inputStream)

    // Blocks until a frame arrives. Throws CorruptFrameException when a frame fails its checks,
    // the next call scans forward to the next magic marker so the stream resyncs on its own
    @Throws(IOException::class)
    fun Read(): Frame {
        SyncToMagic()
        val header = ByteArray(FrameCodec.HEADER_SIZE)
        input.readFully(header)
        val buffer = ByteBuffer.wrap(header)
        val type = buffer.get()
        val seq = buffer.int
        val offset = buffer.long
        val length = buffer.int
        if (length < 0 || length > FrameCodec.MAX_PAYLOAD) {
            throw CorruptFrameException("Bad payload length $length")
        }

        val payload = ByteArray(length)
        input.readFully(payload)
        val expected = input.readInt()
        val crc = CRC32()
        crc.update(header)
        crc.update(payload)
        if (crc.value.toInt() != expected) {
            throw CorruptFrameException("Checksum mismatch on frame $seq")
        }
        return Frame(type, offset, payload, seq)
    }

    private fun SyncToMagic() {
        var previous = -1
        while (true) {
            val current = input.read()
            if (current == -1) throw EOFException("Stream closed")
            if (previous == FrameCodec.MAGIC_1 && current == FrameCodec.MAGIC_2) return
            previous = current
        }
    }
}

object Handshake {
    const val PROTOCOL_VERSION = 1
    const val TIMEOUT_MS = 5000L
    const val ACCEPTED: Byte = 0
    const val REJECTED_VERSION: Byte = 1
    private val APP_ID = "BLSTREAM".toByteArray(Charsets.US_ASCII)

    fun Hello(): ByteArray = ByteBuffer.allocate(APP_ID.size + 4)
        .put(APP_ID)
        .putInt(PROTOCOL_VERSION)
        .array()

    fun HelloAck(status: Byte): ByteArray = ByteBuffer.allocate(APP_ID.size + 5)
        .put(APP_ID)
        .putInt(PROTOCOL_VERSION)
        .put(status)
        .array()

    // Is this peer running BLStream at all
    fun IsFromApp(frame: Frame): Boolean {
        if (frame.payload.size < APP_ID.size + 4) return false
        return frame.payload.copyOfRange(0, APP_ID.size).contentEquals(APP_ID)
    }

    fun VersionOf(frame: Frame): Int = ByteBuffer.wrap(frame.payload, APP_ID.size, 4).int

    fun IsAccepted(frame: Frame): Boolean {
        return frame.type == FrameType.HELLO_ACK &&
                IsFromApp(frame) &&
                frame.payload.size == APP_ID.size + 5 &&
                frame.payload[APP_ID.size + 4] == ACCEPTED
    }
}
