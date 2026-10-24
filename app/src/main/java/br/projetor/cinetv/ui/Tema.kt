package br.projetor.cinetv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Paleta unica e escura: grafite com um acento ambar. */
private val Cores = darkColorScheme(
    primary = Color(0xFFE8B04A),
    onPrimary = Color(0xFF14161A),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF17191E),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF22252B),
    onSurfaceVariant = Color(0xFF9EA3AB),
)

@Composable
fun TemaCineTV(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Cores, content = conteudo)
}
