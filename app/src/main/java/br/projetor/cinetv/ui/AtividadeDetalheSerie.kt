package br.projetor.cinetv.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import br.projetor.cinetv.AplicacaoCineTV
import br.projetor.cinetv.dados.Registro
import br.projetor.cinetv.dados.banco.Serie
import br.projetor.cinetv.dados.usuario.Progresso
import br.projetor.cinetv.dados.xtream.DetalheSerie
import br.projetor.cinetv.dados.xtream.Episodio
import br.projetor.cinetv.dados.xtream.ErroServidor
import br.projetor.cinetv.player.AtividadePlayerSobDemanda
import br.projetor.cinetv.player.tempo
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Detalhe da serie: temporadas e episodios por get_series_info (sob demanda, sem cache no banco).
 * Quando um episodio termina, sugere o proximo com contagem de 10 s.
 */
class AtividadeDetalheSerie : ComponentActivity() {

    private val aplicacao get() = application as AplicacaoCineTV
    private var serie by mutableStateOf<Serie?>(null)
    private var detalhe by mutableStateOf<DetalheSerie?>(null)
    private var erro by mutableStateOf<String?>(null)
    private var progressos by mutableStateOf<Map<Int, Progresso>>(emptyMap())
    private var ultimo by mutableStateOf<Progresso?>(null)
    private var proximo by mutableStateOf<Episodio?>(null)
    private var retomadas by mutableIntStateOf(0)

    private val player = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val terminou = resultado.data?.getIntExtra(AtividadePlayerSobDemanda.RESULTADO_TERMINOU, -1) ?: -1
        if (resultado.resultCode == Activity.RESULT_OK && terminou >= 0) {
            proximo = detalhe?.proximoDepois(terminou)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        setContent {
            TemaCineTV {
                LaunchedEffect(id) {
                    serie = aplicacao.banco.catalogo().serie(id)
                    carregar(id)
                }
                LaunchedEffect(id, retomadas) {
                    val lista = aplicacao.usuario.progresso().daSerie(id)
                    progressos = lista.associateBy { it.idConteudo }
                    ultimo = lista.firstOrNull()
                }
                val ehFavorito by remember(id) { aplicacao.usuario.favoritos().existe("serie:$id") }.collectAsState(initial = false)
                val escopo = rememberCoroutineScope()
                serie?.let { atual ->
                    TelaDetalheSerie(
                        ehFavorito = ehFavorito,
                        aoFavoritar = { escopo.launch { aplicacao.usuario.alternarFavorito("serie", atual.id, atual.nome, atual.capa) } },
                        serie = atual,
                        detalhe = detalhe,
                        erro = erro,
                        progressos = progressos,
                        ultimo = ultimo,
                        proximo = proximo,
                        aoAssistir = ::assistir,
                        aoCancelarProximo = { proximo = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        retomadas++
    }

    private suspend fun carregar(id: Int) {
        val cliente = aplicacao.cliente() ?: return
        try {
            detalhe = withContext(Dispatchers.IO) { cliente.infoSerie(id) }
        } catch (e: ErroServidor) {
            erro = e.mensagemUsuario
        } catch (e: Exception) {
            Registro.aviso("Serie $id indisponivel", e)
            erro = "Não foi possível carregar os episódios"
        }
    }

    private fun assistir(episodio: Episodio, retomarEmMs: Long) {
        val atual = serie ?: return
        proximo = null
        val titulo = "${atual.nome} · T${episodio.temporada}:E${episodio.numero} ${episodio.titulo}"
        player.launch(
            AtividadePlayerSobDemanda.intencao(
                this, AtividadePlayerSobDemanda.TIPO_EPISODIO, episodio.id, episodio.extensao, titulo, atual.capa,
                retomarEmMs, serieId = atual.id,
            ),
        )
    }

    companion object {
        private const val EXTRA_ID = "id"

        fun abrir(contexto: Context, id: Int) {
            contexto.startActivity(Intent(contexto, AtividadeDetalheSerie::class.java).putExtra(EXTRA_ID, id))
        }
    }
}

@Composable
private fun TelaDetalheSerie(
    ehFavorito: Boolean,
    aoFavoritar: () -> Unit,
    serie: Serie,
    detalhe: DetalheSerie?,
    erro: String?,
    progressos: Map<Int, Progresso>,
    ultimo: Progresso?,
    proximo: Episodio?,
    aoAssistir: (Episodio, Long) -> Unit,
    aoCancelarProximo: () -> Unit,
) {
    val focoPrincipal = remember { FocusRequester() }
    var temporada by remember { mutableIntStateOf(-1) }
    val temporadas = detalhe?.temporadas.orEmpty()
    // Episodio de onde parou (se ainda existir na serie) ou o primeiro
    val retomar = ultimo?.let { detalhe?.episodio(it.idConteudo) }
    val primeiro = temporadas.firstOrNull()?.let { detalhe?.episodios?.get(it)?.firstOrNull() }
    // Abre na temporada de onde parou: na primeira, o episodio de retomar nem aparecia
    val temporadaAtual = when {
        temporada in temporadas -> temporada
        retomar != null && retomar.temporada in temporadas -> retomar.temporada
        else -> temporadas.firstOrNull() ?: -1
    }
    val episodios = detalhe?.episodios?.get(temporadaAtual).orEmpty()
    val escopo = rememberCoroutineScope()
    val estadoEpisodios = remember(temporadaAtual) { LazyListState() }
    // Ultimo episodio focado na temporada; comeca no de retomar (ou no primeiro)
    var ultimoEpisodio by remember(temporadaAtual, retomar?.id) {
        mutableIntStateOf(episodios.indexOfFirst { it.id == retomar?.id }.coerceAtLeast(0))
    }
    val focoEpisodio = remember { FocusRequester() }
    val focoTemporada = remember { FocoInicial() }
    var minhaListaFocada by remember { mutableStateOf(false) }

    // A busca de foco do Compose escolhe o item da mesma altura; as setas entre as colunas vao ao item certo
    fun irParaEpisodios(): Boolean {
        if (episodios.isEmpty()) return false
        val alvo = ultimoEpisodio.coerceIn(0, episodios.lastIndex)
        escopo.launch {
            if (estadoEpisodios.layoutInfo.visibleItemsInfo.none { it.index == alvo }) estadoEpisodios.scrollToItem(alvo)
            withFrameNanos { }
            runCatching { focoEpisodio.requestFocus() }
        }
        return true
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AsyncImage(model = detalhe?.fundo ?: serie.capa, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF20E0F12), Color(0xE60E0F12), Color(0x800E0F12)))))
        Row(modifier = Modifier.fillMaxSize().padding(start = 56.dp, top = 48.dp, end = 32.dp, bottom = 32.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(460.dp)
                    // Do ultimo botao, a direita vai ao episodio de retomar e nao ao da mesma altura
                    .onPreviewKeyEvent { evento ->
                        evento.type == KeyEventType.KeyDown && evento.key == Key.DirectionRight &&
                            minhaListaFocada && irParaEpisodios()
                    },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(serie.nome, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 2)
                val linha = listOfNotNull(serie.ano, serie.nota?.let { "★ %.1f".format(it) }, detalhe?.genero).joinToString("  ·  ")
                if (linha.isNotEmpty()) {
                    Text(linha, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
                detalhe?.sinopse?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
                }
                erro?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp)) }
                Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        retomar != null && ultimo != null -> Button(
                            onClick = { aoAssistir(retomar, ultimo.posicaoMs) },
                            modifier = Modifier.focusRequester(focoPrincipal),
                        ) { Text("Continuar T${retomar.temporada}:E${retomar.numero} de ${tempo(ultimo.posicaoMs)}") }
                        primeiro != null -> Button(
                            onClick = { aoAssistir(primeiro, 0L) },
                            modifier = Modifier.focusRequester(focoPrincipal),
                        ) { Text("Assistir T${primeiro.temporada}:E${primeiro.numero}") }
                    }
                    OutlinedButton(onClick = aoFavoritar, modifier = Modifier.onFocusChanged { minhaListaFocada = it.isFocused }) { Text(if (ehFavorito) "✓ Na minha lista" else "+ Minha lista") }
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(start = 40.dp)) {
                if (temporadas.size > 1) {
                    // A temporada aberta (a de retomar pode ser a 5a) fica sempre a vista
                    val estadoTemporadas = rememberLazyListState()
                    LaunchedEffect(temporadaAtual) {
                        val indice = temporadas.indexOf(temporadaAtual)
                        val visivel = estadoTemporadas.layoutInfo.visibleItemsInfo.any {
                            it.index == indice && it.offset >= 0 && it.offset + it.size <= estadoTemporadas.layoutInfo.viewportEndOffset
                        }
                        if (indice >= 0 && !visivel) estadoTemporadas.scrollToItem(indice)
                    }
                    LazyRow(state = estadoTemporadas, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        items(temporadas) { numero ->
                            ItemFoco(
                                aoClicar = { temporada = numero },
                                selecionado = numero == temporadaAtual,
                                aoFocar = { temporada = numero },
                                modifier = Modifier.width(150.dp).then(
                                    if (numero == temporadaAtual) Modifier.focoInicial(focoTemporada) else Modifier,
                                ),
                            ) { focado -> Text("Temporada $numero", color = corDoTexto(focado)) }
                        }
                    }
                }
                LazyColumn(
                    state = estadoEpisodios,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.onPreviewKeyEvent { evento ->
                        if (evento.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (evento.key) {
                            // A esquerda volta ao botao principal
                            Key.DirectionLeft -> runCatching { focoPrincipal.requestFocus() }.isSuccess
                            // Do primeiro episodio, subir vai a temporada atual: a mais proxima trocaria a temporada
                            Key.DirectionUp -> ultimoEpisodio == 0 && focoTemporada.composto &&
                                runCatching { focoTemporada.requester.requestFocus() }.isSuccess
                            else -> false
                        }
                    },
                ) {
                    itemsIndexed(episodios, key = { _, it -> it.id }) { indice, episodio ->
                        val parado = progressos[episodio.id]
                        val ehUltimo by remember(indice) { derivedStateOf { ultimoEpisodio == indice } }
                        ItemFoco(
                            aoClicar = { aoAssistir(episodio, parado?.posicaoMs ?: 0L) },
                            altura = 56.dp,
                            aoFocar = { ultimoEpisodio = indice },
                            modifier = if (ehUltimo) Modifier.focusRequester(focoEpisodio) else Modifier,
                        ) { focado ->
                            Text("E${episodio.numero}", color = corDoTexto(focado), modifier = Modifier.width(48.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(episodio.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis, color = corDoTexto(focado))
                                val extra = listOfNotNull(
                                    episodio.duracaoSegundos?.takeIf { it > 0 }?.let { "${it / 60} min" },
                                    parado?.let { "parou em ${tempo(it.posicaoMs)}" },
                                ).joinToString("  ·  ")
                                if (extra.isNotEmpty()) {
                                    Text(extra, style = MaterialTheme.typography.bodySmall, color = if (focado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (proximo != null) {
            AvisoProximo(proximo, aoAssistir, aoCancelarProximo, Modifier.align(Alignment.BottomEnd))
        }
    }
    LaunchedEffect(retomar?.id, primeiro?.id, proximo) { if (proximo == null) runCatching { focoPrincipal.requestFocus() } }
}

/** "Proximo episodio em 10 s", com Assistir agora e Cancelar. */
@Composable
private fun AvisoProximo(proximo: Episodio, aoAssistir: (Episodio, Long) -> Unit, aoCancelar: () -> Unit, modifier: Modifier) {
    var restantes by remember(proximo.id) { mutableIntStateOf(10) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(proximo.id) {
        runCatching { foco.requestFocus() }
        while (restantes > 0) {
            delay(1_000)
            restantes--
        }
        aoAssistir(proximo, 0L)
    }
    Column(
        modifier = modifier.padding(32.dp).width(420.dp).background(Color(0xF20E0F12), RoundedCornerShape(12.dp)).padding(24.dp),
    ) {
        Text("Próximo episódio em $restantes s", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "T${proximo.temporada}:E${proximo.numero}  ${proximo.titulo}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { aoAssistir(proximo, 0L) }, modifier = Modifier.focusRequester(foco)) { Text("Assistir agora") }
            OutlinedButton(onClick = aoCancelar) { Text("Cancelar") }
        }
    }
}

@Composable
private fun corDoTexto(focado: Boolean) =
    if (focado) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
