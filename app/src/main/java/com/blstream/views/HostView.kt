package com.blstream.views

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.blstream.navigation.mediavm
import com.blstream.routes.MainRoutes
import com.blstream.routes.MainRoutes.Receiver.toReceiver
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.MediaControllerVM
import com.example.blstream.R
import java.net.URI

suspend fun ManagePlayback(){

}
@Composable
fun HostScreen(navController: NavHostController, vm: HostBluetoothPairingVM, mediavm: MediaControllerVM, appcontext: Context) {
    var songtitle by remember { mutableStateOf("") }
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) { //Something's been selected
            Log.d("MediaPicker", "Selected audio URI: $uri")
            mediavm.currentsong = uri
        }
    }
    LaunchedEffect(mediavm.currentsong) {
        mediavm.currentsong?.let { uri ->
            appcontext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        songtitle = cursor.getString(nameIndex)
                    }
                }
            }
        } ?: run {
            songtitle = "None Selected"
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
                Text(text = "Now Playing: ${songtitle}", modifier = Modifier.padding(start = 12.dp))
            }

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
                            mediavm.LoadSong()
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
                            audioPickerLauncher.launch("audio/mpeg")
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
                Text(text = "Now Playing: ${mediavm.currentsong?.path}", modifier = Modifier.padding(start = 12.dp))
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
