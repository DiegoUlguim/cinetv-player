package br.projetor.cinetv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage

private val FormaCapa = RoundedCornerShape(8.dp)

/** Capa de filme ou serie: no foco so ganha uma borda clara, sem animar escala (custo alto no H713). */
@Composable
fun CapaFoco(
    capa: String?,
    descricao: String?,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    aoSegurar: (() -> Unit)? = null,
) {
    val interacao = remember { MutableInteractionSource() }
    val focado by interacao.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(width = 120.dp, height = 180.dp)
            .clip(FormaCapa)
            .border(3.dp, if (focado) MaterialTheme.colorScheme.onBackground else Color.Transparent, FormaCapa)
            .toqueDoControle(interacao, aoClicar, aoSegurar),
    ) {
        AsyncImage(
            model = capa,
            contentDescription = descricao,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
