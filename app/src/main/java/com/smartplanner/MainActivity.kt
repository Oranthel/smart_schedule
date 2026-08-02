package com.smartplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smartplanner.ui.navigation.SmartRoot
import com.smartplanner.ui.theme.SmartPlannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SmartPlannerApp).container
        setContent {
            SmartPlannerTheme {
                SmartRoot(container = container)
            }
        }
    }
}
