package com.am24.am24

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private enum class LocationPrivacyOption {
    PUBLIC,
    MATCHES_ONLY,
    PRIVATE
}

@Composable
fun LocationPrivacyDialog(
    allowLocationForMatches: Boolean,
    allowLocationPublic: Boolean,
    isPrivate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (allowLocationForMatches: Boolean, allowLocationPublic: Boolean, isPrivate: Boolean) -> Unit,
) {
    val initialOption = when {
        isPrivate -> LocationPrivacyOption.PRIVATE
        allowLocationPublic -> LocationPrivacyOption.PUBLIC
        allowLocationForMatches -> LocationPrivacyOption.MATCHES_ONLY
        else -> LocationPrivacyOption.PRIVATE
    }
    val (selectedOption, setSelectedOption) = remember { mutableStateOf(initialOption) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_location_title)) },
        text = {
            Column {
                RadioRow(
                    label = stringResource(R.string.lbl_visible_to_public),
                    option = LocationPrivacyOption.PUBLIC,
                    selected = selectedOption,
                    onSelect = setSelectedOption
                )
                Spacer(modifier = Modifier.height(8.dp))
                RadioRow(
                    label = stringResource(R.string.lbl_visible_to_matches),
                    option = LocationPrivacyOption.MATCHES_ONLY,
                    selected = selectedOption,
                    onSelect = setSelectedOption
                )
                Spacer(modifier = Modifier.height(8.dp))
                RadioRow(
                    label = stringResource(R.string.settings_private_account),
                    option = LocationPrivacyOption.PRIVATE,
                    selected = selectedOption,
                    onSelect = setSelectedOption
                )
                if (selectedOption == LocationPrivacyOption.PRIVATE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.private_account_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedOption) {
                        LocationPrivacyOption.PUBLIC -> onConfirm(true, true, false)
                        LocationPrivacyOption.MATCHES_ONLY -> onConfirm(true, false, false)
                        LocationPrivacyOption.PRIVATE -> onConfirm(false, false, true)
                    }
                }
            ) { Text(stringResource(R.string.btn_save)) }
        },
    )
}

@Composable
private fun RadioRow(
    label: String,
    option: LocationPrivacyOption,
    selected: LocationPrivacyOption,
    onSelect: (LocationPrivacyOption) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(option) }
    ) {
        RadioButton(
            selected = selected == option,
            onClick = { onSelect(option) },
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF6F00))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}