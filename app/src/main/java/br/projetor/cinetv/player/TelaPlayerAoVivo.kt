package br.projetor.cinetv.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import br.projetor.cinetv.dados.GuiaCanal
import br.projetor.cinetv.dados.Programa
import br.projetor.cinetv.dados.banco.Canal
import br.projetor.cinetv.ui.ItemFoco
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Escurecido = Color(0xCC0E0F12)

@OptIn(UnstableApi::class)
@Composable
fun TelaPlayerAoVivo(
    player: ExoPlayer?,
    canais: List<Canal>,
    indice: Int,
    temAnterior: Boolean,
    nomeVisivel: Boolean,
    faixaVisivel: Boolean,
    listaVisivel: Boolean,
    erro: String?,
    versaoTrilhas: Int,
    guia: GuiaCanal?,
    qualidade: String?,
    aviso: String?,
    ehFavorito: Boolean,
    aoFavoritar: () -> Unit,
    aoCanalAnterior: () -> Unit,
    aoTrocarAudio: () -> Unit,
    aoTrocarLegenda: () -> Unit,
    aoEscolherDaLista: (Int) -> Unit,
) {
    val canal = canais.getOrNull(indice)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { contexto ->
                PlayerView(contexto).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (canal != null && (nomeVisivel || faixaVisivel)) {
            CabecalhoCanal(canal, indice, canais.size, guia, qualidade, Modifier.align(Alignment.TopStart))
        }
        if (aviso != null) {
            Text(
                aviso,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp).background(Escurecido, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (erro != null) {
            Text(
                erro,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center).background(Escurecido, RoundedCornerShape(8.dp)).padding(24.dp),
            )
        }
        if (faixaVisivel && player != null) {
            FaixaInformacao(
                player, temAnterior, versaoTrilhas, ehFavorito, aoFavoritar, aoCanalAnterior, aoTrocarAudio, aoTrocarLegenda,
                Modifier.align(Alignment.BottomCenter),
            )
        }
        if (listaVisivel) {
            ListaLateral(canais, indice, aoEscolherDaLista, Modifier.align(Alignment.CenterStart))
        }
    }
}

@Composable
private fun CabecalhoCanal(canal: Canal, indice: Int, total: Int, guia: GuiaCanal?, qualidade: String?, modifier: Modifier) {
    Row(
        modifier = modifier
            .padding(32.dp)
            .widthIn(max = 640.dp)
            .background(Escurecido, RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(model = canal.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp, 36.dp))
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(canal.nomeBase, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                listOfNotNull("${indice + 1} de $total", qualidade).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            guia?.agora?.let { programa ->
                Text(
                    "Agora  ${horario(programa)}  ${programa.titulo}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp),
                )
                BarraProgresso(programa, Modifier.padding(top = 4.dp))
            }
            guia?.aSeguir?.let { programa ->
                Text(
                    "A seguir  ${horario(programa)}  ${programa.titulo}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private val FormatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

private fun horario(programa: Programa): String =
    "${FormatoHora.format(Date(programa.inicio))}–${FormatoHora.format(Date(programa.fim))}"

@Composable
private fun BarraProgresso(programa: Programa, modifier: Modifier) {
    val duracao = (programa.fim - programa.inicio).coerceAtLeast(1)
    val fracao = ((System.currentTimeMillis() - programa.inicio).toFloat() / duracao).coerceIn(0f, 1f)
    Box(modifier = modifier.width(240.dp).height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fracao).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun FaixaInformacao(
    player: ExoPlayer,
    temAnterior: Boolean,
    versaoTrilhas: Int,
    ehFavorito: Boolean,
    aoFavoritar: () -> Unit,
    aoCanalAnterior: () -> Unit,
    aoTrocarAudio: () -> Unit,
    aoTrocarLegenda: () -> Unit,
    modifier: Modifier,
) {
    val foco = remember { FocusRequester() }
    // versaoTrilhas forca a leitura das trilhas atuais quando o player as troca
    val audio = remember(versaoTrilhas) { Trilhas.opcoes(player, C.TRACK_TYPE_AUDIO) }
    val legendas = remember(versaoTrilhas) { Trilhas.opcoes(player, C.TRACK_TYPE_TEXT) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0E0F12))))
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // O primeiro botao visivel recebe o foco; botao sem funcao nao aparece
        var focoUsado = false
        fun primeiro(): Modifier = if (focoUsado) Modifier else Modifier.focusRequester(foco).also { focoUsado = true }
        Button(onClick = aoFavoritar, modifier = primeiro()) {
            Text(if (ehFavorito) "★ Nos favoritos" else "☆ Favoritar")
        }
        if (temAnterior) {
            Button(onClick = aoCanalAnterior, modifier = primeiro()) { Text("Canal anterior") }
        }
        if (audio.size > 1) {
            Button(onClick = aoTrocarAudio, modifier = primeiro()) {
                Text("Áudio: ${Trilhas.atual(player, C.TRACK_TYPE_AUDIO)?.rotulo ?: "—"}")
            }
        }
        if (legendas.isNotEmpty()) {
            Button(onClick = aoTrocarLegenda, modifier = primeiro()) {
                Text("Legenda: ${Trilhas.atual(player, C.TRACK_TYPE_TEXT)?.rotulo ?: "desligada"}")
            }
        }
    }
    // So quando a faixa aparece: pedir foco a cada troca de trilha jogava o foco da Legenda para o primeiro botao
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
}

@Composable
private fun ListaLateral(canais: List<Canal>, indice: Int, aoEscolher: (Int) -> Unit, modifier: Modifier) {
    val estado = rememberLazyListState(initialFirstVisibleItemIndex = (indice - 3).coerceAtLeast(0))
    val foco = remember { FocusRequester() }
    LazyColumn(
        state = estado,
        modifier = modifier.width(380.dp).fillMaxHeight().background(Escurecido).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(canais, key = { _, c -> c.id }) { posicao, canal ->
            ItemFoco(
                aoClicar = { aoEscolher(posicao) },
                selecionado = posicao == indice,
                modifier = if (posicao == indice) Modifier.focusRequester(foco) else Modifier,
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
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
}
