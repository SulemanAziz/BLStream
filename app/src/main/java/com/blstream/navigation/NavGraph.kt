package com.blstream.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blstream.MainScreen
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.MediaControllerVM
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.viewmodels.ReceiverMediaVM
import com.blstream.views.HostPairingScreen
import com.blstream.views.HostScreen
import com.blstream.views.ReceiverPairingScreen
import com.blstream.views.ReceiverScreen

@Composable
fun AppNavigation(Receivervm: ReceiverBluetoothPairingVM, Hostvm: HostBluetoothPairingVM, mediavm: MediaControllerVM, receivermediavm: ReceiverMediaVM, appcontext: Context) {
    val navController = rememberNavController()
    // Reopened from the notification while the host is still streaming, go straight back to it
    val start = if (mediavm.islinkup || mediavm.isstreaming) "host_view" else "home"

    NavHost(navController = navController, startDestination = start) {

        composable("home") {
            MainScreen(navController)
        }

        composable("host_view") {
            HostScreen(navController, Hostvm, mediavm, appcontext)
        }

        composable(route = "receiver_view",) {
            ReceiverScreen(navController, Receivervm, receivermediavm)
        }

        composable("host_pairing") {
            HostPairingScreen(vm = Hostvm, appcontext = appcontext, navcontroller = navController)
        }

        composable("receiver_pairing") {
            ReceiverPairingScreen(vm = Receivervm, appcontext = appcontext, navController = navController)
        }
    }
}
