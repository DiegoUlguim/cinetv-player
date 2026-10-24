package br.projetor.cinetv.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Onde o foco entra numa aba quando ela ainda nao guardou um item focado.
 * O item costuma estar numa lista preguicosa e sai da composicao ao rolar; pedir foco a um
 * FocusRequester solto derruba o app, entao quem usa confere [composto] antes.
 */
class FocoInicial {
    val requester = FocusRequester()
    private var anexados by mutableIntStateOf(0)

    val composto: Boolean get() = anexados > 0

    /** O alvo enquanto composto; senao o padrao do Compose (o item mais proximo). */
    val alvo: FocusRequester get() = if (composto) requester else FocusRequester.Default

    internal fun anexar() { anexados++ }
    internal fun soltar() { anexados-- }
}

fun Modifier.focoInicial(foco: FocoInicial): Modifier = composed {
    DisposableEffect(foco) {
        foco.anexar()
        onDispose { foco.soltar() }
    }
    focusRequester(foco.requester)
}
