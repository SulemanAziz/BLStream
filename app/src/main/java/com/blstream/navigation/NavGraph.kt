package com.blstream.navigation

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.widget.MediaController
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavArgument
import androidx.navigation.NavArgumentBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blstream.MainScreen
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.MediaControllerVM
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.views.HostPairingScreen
import com.blstream.views.HostScreen
import com.blstream.views.ReceiverPairingScreen
import com.blstream.views.ReceiverScreen

val mediavm = MediaControllerVM()
@Composable
fun AppNavigation(Receivervm: ReceiverBluetoothPairingVM, Hostvm: HostBluetoothPairingVM, appcontext: Context) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            MainScreen(navController)
        }

        composable("host_view") {
            HostScreen(navController, Hostvm, mediavm, appcontext)
        }

        composable(route = "receiver_view",) {
            ReceiverScreen(navController, Receivervm, mediavm)
        }

        composable("host_pairing") {
            HostPairingScreen(vm = Hostvm, appcontext = appcontext, navcontroller = navController)
        }

        composable("receiver_pairing") {
            ReceiverPairingScreen(vm = Receivervm, navController = navController)
        }
    }
}