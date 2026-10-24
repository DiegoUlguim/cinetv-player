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

@OptIn(UnstableApi::class)
@Composable
fun TelaPlayerSobDemanda(
    player: ExoPlayer?,
    titulo: String,
    posicao: Long,
    duracao: Long,
    tocando: Boolean,
    barraVisivel: Boolean,
    botoesVisiveis: Boolean,
    erro: String?,
    versaoTrilhas: Int,
    aoTrocarAudio: () -> Unit,
    aoTrocarLegenda: () -> Unit,
) {
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
        if (erro != null) {
            Text(
                erro,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center).background(Color(0xCC0E0F12), RoundedCornerShape(8.dp)).padding(24.dp),
            )
        }
        if (barraVisivel || botoesVisiveis) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0E0F12))))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (tocando) "▶" else "❚❚",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        titulo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    Text(
                        "${tempo(posicao)} / ${tempo(duracao)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val fracao = if (duracao > 0) (posicao.toFloat() / duracao).coerceIn(0f, 1f) else 0f
                Box(modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fracao).background(MaterialTheme.colorScheme.primary))
                }
                if (botoesVisiveis && player != null) {
                    BotoesTrilhas(player, versaoTrilhas, aoTrocarAudio, aoTrocarLegenda)
                } else {
                    Text(
                        "OK pausa · ◀ ▶ volta e avança · ▼ áudio e legenda",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BotoesTrilhas(player: ExoPlayer, versaoTrilhas: Int, aoTrocarAudio: () -> Unit, aoTrocarLegenda: () -> Unit) {
    val foco = remember { FocusRequester() }
    val audio = remember(versaoTrilhas) { Trilhas.opcoes(player, C.TRACK_TYPE_AUDIO) }
    val legendas = remember(versaoTrilhas) { Trilhas.opcoes(player, C.TRACK_TYPE_TEXT) }
    if (audio.size <= 1 && legendas.isEmpty()) {
        Text(
            "Este título não tem outras faixas de áudio nem legendas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
        return
    }
    Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (audio.size > 1) {
            Button(onClick = aoTrocarAudio, modifier = Modifier.focusRequester(foco)) {
                Text("Áudio: ${Trilhas.atual(player, C.TRACK_TYPE_AUDIO)?.rotulo ?: "padrão"}")
            }
        }
        if (legendas.isNotEmpty()) {
            Button(onClick = aoTrocarLegenda, modifier = if (audio.size > 1) Modifier else Modifier.focusRequester(foco)) {
                Text("Legenda: ${Trilhas.atual(player, C.TRACK_TYPE_TEXT)?.rotulo ?: "desligada"}")
            }
        }
    }
    // So quando os botoes aparecem: pedir foco a cada troca de trilha jogava o foco da Legenda para o Audio
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
}

/** 1:05:09 ou 5:09. */
fun tempo(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val horas = total / 3600
    val minutos = (total % 3600) / 60
    val segundos = total % 60
    return if (horas > 0) "%d:%02d:%02d".format(horas, minutos, segundos) else "%d:%02d".format(minutos, segundos)
}
