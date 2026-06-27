package it.diunipi.sam.highnoon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun LobbyScreen(modifier: Modifier = Modifier) {
    // STATO: la frase di stato corrente del duello.
    // 'remember' + 'mutableStateOf' = il pattern della Lez. 09.
    // 'by' è la proprieta' delegata (Lez. 09): leggiamo/scriviamo 'statusText'
    // come una normale variabile, ma dietro c'e' il getter/setter di mutableStateOf.
    var statusText by remember { mutableStateOf("waiting for the start") }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Titolo del gioco
        Text(
            text = "HIGH NOON",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sottotitolo
        Text(
            text = "Gunslinger duel",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Stato corrente: questo Text LEGGE 'statusText'.
        // Per la logica publish/subscribe della Lez. 09, questa lettura
        // "abbona" il Text ai cambiamenti: se statusText cambia, SOLO
        // questa parte viene ridisegnata (recomposing).
        Text(
            text = statusText,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // EVENTO: il bottone. onClick e' una lambda (Lez. 09).
        // Premendolo MODIFICHIAMO lo stato -> scatta il recomposing.
        Button(onClick = {
            statusText = "Looking for an opponent..."
        }) {
            Text(text = "Find an opponent")
        }
    }
}