package com.blstream.views

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.viewmodels.ReceiverViewModelFactory
@Composable
fun ReceiverScreen() {
    Text(text = "Welcome to the Receiver Screen!")

}