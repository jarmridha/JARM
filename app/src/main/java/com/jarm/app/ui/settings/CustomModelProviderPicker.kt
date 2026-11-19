package com.jarm.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jarm.app.R
import com.jarm.app.ui.components.providerIcon
import com.jarm.app.ui.components.JARMDropdownMenuItem
import com.jarm.app.ui.components.JARMExposedDropdownMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomModelProviderPicker(
    customModelProvider: String,
    customModelProviderMenuExpanded: Boolean,
    providerChoices: List<String>,
    onExpandedChange: (Boolean) -> Unit,
    onProviderChange: (String) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = customModelProviderMenuExpanded,
        onExpandedChange = { onExpandedChange(it) },
    ) {
        OutlinedTextField(
            value = customModelProvider,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.embedding_provider_label)) },
            leadingIcon = {
                val iconRes = providerIcon(customModelProvider)
                if (iconRes != 0) {
                    Icon(
                        painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(Icons.Default.Cloud, contentDescription = null)
                }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = customModelProviderMenuExpanded
                )
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
        )
        JARMExposedDropdownMenu(
            expanded = customModelProviderMenuExpanded,
            onDismissRequest = {
                onExpandedChange(false)
            },
            matchAnchorWidth = false,
        ) {
            providerChoices.forEach { providerName ->
                JARMDropdownMenuItem(
                    text = { Text(providerName) },
                    onClick = {
                        onProviderChange(providerName)
                        onExpandedChange(false)
                    },
                    leadingIcon = {
                        val iconRes = providerIcon(providerName)
                        if (iconRes != 0) {
                            Icon(
                                painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}
