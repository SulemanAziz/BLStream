package com.blstream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blstream.routes.MainRoutes
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.blstream.routes.MainRoutes.Receiver.toReceiver

@Composable
fun MainScreen(
    navController: NavController
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Mode",
                fontSize = 50.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .border(
                        shape = CircleShape,
                        width = 4.dp,
                        color = Color.Black
                    ),
                onClick = {
                    with(MainRoutes.Host){
                        navController.toHost()
                    }
                }) {
                Text(
                    text = "Host"
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        shape = CircleShape,
                        width = 4.dp,
                        color = Color.Black,
                    ),
                onClick = {
                    with(MainRoutes.Host){
                        navController.toReceiver()
                    }
                }) {
                Text(
                    text = "Receiver"
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Info bla bla bla", fontSize = 15.sp)
        }
    }
}