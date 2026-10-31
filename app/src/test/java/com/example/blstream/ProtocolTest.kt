package com.example.blstream

import com.blstream.bluetooth.CorruptFrameException
import com.blstream.bluetooth.FrameReader
import com.blstream.bluetooth.FrameType
import com.blstream.bluetooth.FrameWriter
import com.blstream.bluetooth.Handshake
import com.blstream.viewmodels.LyricsVM
import com.blstream.bluetooth.StreamProtocol
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FrameTest {
    @Test
    fun roundTrip() {
        val out = ByteArrayOutputStream()
        val writer = FrameWriter(out)
        writer.Write(FrameType.AUDIO_CHUNK, 4096, byteArrayOf(1, 2, 3))
        writer.Write(FrameType.PING)

        val reader = FrameReader(ByteArrayInputStream(out.toByteArray()))
        val first = reader.Read()
        assertEquals(FrameType.AUDIO_CHUNK, first.type)
        assertEquals(4096L, first.offset)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.payload)
        assertEquals(0, first.seq)
        assertEquals(1, reader.Read().seq)
    }

    @Test
    fun corruptFrameIsRejectedAndStreamResyncs() {
        val out = ByteArrayOutputStream()
        val writer = FrameWriter(out)
        writer.Write(FrameType.AUDIO_CHUNK, 0, ByteArray(100) { it.toByte() })
        writer.Write(FrameType.ACK, 77)
        val bytes = out.toByteArray()
        bytes[40] = (bytes[40] + 1).toByte() // Flip a payload byte in the first frame

        val reader = FrameReader(ByteArrayInputStream(bytes))
        try {
            reader.Read()
            fail("Expected checksum failure")
        } catch (e: CorruptFrameException) {}
        val next = reader.Read()
        assertEquals(FrameType.ACK, next.type)
        assertEquals(77L, next.offset)
    }

    @Test
    fun garbageBeforeFrameIsSkipped() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(9, 9, 0xB5.toByte(), 3, 7))
        FrameWriter(out).Write(FrameType.PLAY)
        assertEquals(FrameType.PLAY, FrameReader(ByteArrayInputStream(out.toByteArray())).Read().type)
    }

    @Test
    fun handshake() {
        val out = ByteArrayOutputStream()
        FrameWriter(out).Write(FrameType.HELLO_ACK, payload = Handshake.HelloAck(Handshake.ACCEPTED))
        FrameWriter(out).Write(FrameType.HELLO_ACK, payload = Handshake.HelloAck(Handshake.REJECTED_VERSION))
        val reader = FrameReader(ByteArrayInputStream(out.toByteArray()))
        assertTrue(Handshake.IsAccepted(reader.Read()))
        assertFalse(Handshake.IsAccepted(reader.Read()))
    }

    @Test
    fun songInfoRoundTrip() {
        val info = StreamProtocol.SongInfo(42L, 1_000_000L, 180_000L, 0xDEADBEEFL, "Song – ü.mp3")
        val decoded = StreamProtocol.DecodeSongInfo(StreamProtocol.EncodeSongInfo(info))!!
        assertEquals(42L, decoded.sessionid)
        assertEquals(1_000_000L, decoded.size)
        assertEquals(180_000L, decoded.durationms)
        assertEquals(0xDEADBEEFL, decoded.crc)
        assertEquals("Song – ü.mp3", decoded.name)
        assertNull(StreamProtocol.DecodeSongInfo(ByteArray(10)))
    }
}

class SrtParserTest {
    @Test
    fun parsesStandardSrt() {
        val srt = "1\n00:00:01,000 --> 00:00:02,500\nHello\nWorld\n\n2\n00:00:03,000 --> 00:00:04,000\n<i>Second</i>\n"
        val cues = LyricsVM.ParseLyrics(srt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startms)
        assertEquals(2500L, cues[0].endms)
        assertEquals("Hello\nWorld", cues[0].text)
        assertEquals("Second", cues[1].text)
    }

    @Test
    fun handlesBomCrlfDotsAndMissingBlankLines() {
        val srt = "﻿1\r\n00:00:01.5 --> 00:00:02.000\r\nOne\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nTwo\r\n"
        val cues = LyricsVM.ParseLyrics(srt)
        assertEquals(2, cues.size)
        assertEquals(1500L, cues[0].startms)
        assertEquals("One", cues[0].text)
        assertEquals("Two", cues[1].text)
    }

    @Test
    fun skipsBrokenBlocks() {
        val srt = "1\nnot a timing\nJunk\n\n2\n00:00:05,000 --> 00:00:04,000\nBackwards\n\n3\n00:00:06,000 --> 00:00:07,000\n\n4\n00:00:08,000 --> 00:00:09,000\nGood\n"
        val cues = LyricsVM.ParseLyrics(srt)
        assertEquals(1, cues.size)
        assertEquals("Good", cues[0].text)
    }

    @Test
    fun findsCueForPosition() {
        val cues = LyricsVM.ParseLyrics("1\n00:00:01,000 --> 00:00:02,000\nA\n\n2\n00:00:03,000 --> 00:00:04,000\nB\n")
        assertEquals(-1, LyricsVM.LastStartedCue(cues, 500))
        assertEquals(0, LyricsVM.LastStartedCue(cues, 1500))
        assertEquals(0, LyricsVM.LastStartedCue(cues, 2500)) // Between lines, A started but ended
        assertEquals(1, LyricsVM.LastStartedCue(cues, 9000))
    }
}
