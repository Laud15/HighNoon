package it.diunipi.sam.highnoon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LobbyScreen(modifier: Modifier = Modifier.Companion) {
    //STATUS: The current status phrase of the duel.
    // 'remember' + 'mutableStateOf' = the pattern of Lez. 09.
    // 'by' is the delegated property (Lesson 09): we read/write 'statusText' as a normal variable,
    // but behind it there is the mutableStateOf getter/setter.
    var statusText by remember { mutableStateOf("waiting for the start") }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Game's title
        Text(
            text = "HIGH NOON",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // subtitle
        Text(
            text = "Gunslinger duel",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        //Current Status: This Text READS 'statusText'. For the publish/subscribe logic of Lez. 09,
        // this reading "subscribes" the Text to changes: if statusText changes,
        // ONLY This part is recomposed.
        Text(
            text = statusText,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        //EVENT: the button. onClick is a lambda (Lez. 09).
        // By pressing it we CHANGE the state -> the recomposing is triggered.
        Button(onClick = {
            statusText = "Looking for an opponent..."
        }) {
            Text(text = "Find an opponent")
        }
    }
}