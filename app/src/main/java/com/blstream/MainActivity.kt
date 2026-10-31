package com.blstream

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.example.blstream.ui.theme.BLStreamTheme
import com.blstream.navigation.AppNavigation
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.MediaControllerVM
import com.blstream.viewmodels.MediaViewModelFactory
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.viewmodels.ReceiverMediaVM
import com.blstream.viewmodels.ReceiverViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewModelProvider so the connection survives rotation
        val Hostvm = ViewModelProvider(this)[HostBluetoothPairingVM::class.java]
        val Receivervm = ViewModelProvider(this, ReceiverViewModelFactory(applicationContext))[ReceiverBluetoothPairingVM::class.java]
        val mediafactory = MediaViewModelFactory(applicationContext)
        val Mediavm = ViewModelProvider(this, mediafactory)[MediaControllerVM::class.java]
        val Receivermediavm = ViewModelProvider(this, mediafactory)[ReceiverMediaVM::class.java]
        enableEdgeToEdge()
        setContent {
            BLStreamTheme(
                darkTheme = true
            ) {
                val appcontext: Context = LocalContext.current;
                AppNavigation(Receivervm, Hostvm, Mediavm, Receivermediavm, appcontext)
            }
        }
    }
}
