package com.blstream.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blstream.MainScreen
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.ReceiverBluetoothPairingVM
import com.blstream.views.HostPairingScreen
import com.blstream.views.HostScreen
import com.blstream.views.ReceiverPairingScreen
import com.blstream.views.ReceiverScreen

@Composable
fun AppNavigation(Receivervm: ReceiverBluetoothPairingVM, Hostvm: HostBluetoothPairingVM, appcontext: Context) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            MainScreen(navController)
        }

        composable("host_view") {

        }

        composable("receiver_view") {

        }

        composable("host_pairing"){
            HostPairingScreen(vm = Hostvm, appcontext = appcontext)
        }

        composable("receiver_pairing"){
            ReceiverPairingScreen(vm = Receivervm, appcontext = appcontext)
        }
    }
}