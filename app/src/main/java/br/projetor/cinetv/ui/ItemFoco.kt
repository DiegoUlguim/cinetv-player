package br.projetor.cinetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

private val Forma = RoundedCornerShape(8.dp)

/**
 * Item de lista leve para o projetor: so troca a cor de fundo no foco.
 * O ListItem do tv-material anima escala e brilho a cada foco, o que dobra o custo do quadro no H713.
 */
@Composable
fun ItemFoco(
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    selecionado: Boolean = false,
    altura: Dp = 48.dp,
    aoFocar: (() -> Unit)? = null,
    aoSegurar: (() -> Unit)? = null,
    conteudo: @Composable RowScope.(focado: Boolean) -> Unit,
) {
    val interacao = remember { MutableInteractionSource() }
    val focado by interacao.collectIsFocusedAsState()
    val fundo = when {
        focado -> MaterialTheme.colorScheme.onBackground
        selecionado -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(altura)
            .clip(Forma)
            .background(fundo)
            .onFocusChanged { if (it.isFocused) aoFocar?.invoke() }
            .toqueDoControle(interacao, aoClicar, aoSegurar)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        conteudo(focado)
    }
}
