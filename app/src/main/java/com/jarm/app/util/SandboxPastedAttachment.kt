package com.jarm.app.util

import com.jarm.app.model.AttachmentStorage
import com.jarm.app.model.SelectedAttachment

/** Clipboard admission only; ordinary typing and existing draft text never use this threshold. */
internal const val SANDBOX_PASTE_MIN_CHARS = 8_000

internal fun sandboxPastedAttachment(text: String): SelectedAttachment = SelectedAttachment(
    uri = "",
    type = "file",
    fileName = "pasted-text.txt",
    mimeType = "text/plain",
    storage = AttachmentStorage.LOCAL_SANDBOX_PENDING,
    sourceText = text,
)
