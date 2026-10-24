package br.projetor.cinetv.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.MaterialTheme

/**
 * Campo de uma linha que guarda o cursor e o leva para o fim ao ganhar foco.
 * O BasicTextField de String recria a selecao no inicio quando o campo volta a ser composto
 * (rolagem, troca de aba), e quem volta para continuar digitando escrevia antes do texto.
 */
@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    estilo: TextStyle,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    acoes: KeyboardActions = KeyboardActions.Default,
    transformacao: VisualTransformation = VisualTransformation.None,
    decoracao: @Composable (campo: @Composable () -> Unit) -> Unit = { it() },
) {
    var campo by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(valor, TextRange(valor.length)))
    }
    // Texto trocado por fora (ex.: login recebido por ADB) substitui o do campo
    LaunchedEffect(valor) {
        if (campo.text != valor) campo = TextFieldValue(valor, TextRange(valor.length))
    }
    BasicTextField(
        value = campo,
        onValueChange = { novo ->
            campo = novo
            if (novo.text != valor) aoMudar(novo.text)
        },
        singleLine = true,
        textStyle = estilo,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = teclado,
        keyboardActions = acoes,
        visualTransformation = transformacao,
        decorationBox = decoracao,
        modifier = modifier.onFocusChanged {
            if (it.isFocused) campo = campo.copy(selection = TextRange(campo.text.length))
        },
    )
}
