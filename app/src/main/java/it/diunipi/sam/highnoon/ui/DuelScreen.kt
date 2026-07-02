package it.diunipi.sam.highnoon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diunipi.sam.highnoon.game.DuelPhase
import it.diunipi.sam.highnoon.game.DuelViewModel

@Composable
fun DuelScreen(
    modifier: Modifier = Modifier,
    viewModel: DuelViewModel = viewModel()
) {
    // Il sensore ascolta solo mentre la schermata e' presente.
    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (viewModel.phase) {
            DuelPhase.IDLE -> {
                Text(text = "Pronto al duello?", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.startDuel() }) {
                    Text(text = "Start")
                }
            }
            DuelPhase.WAITING -> {
                Text(text = "Tieni fermo...", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estrai SOLO al segnale!",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DuelPhase.DRAW -> {
                Text(text = "DRAW!", fontSize = 64.sp, color = MaterialTheme.colorScheme.primary)
            }
            DuelPhase.RESULT -> {
                if (viewModel.falseStart) {
                    Text(text = "Falsa partenza!", fontSize = 32.sp, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(text = "Tempo di reazione", fontSize = 20.sp)
                    Text(text = "${viewModel.reactionMs} ms", fontSize = 40.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.reset() }) {
                    Text(text = "Rigioca")
                }
            }
        }
    }
}