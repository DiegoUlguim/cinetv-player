package br.projetor.cinetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import br.projetor.cinetv.dados.Busca
import br.projetor.cinetv.dados.banco.CatalogoDao
import br.projetor.cinetv.dados.banco.Canal
import br.projetor.cinetv.dados.banco.Filme
import br.projetor.cinetv.dados.banco.Serie
import kotlinx.coroutines.delay

/** [consulta] e a busca que gerou estes resultados: vira a chave das fileiras, que recomecam do inicio a cada busca. */
private data class Resultados(val consulta: String, val canais: List<Canal>, val filmes: List<Filme>, val series: List<Serie>) {
    val vazio get() = canais.isEmpty() && filmes.isEmpty() && series.isEmpty()
}

/** Um campo e, abaixo, canais, filmes e series que batem com o texto (sem acento, por prefixo). */
@Composable
fun TelaBusca(
    dao: CatalogoDao,
    aoAbrirCanal: (Int) -> Unit,
    aoAbrirFilme: (Int) -> Unit,
    aoAbrirSerie: (Int) -> Unit,
    focoInicial: FocoInicial,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    var resultados by remember { mutableStateOf<Resultados?>(null) }
    var campoFocado by remember { mutableStateOf(false) }
    val teclado = LocalSoftwareKeyboardController.current
    val foco = LocalFocusManager.current
    val estadoLista = rememberLazyListState()
    val primeiroResultado = remember { FocoInicial() }

    // Busca nova volta ao topo; a chave e a consulta dos resultados, e nao o texto, para nao
    // remontar as fileiras a cada letra enquanto os resultados novos nao chegam
    LaunchedEffect(resultados?.consulta) { estadoLista.scrollToItem(0) }

    // Descer do campo (ou a lupa do teclado) vai ao primeiro resultado, nao ao mais proximo do campo
    fun irParaResultados(): Boolean {
        if (!primeiroResultado.composto) return false
        return runCatching { primeiroResultado.requester.requestFocus() }.isSuccess
    }

    LaunchedEffect(texto) {
        val consulta = Busca.consultaFts(texto)
        if (consulta == null || texto.trim().length < 2) {
            resultados = null
            return@LaunchedEffect
        }
        delay(300)
        resultados = Resultados(consulta, dao.buscarCanais(consulta), dao.buscarFilmes(consulta), dao.buscarSeries(consulta))
    }

    LazyColumn(
        state = estadoLista,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "campo") {
            CampoTexto(
                valor = texto,
                aoMudar = { texto = it },
                estilo = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                teclado = KeyboardOptions(imeAction = ImeAction.Search),
                // A lupa do teclado fecha o teclado e desce para os resultados
                acoes = KeyboardActions(onSearch = {
                    teclado?.hide()
                    if (!irParaResultados()) foco.moveFocus(FocusDirection.Down)
                }),
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .width(640.dp)
                    .focoInicial(focoInicial)
                    .onPreviewKeyEvent { evento ->
                        evento.type == KeyEventType.KeyDown && evento.key == Key.DirectionDown && irParaResultados()
                    }
                    .sairComSetas(foco)
                    .onFocusChanged { campoFocado = it.isFocused }
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .border(2.dp, if (campoFocado) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decoracao = { campo ->
                    if (texto.isEmpty()) {
                        Text("Buscar canal, filme ou série (OK abre o teclado)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    campo()
                },
            )
        }
        val atuais = resultados
        if (atuais != null && atuais.vazio) {
            item(key = "nada") {
                Text("Nada encontrado para \"$texto\"", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp))
            }
        }
        if (atuais != null && atuais.canais.isNotEmpty()) {
            item(key = "canais:" + atuais.consulta) {
                Column {
                    Titulo("Canais")
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(atuais.canais, key = { _, it -> it.id }) { indice, canal ->
                            ItemFoco(
                                aoClicar = { aoAbrirCanal(canal.id) },
                                modifier = Modifier.width(280.dp).then(if (indice == 0) Modifier.focoInicial(primeiroResultado) else Modifier),
                            ) { focado ->
                                Text(
                                    canal.nomeBase,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (focado) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (atuais != null && atuais.filmes.isNotEmpty()) {
            item(key = "filmes:" + atuais.consulta) {
                FileiraResultado(
                    "Filmes", atuais.filmes.map { ItemCapa(it.id, it.nome, it.capa) }, aoAbrirFilme,
                    primeiro = if (atuais.canais.isEmpty()) primeiroResultado else null,
                )
            }
        }
        if (atuais != null && atuais.series.isNotEmpty()) {
            item(key = "series:" + atuais.consulta) {
                FileiraResultado(
                    "Séries", atuais.series.map { ItemCapa(it.id, it.nome, it.capa) }, aoAbrirSerie,
                    primeiro = if (atuais.canais.isEmpty() && atuais.filmes.isEmpty()) primeiroResultado else null,
                )
            }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
}

@Composable
private fun FileiraResultado(titulo: String, itens: List<ItemCapa>, aoAbrir: (Int) -> Unit, primeiro: FocoInicial?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Titulo(titulo)
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(itens, key = { _, it -> it.id }) { indice, item ->
                CapaFoco(
                    capa = item.capa,
                    descricao = item.nome,
                    aoClicar = { aoAbrir(item.id) },
                    modifier = if (indice == 0 && primeiro != null) Modifier.focoInicial(primeiro) else Modifier,
                )
            }
        }
    }
}
