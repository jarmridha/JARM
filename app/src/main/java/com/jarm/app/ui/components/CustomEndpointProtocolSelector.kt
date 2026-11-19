package com.jarm.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jarm.app.data.CustomEndpointProtocol
import com.jarm.app.ui.settings.PillTabSwitcher

fun CustomEndpointProtocol.displayName(): String = when (this) {
    CustomEndpointProtocol.OPENAI -> "OpenAI"
    CustomEndpointProtocol.GOOGLE -> "Google"
    CustomEndpointProtocol.ANTHROPIC -> "Anthropic"
    CustomEndpointProtocol.UNKNOWN -> "Unsupported"
}

@Composable
fun CustomEndpointProtocolSelector(
    selected: CustomEndpointProtocol,
    onSelected: (CustomEndpointProtocol) -> Unit,
    modifier: Modifier = Modifier,
) {
    val protocols = CustomEndpointProtocol.selectable
    PillTabSwitcher(
        tabs = protocols.map(CustomEndpointProtocol::displayName),
        selectedIndex = protocols.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> protocols.getOrNull(index)?.let(onSelected) },
        modifier = modifier,
        allowLabelOverflow = true,
    )
}
