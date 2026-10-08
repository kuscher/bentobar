package io.github.kuscher.bentobar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.util.SymIcon

/** A labelled group of single-choice chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(label: String, choices: List<Pair<T, String>>, selected: T, help: String? = null, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            choices.forEach { (value, text) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) },
                    leadingIcon = if (value == selected) { { SymIcon(io.github.kuscher.bentobar.util.Sym.CHECK) } } else null)
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, help: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // The whole row is the switch: clicking the label toggles it, and a screen reader hears its name.
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A text field that saves as you type. While it has the focus it keeps what is typed: a caller may
 * store something else (nothing for a blank, a shortened text) and the field must not change under
 * the cursor. It shows the stored [value] again when the focus goes, and whenever the value changes
 * while it doesn't have it. [maxLength]: no more can be typed than the caller keeps.
 */
@Composable
fun TextRow(label: String, value: String, help: String? = null, placeholder: String = "", numeric: Boolean = false,
            /** Shown instead of [help], with the field marked, while the text can't be used. */
            error: String? = null, maxLength: Int = Int.MAX_VALUE, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = value }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { typed -> typed.take(maxLength).let { text = it; onChange(it) } },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        (error ?: help)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}

@Composable
fun SliderRow(label: String, value: Int, range: IntRange, format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    var v by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { v = value.toFloat() }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(format(v.toInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onChange(v.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(), steps = (range.last - range.first - 1).coerceIn(0, 40),
            modifier = Modifier.semantics { contentDescription = label })
    }
}
