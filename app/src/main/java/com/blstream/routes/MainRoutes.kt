package com.blstream.routes
import androidx.navigation.NavController

sealed class MainRoutes() {
    data object Host: MainRoutes(){
        fun NavController.toHost() = navigate(route = "host_view")
    }

    data object Receiver: MainRoutes(){
        fun NavController.toReceiver() = navigate(route = "receiver_view")
    }

}