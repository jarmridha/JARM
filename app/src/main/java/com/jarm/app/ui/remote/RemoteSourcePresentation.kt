package com.jarm.app.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jarm.app.R
import com.jarm.app.remote.RemoteAdapter

@Composable
internal fun remoteSourceName(source: RemoteAdapter): String = stringResource(when (source.id) {
    "codex-cli" -> R.string.remote_source_codex_cli
    "claude-code" -> R.string.remote_source_claude_code
    "claude-desktop" -> R.string.remote_source_claude_desktop
    "codex-desktop", RemoteAdapter.LEGACY_DESKTOP -> R.string.remote_source_codex_desktop
    else -> R.string.remote_source_unavailable
})

internal fun remoteSourceIcon(source: RemoteAdapter): Int =
    if (source.family == "claude") R.drawable.provider_claude else R.drawable.provider_openai
