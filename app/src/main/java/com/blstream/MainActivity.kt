package com.blstream

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import com.example.blstream.ui.theme.BLStreamTheme
import com.blstream.navigation.AppNavigation
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.viewmodels.ReceiverViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val Hostvm = HostBluetoothPairingVM()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BLStreamTheme(
                darkTheme = true
            ) {
                val appcontext: Context = LocalContext.current;
                val Receivervm = ViewModelProvider(this,ReceiverViewModelFactory(appcontext))[ReceiverBluetoothPairingVM::class.java]
                AppNavigation(Receivervm, Hostvm, appcontext)
            }
        }
    }
}
