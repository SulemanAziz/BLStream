package com.blstream.views

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.blstream.routes.MainRoutes
import com.blstream.routes.MainRoutes.Receiver.toReceiver
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.MediaControllerVM
import com.example.blstream.R
import java.net.URI

@Composable
fun HostScreen(navController: NavHostController, vm: HostBluetoothPairingVM, mediavm: MediaControllerVM, appcontext: Context) {

    LaunchedEffect(vm.connectedsocket) {
        vm.connectedsocket?.let { mediavm.AttachSocket(it) }
    }

    // Android 13+ hides the streaming notification (and its controls) without this
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Log.w("BT", "Notification permission denied, streaming continues without controls")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appcontext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // .srt files have no reliable mime type on Android, so accept anything and let the parser decide
    val lyricsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        Log.d("MediaPicker", "Selected lyrics URI: $uri")
        mediavm.SelectLyrics(uri) // null = no lyrics for this song
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) { //Something's been selected
            Log.d("MediaPicker", "Selected audio URI: $uri")
            mediavm.SelectSong(uri)
            lyricsPickerLauncher.launch("*/*")
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings Icon") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { /* Navigate or switch view */ }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Music Icon") },
                    label = { Text("Music") },
                    selected = true,
                    onClick = { /* Navigate or switch view */ }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_devices),
                            contentDescription = "Devices Icon",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Devices") },
                    selected = false,
                    onClick = {
                        with(MainRoutes.Host){
                            navController.toHostPairing()
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Top Section placeholders based on the sketch lines
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .border(2.dp, Color.DarkGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Now Playing: ${if (mediavm.songname.isEmpty()) "-" else mediavm.songname}",
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1
                )
            }
            Text(
                text = when {
                    mediavm.currentsong == null -> ""
                    mediavm.currentlyrics == null -> "No lyrics"
                    mediavm.lyricslinecount > 0 -> "Lyrics: ${mediavm.lyricsname} (${mediavm.lyricslinecount} lines)"
                    else -> "Lyrics: ${mediavm.lyricsname}"
                },
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Connection + receiver state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mediavm.islinkup) "Receiver: ${mediavm.receiverstate}" else "Receiver: Disconnected",
                    color = if (mediavm.islinkup) Color.Unspecified else Color(0xFFE57373)
                )
                if (!mediavm.islinkup && vm.lastdevice != null) {
                    Button(onClick = { vm.Reconnect() }, enabled = !vm.isconnecting) {
                        Text(if (vm.isconnecting) "Reconnecting..." else "Reconnect")
                    }
                }
            }
            if (mediavm.isstreaming) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { mediavm.transferprogress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Sent ${(mediavm.transferprogress * 100).toInt()}%  -  Playing at ${FormatTime(mediavm.receiverpositionms)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            val status = mediavm.statusmessage.ifEmpty { if (!mediavm.islinkup) vm.connectionstatus else "" }
            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    fontSize = 14.sp,
                    color = Color(0xFFE57373),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Add new Song Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mediavm.isstreaming) {
                        FloatingActionButton(
                            onClick = { mediavm.StopSong() },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_stop), contentDescription = "Stop Stream")
                        }
                    }
                    Text(
                        text = when {
                            mediavm.issongloading -> "Preparing..."
                            mediavm.issongplaying -> "Pause"
                            mediavm.isstreaming -> "Resume"
                            else -> "Start Stream"
                        },
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    FloatingActionButton(
                        onClick = {
                            if (mediavm.issongplaying) mediavm.PauseSong() else mediavm.PlaySong()
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (mediavm.issongplaying) {
                            Icon(painter = painterResource(id = R.drawable.ic_pause), contentDescription = "Pause Stream")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Stream")
                        }
                    }
                }
                Spacer(Modifier.padding(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add new Song",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    FloatingActionButton(
                        onClick = {
                            audioPickerLauncher.launch("audio/*")
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Song")
                    }
                }
            }
        }
    }
}

@Preview(showBackground=true)
@Composable
fun LayoutPreview(){
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings Icon") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { /* Navigate or switch view */ }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Music Icon") },
                    label = { Text("Music") },
                    selected = true,
                    onClick = { /* Navigate or switch view */ }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_devices),
                            contentDescription = "Devices Icon",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Devices") },
                    selected = false,
                    onClick = {

                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Top Section placeholders based on the sketch lines
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .border(2.dp, Color.DarkGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(text = "Now Playing: -", modifier = Modifier.padding(start = 12.dp))
            }
            Spacer(Modifier.padding(250.dp))
            // Add new Song Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start Stream",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    FloatingActionButton(
                        onClick = {

                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Stream")
                    }
                }
                Spacer(Modifier.padding(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add new Song",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    FloatingActionButton(
                        onClick = {

                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Song")
                    }
                }
            }
        }
    }
}