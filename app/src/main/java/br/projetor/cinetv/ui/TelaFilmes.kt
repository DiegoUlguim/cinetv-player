package br.projetor.cinetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import br.projetor.cinetv.dados.banco.CatalogoDao
import br.projetor.cinetv.dados.banco.Categoria
import br.projetor.cinetv.dados.usuario.BancoUsuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Altura da capa (CapaFoco) e, portanto, de toda fileira, carregada ou nao. */
private val ALTURA_CAPA = 180.dp

/** O que a fileira precisa saber de um item (filme ou serie). */
class ItemCapa(val id: Int, val nome: String, val capa: String?)

/** Fileira montada fora do catalogo paginado ("Continuar assistindo", "Minha lista"). */
class FileiraPronta(val chave: String, val titulo: String, val itens: List<ItemCapa>)

@Composable
fun TelaFilmes(dao: CatalogoDao, usuario: BancoUsuario, aoAbrirFilme: (Int) -> Unit, focoInicial: FocoInicial) {
    val extras = remember {
        val continuar = usuario.progresso().recentes("filme", 20).map { lista ->
            FileiraPronta("continuar", "Continuar assistindo", lista.map { ItemCapa(it.idConteudo, it.titulo, it.capa) })
        }
        val favoritos = usuario.favoritos().doTipo("filme").map { lista ->
            FileiraPronta("favoritos", "Minha lista", lista.map { ItemCapa(it.idConteudo, it.nome, it.capa) })
        }
        combine(continuar, favoritos) { a, b -> listOf(a, b).filter { it.itens.isNotEmpty() } }
    }
    val escopo = rememberCoroutineScope()
    TelaCapas(
        extras = extras,
        categorias = remember { dao.categoriasDeFilmeComItens() },
        itens = { categoriaId -> dao.filmes(categoriaId) },
        paraCapa = { ItemCapa(it.id, it.nome, it.capa) },
        aoAbrir = aoAbrirFilme,
        aoSegurar = { item -> escopo.launch { usuario.alternarFavorito("filme", item.id, item.nome, item.capa) } },
        focoInicial = focoInicial,
    )
}

@Composable
fun TelaSeries(dao: CatalogoDao, usuario: BancoUsuario, aoAbrirSerie: (Int) -> Unit, focoInicial: FocoInicial) {
    val extras = remember {
        // Progresso e por episodio; a fileira mostra cada serie uma vez, com a capa da serie
        val continuar = usuario.progresso().recentes("episodio", 60).map { lista ->
            val ids = lista.mapNotNull { it.serieId }.distinct().take(20)
            val series = dao.seriesPorIds(ids).associateBy { it.id }
            FileiraPronta("continuar", "Continuar assistindo", ids.mapNotNull { id -> series[id]?.let { ItemCapa(it.id, it.nome, it.capa) } })
        }
        val favoritos = usuario.favoritos().doTipo("serie").map { lista ->
            FileiraPronta("favoritos", "Minha lista", lista.map { ItemCapa(it.idConteudo, it.nome, it.capa) })
        }
        combine(continuar, favoritos) { a, b -> listOf(a, b).filter { it.itens.isNotEmpty() } }
    }
    val escopo = rememberCoroutineScope()
    TelaCapas(
        extras = extras,
        categorias = remember { dao.categoriasDeSerieComItens() },
        itens = { categoriaId -> dao.series(categoriaId) },
        paraCapa = { ItemCapa(it.id, it.nome, it.capa) },
        aoAbrir = aoAbrirSerie,
        aoSegurar = { item -> escopo.launch { usuario.alternarFavorito("serie", item.id, item.nome, item.capa) } },
        focoInicial = focoInicial,
    )
}

/** Fileiras prontas no topo e, abaixo, uma fileira de capas por categoria carregada aos poucos. */
@Composable
private fun <T : Any> TelaCapas(
    extras: Flow<List<FileiraPronta>>,
    categorias: Flow<List<Categoria>>,
    itens: (String) -> PagingSource<Int, T>,
    paraCapa: (T) -> ItemCapa,
    aoAbrir: (Int) -> Unit,
    aoSegurar: (ItemCapa) -> Unit,
    focoInicial: FocoInicial,
) {
    val prontas by extras.collectAsState(initial = emptyList())
    val lista by categorias.collectAsState(initial = emptyList())
    // Paginas de cada categoria guardadas enquanto a aba esta aberta: a fileira que volta a tela
    // (subindo a lista) mostra as capas na hora, em vez de recarregar do banco
    val escopo = rememberCoroutineScope()
    val paginas = remember { HashMap<String, Flow<PagingData<T>>>() }
    fun paginaDe(categoriaId: String) = paginas.getOrPut(categoriaId) {
        Pager(PagingConfig(pageSize = 20, prefetchDistance = 10, enablePlaceholders = false)) { itens(categoriaId) }
            .flow.cachedIn(escopo)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // A primeira capa da primeira fileira e onde o foco entra quando a aba abre
        fun primeira(fileira: Int, indice: Int) =
            if (fileira == 0 && indice == 0) Modifier.focoInicial(focoInicial) else Modifier
        itemsIndexed(prontas, key = { _, it -> "extra:" + it.chave }) { posicao, fileira ->
            Column {
                TituloFileira(fileira.titulo)
                LazyRow(
                    modifier = Modifier.height(ALTURA_CAPA).focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(fileira.itens, key = { _, it -> it.id }) { indice, item ->
                        CapaFoco(
                            capa = item.capa,
                            descricao = item.nome,
                            aoClicar = { aoAbrir(item.id) },
                            aoSegurar = { aoSegurar(item) },
                            modifier = primeira(posicao, indice),
                        )
                    }
                }
            }
        }
        itemsIndexed(lista, key = { _, it -> it.id }) { posicao, categoria ->
            Fileira(categoria, paginaDe(categoria.id), paraCapa, aoAbrir, aoSegurar, primeiraCapa = primeira(prontas.size + posicao, 0))
        }
    }
}

@Composable
private fun TituloFileira(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
    )
}

@Composable
private fun <T : Any> Fileira(
    categoria: Categoria,
    fluxo: Flow<PagingData<T>>,
    paraCapa: (T) -> ItemCapa,
    aoAbrir: (Int) -> Unit,
    aoSegurar: (ItemCapa) -> Unit,
    primeiraCapa: Modifier,
) {
    val pagina = fluxo.collectAsLazyPagingItems()

    Column {
        TituloFileira(categoria.nome)
        // Voltar a uma fileira retorna a capa onde o foco estava nela, e nao a da mesma coluna.
        // Altura fixa: vazia ate a pagina carregar, a fileira encolhia e a lista pulava na subida
        LazyRow(
            modifier = Modifier.height(ALTURA_CAPA).focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(count = pagina.itemCount, key = pagina.itemKey { paraCapa(it).id }) { indice ->
                val item = pagina[indice]?.let(paraCapa)
                CapaFoco(
                    capa = item?.capa,
                    descricao = item?.nome,
                    aoClicar = { item?.let { aoAbrir(it.id) } },
                    aoSegurar = { item?.let(aoSegurar) },
                    modifier = if (indice == 0) primeiraCapa else Modifier,
                )
            }
        }
    }
}
