package com.listenbuddy

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.listenbuddy.ui.screens.ReceiverScreen
import com.listenbuddy.ui.screens.SenderScreen
import com.listenbuddy.ui.screens.StartScreen

sealed class Screen(val route: String) {
    object Start : Screen("start")
    object Sender : Screen("sender")
    object Receiver : Screen("receiver")
}

@Composable
fun ListenBuddyApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Start.route
    ) {
        composable(Screen.Start.route) {
            StartScreen(
                onSenderClick = { navController.navigate(Screen.Sender.route) },
                onReceiverClick = { navController.navigate(Screen.Receiver.route) }
            )
        }

        composable(Screen.Sender.route) {
            SenderScreen(
                    onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Receiver.route) {
            ReceiverScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}