package com.am24.am24

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun LocationPrivacyDialog(
    allowLocationForMatches: Boolean,
    onAllowLocationForMatchesChange: (Boolean) -> Unit,
    allowLocationPublic: Boolean,
    onAllowLocationPublicChange: (Boolean) -> Unit,
    isPrivate: Boolean,
    onIsPrivateChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_location_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lbl_visible_to_matches))
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = allowLocationForMatches,
                        onCheckedChange = onAllowLocationForMatchesChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF6F00),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lbl_visible_to_public))
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = allowLocationPublic,
                        onCheckedChange = onAllowLocationPublicChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF6F00),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_private_account),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = onIsPrivateChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF6F00),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.private_account_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.btn_save)) }
        },
    )
}