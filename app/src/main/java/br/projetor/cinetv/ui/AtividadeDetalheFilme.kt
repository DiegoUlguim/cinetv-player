package br.projetor.cinetv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import br.projetor.cinetv.dados.banco.DetalheFilme
import br.projetor.cinetv.dados.banco.Filme
import br.projetor.cinetv.dados.usuario.Progresso
import br.projetor.cinetv.dados.xtream.Mapeadores
import br.projetor.cinetv.player.AtividadePlayerSobDemanda
import br.projetor.cinetv.player.tempo
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Detalhe do filme: o get_vod_info e buscado na primeira abertura e fica em cache no banco. */
class AtividadeDetalheFilme : ComponentActivity() {

    private val aplicacao get() = application as AplicacaoCineTV

    /** Muda a cada volta para a tela, para reler o progresso depois de assistir. */
    private var retomadas by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        setContent {
            TemaCineTV {
                var filme by remember { mutableStateOf<Filme?>(null) }
                var detalhe by remember { mutableStateOf<DetalheFilme?>(null) }
                var progresso by remember { mutableStateOf<Progresso?>(null) }
                LaunchedEffect(id) {
                    filme = aplicacao.banco.catalogo().filme(id)
                    detalhe = carregarDetalhe(id)
                }
                LaunchedEffect(id, retomadas) {
                    progresso = aplicacao.usuario.progresso().obter("${AtividadePlayerSobDemanda.TIPO_FILME}:$id")
                }
                val ehFavorito by remember(id) { aplicacao.usuario.favoritos().existe("filme:$id") }.collectAsState(initial = false)
                val escopo = rememberCoroutineScope()
                filme?.let { atual ->
                    TelaDetalheFilme(atual, detalhe, progresso, ehFavorito, ::assistir) {
                        escopo.launch { aplicacao.usuario.alternarFavorito("filme", atual.id, atual.nome, atual.capa) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        retomadas++
    }

    private suspend fun carregarDetalhe(id: Int): DetalheFilme? {
        val dao = aplicacao.banco.catalogo()
        dao.detalheFilme(id)?.let { return it }
        val cliente = aplicacao.cliente() ?: return null
        return try {
            val info = withContext(Dispatchers.IO) { cliente.infoFilme(id) } ?: return null
            Mapeadores.detalheFilme(id, info).also { dao.gravarDetalheFilme(it) }
        } catch (e: Exception) {
            Registro.aviso("Detalhe do filme $id indisponivel", e)
            null
        }
    }

    private fun assistir(filme: Filme, retomarEmMs: Long) {
        AtividadePlayerSobDemanda.abrir(
            this, AtividadePlayerSobDemanda.TIPO_FILME, filme.id, filme.extensao, filme.nome, filme.capa, retomarEmMs,
        )
    }

    companion object {
        private const val EXTRA_ID = "id"

        fun abrir(contexto: Context, id: Int) {
            contexto.startActivity(Intent(contexto, AtividadeDetalheFilme::class.java).putExtra(EXTRA_ID, id))
        }
    }
}

@Composable
private fun TelaDetalheFilme(
    filme: Filme,
    detalhe: DetalheFilme?,
    progresso: Progresso?,
    ehFavorito: Boolean,
    aoAssistir: (Filme, Long) -> Unit,
    aoFavoritar: () -> Unit,
) {
    val foco = remember { FocusRequester() }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AsyncImage(
            model = detalhe?.fundo ?: detalhe?.capaGrande ?: filme.capa,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Escurece da esquerda para a direita para o texto ficar legivel sobre qualquer imagem
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xF20E0F12), Color(0xCC0E0F12), Color(0x330E0F12))),
            ),
        )
        Column(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.6f).padding(start = 56.dp, top = 56.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(filme.nome, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 2)
            val linha = listOfNotNull(
                detalhe?.lancamento?.take(4) ?: filme.ano,
                detalhe?.duracaoSegundos?.takeIf { it > 0 }?.let { "${it / 3600}h ${(it % 3600) / 60}min".removePrefix("0h ") },
                filme.nota?.let { "★ %.1f".format(it) },
                detalhe?.genero,
            ).joinToString("  ·  ")
            if (linha.isNotEmpty()) {
                Text(linha, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
            detalhe?.sinopse?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            detalhe?.elenco?.let {
                Text(
                    "Elenco: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (progresso != null) {
                    Button(onClick = { aoAssistir(filme, progresso.posicaoMs) }, modifier = Modifier.focusRequester(foco)) {
                        Text("Continuar de ${tempo(progresso.posicaoMs)}")
                    }
                    OutlinedButton(onClick = { aoAssistir(filme, 0L) }) { Text("Do início") }
                } else {
                    Button(onClick = { aoAssistir(filme, 0L) }, modifier = Modifier.focusRequester(foco)) { Text("Assistir") }
                }
                OutlinedButton(onClick = aoFavoritar) { Text(if (ehFavorito) "✓ Na minha lista" else "+ Minha lista") }
            }
        }
    }
    LaunchedEffect(progresso != null) { runCatching { foco.requestFocus() } }
}
