package com.wpinrui.snapmath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.wpinrui.snapmath.navigation.NavGraph
import com.wpinrui.snapmath.ui.theme.SnapmathTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapmathTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}