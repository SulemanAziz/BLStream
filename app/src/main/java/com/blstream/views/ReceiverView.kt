package com.blstream.views

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.example.blstream.R
import androidx.compose.runtime.collectAsState
import com.blstream.viewmodels.MediaControllerVM

@SuppressLint("MissingPermission")
@Composable
fun ReceiverScreen(navController: NavController? = null, vm: ReceiverBluetoothPairingVM, mediavm: MediaControllerVM) {

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
            Text(text = "Connected", color = Color.Gray, fontWeight = FontWeight.Medium) //To do, make this state dynamic
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Now Playing Title
        Text(
            text = "Device: ${vm.connectedsocket!!.remoteDevice.name}",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Center Box: Album Art / Lyrics Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(2.dp, Color.DarkGray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Hold Song lyrics,\notherwise show album art.",
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom: Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_stop),
                contentDescription = "Stop Playback",
                modifier = Modifier.size(36.dp)
            )

            // Play/Pause Button
            IconButton(onClick = {  }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(56.dp))
            }

            // Toggle Lyrics Button
            IconButton(onClick = {  }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show/Disable Lyrics", modifier = Modifier.size(36.dp))
            }
        }
    }
}