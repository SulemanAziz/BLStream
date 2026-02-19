package com.blstream.views

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.blstream.viewmodels.ReceiverBluetoothPairingVM

@Composable
fun ReceiverPairingScreen() {
    ReceiverScreen();
    val appcontext: Context = LocalContext.current;
    val vm: ReceiverBluetoothPairingVM = ReceiverBluetoothPairingVM(appcontext);
    //Bluetooth needed here
    vm.AcceptThread().start();
}
@Composable
fun ReceiverScreen() {
    Text(text = "Welcome to the Receiver Screen!")
}