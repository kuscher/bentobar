package io.github.kuscher.bentobar.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/** Short messages for the settings window's snackbar, with an optional action (e.g. Undo). */
object Notice {
    class Message(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

    val messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)

    fun post(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        messages.tryEmit(Message(text, action, onAction))
    }
}
