package com.blstream.views

import android.annotation.SuppressLint
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.blstream.bluetooth.FrameType
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.viewmodels.ReceiverMediaVM
import com.example.blstream.R

@SuppressLint("MissingPermission")
@Composable
fun ReceiverScreen(navController: NavController? = null, vm: ReceiverBluetoothPairingVM, mediavm: ReceiverMediaVM) {

    var showlyrics by remember { mutableStateOf(true) }

    LaunchedEffect(vm.connectedsocket) {
        vm.connectedsocket?.let { mediavm.AttachSocket(it) }
    }
    // Lost the host, go back to listening so it can reconnect and resume
    LaunchedEffect(mediavm.linklost) {
        if (mediavm.linklost) vm.StartListening()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top: Pairing Status Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Pairing Status:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                text = when {
                    mediavm.islinkup -> "Connected"
                    vm.islistening -> "Disconnected, waiting for host"
                    else -> "Disconnected"
                },
                color = if (mediavm.islinkup) Color.Gray else Color(0xFFE57373),
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Now Playing Title
        Text(
            text = "Device: ${vm.connecteddevicename}",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = if (mediavm.songname.isEmpty()) "Nothing playing" else mediavm.songname,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.Start)
        )
        if (mediavm.statusmessage.isNotEmpty()) {
            Text(text = mediavm.statusmessage, color = Color(0xFFE57373), fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Box: Lyrics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(2.dp, Color.DarkGray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            LyricsPanel(mediavm, showlyrics)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback progress, the track behind it shows how much has been buffered
        Box(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { mediavm.bufferedfraction },
                modifier = Modifier.fillMaxWidth(),
                color = Color.DarkGray
            )
            LinearProgressIndicator(
                progress = { if (mediavm.durationms > 0) (mediavm.positionms.toFloat() / mediavm.durationms).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = Color.Transparent
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = FormatTime(mediavm.positionms), fontSize = 12.sp, color = Color.Gray)
            Text(text = mediavm.playbackstate.label, fontSize = 12.sp, color = Color.Gray)
            Text(text = FormatTime(mediavm.durationms), fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom: Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { mediavm.RequestControl(FrameType.STOP) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stop),
                    contentDescription = "Stop Playback",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play/Pause Button
            IconButton(onClick = { mediavm.TogglePlayback() }, modifier = Modifier.size(64.dp)) {
                if (mediavm.playbackstate == ReceiverMediaVM.PlaybackState.PLAYING) {
                    Icon(painter = painterResource(id = R.drawable.ic_pause), contentDescription = "Pause", modifier = Modifier.size(56.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(56.dp))
                }
            }

            // Toggle Lyrics Button
            IconButton(onClick = { showlyrics = !showlyrics }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show/Disable Lyrics", modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun LyricsPanel(mediavm: ReceiverMediaVM, showlyrics: Boolean) {
    val lyrics = mediavm.lyrics
    val cues = lyrics.cues
    when {
        !showlyrics || mediavm.songname.isEmpty() -> Text(
            text = if (mediavm.songname.isEmpty()) "Waiting for the host to start a song" else "Lyrics hidden",
            color = Color.Gray,
            modifier = Modifier.padding(16.dp)
        )
        cues.isEmpty() -> Text(text = "No lyrics for this song", color = Color.Gray, modifier = Modifier.padding(16.dp))
        else -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = cues.getOrNull(lyrics.currentindex)?.text ?: "♪",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = cues.getOrNull(lyrics.nextindex)?.text ?: "",
                fontSize = 18.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun FormatTime(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
