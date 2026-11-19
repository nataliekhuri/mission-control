package com.cineflow.missioncontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ------------------------------------------------------------------ Layout blocks

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Border),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Background),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, color: Color = Palette.Cyan, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.6f) else Palette.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text)
    }
}

/** Small icon button with tooltip-like semantics (content description). */
@Composable
fun ActionIcon(icon: ImageVector, description: String, tint: Color = Palette.TextMuted, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// ------------------------------------------------------------------ Badges & cards

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Panel(modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick) else Modifier), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Palette.Cyan.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Palette.Cyan, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

// ------------------------------------------------------------------ Tables

@Composable
fun TableHeader(columns: List<Pair<String, Float>>, trailingWidth: Dp = 0.dp) {
    Row(
        Modifier.fillMaxWidth().background(Palette.SurfaceHigh, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { (name, weight) ->
            Text(name.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, modifier = Modifier.weight(weight))
        }
        if (trailingWidth > 0.dp) Spacer(Modifier.width(trailingWidth))
    }
}

@Composable
fun TableRow(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        HorizontalDivider(color = Palette.Border.copy(alpha = 0.6f))
    }
}

@Composable
fun RowScope.Cell(text: String, weight: Float, color: Color = Palette.Text, bold: Boolean = false, secondary: String? = null) {
    Column(Modifier.weight(weight).padding(end = 8.dp)) {
        Text(
            text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (secondary != null) {
            Text(secondary, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------------ Inputs

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = Palette.TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.TextMuted) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (!numeric || new.all { it.isDigit() }) onValueChange(new) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Palette.TextMuted) } },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}

/** Read-only text field that opens a dropdown menu. [noneLabel] adds an "all / none" option that maps to null. */
@Composable
fun <T> SelectField(
    label: String,
    options: List<T>,
    selected: T?,
    onSelected: (T?) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: noneLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent layer on top of the field so a click anywhere opens the menu.
        Box(Modifier.matchParentSize().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled) { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(min = 200.dp)) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelected(null); expanded = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

// ------------------------------------------------------------------ Dialogs

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Eliminar",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = Palette.Danger) },
        title = { Text(title) },
        text = { Text(message, color = Palette.TextMuted) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Palette.SurfaceHigh,
    )
}

/** Dialog hosting a form. [errors] are displayed at the top (validation or database errors). */
@Composable
fun FormDialog(
    title: String,
    errors: List<String>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveText: String = "Guardar",
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(600.dp),
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (errors.isNotEmpty()) ErrorBox(errors)
                content()
            }
        },
        confirmButton = { PrimaryButton(saveText, onClick = onSave) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Palette.SurfaceHigh,
    )
}

@Composable
fun ErrorBox(errors: List<String>) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Danger.copy(alpha = 0.12f))
            .border(1.dp, Palette.Danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        errors.flatMap { it.lines() }.forEach { Text("• ${it.removePrefix("• ")}", color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
    }
}
