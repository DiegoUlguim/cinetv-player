package br.projetor.cinetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.first
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import br.projetor.cinetv.dados.RepositorioGuia
import br.projetor.cinetv.dados.banco.Canal
import br.projetor.cinetv.dados.banco.CatalogoDao
import br.projetor.cinetv.dados.banco.Categoria
import br.projetor.cinetv.dados.usuario.BancoUsuario
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingSource
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val ESPERA_TROCA_GRUPO_MS = 300L
private const val ESPERA_GUIA_MS = 400L

/** Id do grupo "Favoritos", que nao existe no servidor. */
const val GRUPO_FAVORITOS = "__favoritos__"

/** Grupos a esquerda ("Favoritos" primeiro, se houver); canais do grupo em foco a direita. */
@Composable
fun TelaAoVivo(
    dao: CatalogoDao,
    usuario: BancoUsuario,
    guia: RepositorioGuia,
    aoAbrirCanal: (canalId: Int, deFavoritos: Boolean) -> Unit,
    focoInicial: FocoInicial,
) {
    val doServidor by remember { dao.categorias("live") }.collectAsState(initial = emptyList())
    val favoritos by remember { usuario.favoritos().doTipo("canal") }.collectAsState(initial = emptyList())
    val idsFavoritos = favoritos.map { it.idConteudo }
    val categorias = if (favoritos.isEmpty()) doServidor
    else listOf(Categoria("live", GRUPO_FAVORITOS, "★ Favoritos", -1)) + doServidor
    val escopo = rememberCoroutineScope()
    var categoriaAtual by rememberSaveable { mutableStateOf<String?>(null) }
    var categoriaEmFoco by remember { mutableStateOf<String?>(null) }
    val selecionada = categoriaAtual ?: categorias.firstOrNull()?.id
    val focoGrupo = remember { FocoInicial() }
    // Pedido de foco nos canais (seta direita), atendido quando a lista do grupo carregar
    var entrarNosCanais by remember { mutableStateOf(false) }

    // So troca a lista de canais quando o foco para no grupo: rolar pelos grupos nao recria a lista a cada passo
    LaunchedEffect(categoriaEmFoco) {
        val emFoco = categoriaEmFoco ?: return@LaunchedEffect
        delay(ESPERA_TROCA_GRUPO_MS)
        categoriaAtual = emFoco
    }

    Row(modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 16.dp)) {
        LazyColumn(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .onPreviewKeyEvent { evento ->
                    if (evento.type != KeyEventType.KeyDown || evento.key != Key.DirectionRight) return@onPreviewKeyEvent false
                    // A direita confirma o grupo na hora (esperar os 300 ms deixaria entrar nos canais do grupo
                    // anterior) e a lista decide o canal: o ultimo usado no grupo ou o primeiro visivel.
                    // A busca de foco do Compose escolheria o canal da mesma altura.
                    categoriaEmFoco?.let { categoriaAtual = it }
                    entrarNosCanais = true
                    true
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(categorias, key = { it.id }) { categoria ->
                ItemFoco(
                    aoClicar = { categoriaAtual = categoria.id },
                    selecionado = categoria.id == selecionada,
                    aoFocar = {
                        categoriaEmFoco = categoria.id
                        // Um pedido pendente (grupo sem canais) nao pode puxar o foco depois, no meio da navegacao
                        entrarNosCanais = false
                    },
                    modifier = if (categoria.id == selecionada) Modifier.focoInicial(focoInicial).focoInicial(focoGrupo) else Modifier,
                ) { focado ->
                    Text(
                        categoria.nome,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = corTexto(focado),
                    )
                }
            }
        }
        if (selecionada != null) {
            val deFavoritos = selecionada == GRUPO_FAVORITOS
            ListaDeCanais(
                fonte = if (deFavoritos) {
                    { dao.canaisPorIdsPaginado(idsFavoritos) }
                } else {
                    { dao.canais(selecionada) }
                },
                chaveFonte = if (deFavoritos) "fav:" + idsFavoritos.joinToString(",") else selecionada,
                grupo = selecionada,
                entrarNosCanais = entrarNosCanais,
                aoEntrarNosCanais = { entrarNosCanais = false },
                // A esquerda volta ao grupo selecionado, nao ao da mesma altura. O onEnter da coluna de
                // grupos nao resolve: a busca de foco em 2D escolhe direto o item, sem entrar no grupo
                aoVoltarAosGrupos = { focoGrupo.composto && runCatching { focoGrupo.requester.requestFocus() }.isSuccess },
                guia = guia,
                aoAbrirCanal = { aoAbrirCanal(it, deFavoritos) },
                aoSegurar = { canal ->
                    escopo.launch {
                        val ids = dao.variantes(canal.grupo).map { it.id }
                        usuario.alternarFavoritoCanal(canal.id, ids, canal.nomeBase, canal.logo)
                    }
                },
            )
        }
    }
}

@Composable
private fun ListaDeCanais(
    fonte: () -> PagingSource<Int, Canal>,
    chaveFonte: String,
    grupo: String,
    entrarNosCanais: Boolean,
    aoEntrarNosCanais: () -> Unit,
    aoVoltarAosGrupos: () -> Boolean,
    guia: RepositorioGuia,
    aoAbrirCanal: (Int) -> Unit,
    aoSegurar: (Canal) -> Unit,
) {
    val canais = remember(chaveFonte) {
        Pager(PagingConfig(pageSize = 40, enablePlaceholders = true), pagingSourceFactory = fonte).flow
    }.collectAsLazyPagingItems()
    // Cada grupo abre no topo (decisao do dono, 24/10); a chave e o grupo e nao a fonte, para
    // desfavoritar um canal dentro de Favoritos nao jogar a lista para o topo
    val estadoLista = remember(grupo) { LazyListState() }
    var listaParada by remember { mutableStateOf(false) }
    // Ultimo canal focado neste grupo. O focusRestorer nao serve aqui: na lista paginada ele nao
    // restaurava (conferido no emulador em 24/10) e a busca caia no canal da mesma altura do grupo
    var ultimoFocado by remember(grupo) { mutableIntStateOf(-1) }
    val focoUltimo = remember { FocusRequester() }

    // Na rolagem continua cada canal fica ~1 s na tela; o guia so e consultado quando a lista para
    LaunchedEffect(estadoLista) {
        snapshotFlow { estadoLista.firstVisibleItemIndex }.collectLatest {
            listaParada = false
            delay(ESPERA_GUIA_MS)
            listaParada = true
        }
    }

    // Seta direita nos grupos: foco no ultimo canal usado no grupo ou no primeiro visivel
    LaunchedEffect(grupo, entrarNosCanais) {
        if (!entrarNosCanais) return@LaunchedEffect
        snapshotFlow { canais.itemCount }.first { it > 0 }
        val alvo = ultimoFocado.takeIf { it in 0 until canais.itemCount } ?: estadoLista.firstVisibleItemIndex
        if (estadoLista.layoutInfo.visibleItemsInfo.none { it.index == alvo }) estadoLista.scrollToItem(alvo)
        ultimoFocado = alvo
        withFrameNanos { }
        runCatching { focoUltimo.requestFocus() }
        aoEntrarNosCanais()
    }

    LazyColumn(
        state = estadoLista,
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp)
            .onPreviewKeyEvent { evento ->
                evento.type == KeyEventType.KeyDown && evento.key == Key.DirectionLeft && aoVoltarAosGrupos()
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(count = canais.itemCount, key = canais.itemKey { it.id }) { indice ->
            val canal = canais[indice]
            val programa = canal?.let { programaAtual(guia, it, listaParada) }
            // derivedStateOf: mover o foco recompoe so os dois itens que mudaram, nao a tela toda
            val ehUltimo by remember(indice) { derivedStateOf { ultimoFocado == indice } }
            ItemFoco(
                modifier = if (ehUltimo) Modifier.focusRequester(focoUltimo) else Modifier,
                aoFocar = { ultimoFocado = indice },
                aoClicar = { canal?.let { aoAbrirCanal(it.id) } },
                aoSegurar = { canal?.let(aoSegurar) },
                altura = 56.dp,
            ) { focado ->
                AsyncImage(
                    model = canal?.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 56.dp, height = 32.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(canal?.nomeBase.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = corTexto(focado))
                    if (programa != null) {
                        Text(
                            programa,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (focado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Programa atual do canal: do cache na hora; da rede so com a lista parada. */
@Composable
private fun programaAtual(guia: RepositorioGuia, canal: Canal, listaParada: Boolean): String? {
    val resultado by produceState(initialValue = guia.emCache(canal), canal.id, listaParada) {
        if (value != null || canal.epgId == null || !listaParada) return@produceState
        value = guia.obter(canal)
    }
    return resultado?.agora?.titulo
}

@Composable
private fun corTexto(focado: Boolean) =
    if (focado) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
