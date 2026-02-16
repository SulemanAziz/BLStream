package com.blstream.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blstream.MainScreen
import com.blstream.views.HostPairingScreen
import com.blstream.views.HostScreen
import com.blstream.views.ReceiverPairingScreen
import com.blstream.views.ReceiverScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            MainScreen(navController)
        }

        composable("host_view") {
            HostScreen()
        }

        composable("receiver_view") {
            ReceiverScreen()
        }

        composable("host_pairing"){
            HostPairingScreen()
        }

        composable("receiver_pairing"){
            ReceiverPairingScreen()
        }
    }
}