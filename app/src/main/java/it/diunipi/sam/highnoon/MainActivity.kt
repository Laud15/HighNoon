package it.diunipi.sam.highnoon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import it.diunipi.sam.highnoon.ui.DuelScreen
import it.diunipi.sam.highnoon.ui.theme.HighNoonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HighNoonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DuelScreen(modifier = Modifier.padding(innerPadding));
                }
            }
        }
    }
}