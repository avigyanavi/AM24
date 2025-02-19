package com.am24.am24.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.am24.am24.ModelingState

@Composable
fun RevaanProfileDialog(
    modelingState: ModelingState,
    memoryLog: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revaan's Profile", color = Color.White) },
        text = {
            Column {
                Text("Age: ${modelingState.age}", color = Color.White)
                Text("Mood: ${modelingState.mood}", color = Color.White)
                Text("Career Progress: ${modelingState.careerProgress}", color = Color.White)
                Text("External Attention: ${modelingState.externalAttention}", color = Color.White)
                Text("Focus on User: ${modelingState.focusOnUser}", color = Color.White)
                Text("Jealousy Level: ${modelingState.jealousyLevel}", color = Color.White)
                Text("Maturity: ${modelingState.maturity}", color = Color.White)

                Spacer(modifier = Modifier.height(8.dp))
                Text("Recent Memories:", color = Color.White, style = MaterialTheme.typography.h6)
                LazyColumn(modifier = Modifier.height(100.dp)) {
                    items(memoryLog) { memory ->
                        Text("• $memory", color = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = """
                    Revaan reacts to your engagement.
                    The more personal and deep your conversations, the more dynamic his state becomes.
                    """.trimIndent(),
                    color = Color.Gray,
                    style = MaterialTheme.typography.body2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00))
            ) {
                Text("Close", color = Color.White)
            }
        },
        backgroundColor = Color.DarkGray,
        contentColor = Color.White
    )
}
