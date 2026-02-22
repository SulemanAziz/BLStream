package com.blstream.views

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import com.blstream.routes.MainRoutes
import com.blstream.routes.MainRoutes.Receiver.toReceiver
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.viewmodels.ReceiverViewModelFactory
import androidx.compose.runtime.collectAsState


@Composable
fun ReceiverPairingScreen(vm: ReceiverBluetoothPairingVM, navController: NavController) {

    LaunchedEffect(vm.connectedsocket) {
        if (vm.connectedsocket!=null) {
            with(MainRoutes.Receiver){
                navController.toReceiver()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(
        ) {
            Button(
                onClick = {
                    vm.listen=true
                    vm.AcceptThread().start()
                }
            ) {
                Text("Start Listening")
            }

            Button(
                onClick = {
                    vm.listen=false
                    vm.AcceptThread().cancel()
                }
            ) {
                Text("Stop Listening")
            }
        }
    }
}