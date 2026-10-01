package io.github.kuscher.bentobar.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Short messages. Without an action it's a toast, which fades on its own. With one (Undo, Open
 * settings) it's the settings window's snackbar, shown for a fixed time: a snackbar with an action
 * and no duration stays until dismissed.
 */
object Notice {
    class Message(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

    val messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)

    fun post(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        if (action == null) android.widget.Toast.makeText(io.github.kuscher.bentobar.items.Env.app, text, android.widget.Toast.LENGTH_SHORT).show()
        else messages.tryEmit(Message(text, action, onAction))
    }
}
