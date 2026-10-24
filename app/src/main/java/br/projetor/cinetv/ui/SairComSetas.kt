package br.projetor.cinetv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Campo de texto no controle remoto: cima e baixo sempre saem do campo.
 * Sem isso o campo segura o foco (as setas so movem o cursor) e o usuario fica preso.
 */
fun Modifier.sairComSetas(foco: FocusManager): Modifier = onPreviewKeyEvent { evento ->
    if (evento.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (evento.key) {
        Key.DirectionUp -> foco.moveFocus(FocusDirection.Up)
        Key.DirectionDown -> foco.moveFocus(FocusDirection.Down)
        else -> false
    }
}

private class EstadoToque {
    var pressionado = false
    var segurou = false
}

/**
 * Focavel com OK curto e OK segurado. O combinedClickable do Compose nao reconhece o OK segurado
 * do controle; aqui o "segurar" e a repeticao que o Android gera depois de ~500 ms com a tecla presa.
 * Soltar depois de segurar nao abre o item.
 */
fun Modifier.toqueDoControle(
    interacao: MutableInteractionSource,
    aoClicar: () -> Unit,
    aoSegurar: (() -> Unit)?,
): Modifier = composed {
    val estado = remember { EstadoToque() }
    this
        .onKeyEvent { evento ->
            if (evento.key != Key.DirectionCenter && evento.key != Key.Enter && evento.key != Key.NumPadEnter) {
                return@onKeyEvent false
            }
            when (evento.type) {
                KeyEventType.KeyDown -> {
                    if (evento.nativeKeyEvent.repeatCount == 0) {
                        estado.pressionado = true
                        estado.segurou = false
                    } else if (!estado.segurou && aoSegurar != null) {
                        estado.segurou = true
                        aoSegurar()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    if (estado.pressionado && !estado.segurou) aoClicar()
                    estado.pressionado = false
                    true
                }
                else -> false
            }
        }
        .focusable(interactionSource = interacao)
}
