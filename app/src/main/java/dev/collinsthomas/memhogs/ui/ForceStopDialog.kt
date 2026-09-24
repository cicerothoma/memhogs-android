package dev.collinsthomas.memhogs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.collinsthomas.memhogs.R

@Composable
internal fun ForceStopDialog(label: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Palette.Surface, shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    stringResource(R.string.force_stop_title, label),
                    fontFamily = Mono,
                    fontSize = 15.sp,
                    color = Palette.Amber,
                )
                Text(
                    stringResource(R.string.force_stop_explanation),
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Palette.Text,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TermButton(stringResource(R.string.force_stop_button), onClick = onConfirm)
                    TermButton(stringResource(R.string.selection_cancel), accent = Palette.Dim, onClick = onDismiss)
                }
            }
        }
    }
}
