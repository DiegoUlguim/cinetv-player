package br.projetor.cinetv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import br.projetor.cinetv.AplicacaoCineTV
import br.projetor.cinetv.dados.Registro
import br.projetor.cinetv.dados.usuario.Progresso
import br.projetor.cinetv.ui.TemaCineTV
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Player de filme e episodio. Guarda o ponto de parada a cada 10 s e ao sair; apaga quando o
 * conteudo termina. Esquerda/direita avancam e voltam 10 s, OK pausa, baixo mostra audio e legenda.
 */
@OptIn(UnstableApi::class)
class AtividadePlayerSobDemanda : ComponentActivity() {

    private val aplicacao get() = application as AplicacaoCineTV
    private lateinit var conteudo: Conteudo
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var gravacao: Job? = null
    private var ocultar: Job? = null
    private var retomarEm = 0L

    private var barraVisivel by mutableStateOf(true)
    private var botoesVisiveis by mutableStateOf(false)
    private var posicao by mutableLongStateOf(0L)
    private var duracao by mutableLongStateOf(0L)
    private var tocando by mutableStateOf(false)
    private var erro by mutableStateOf<String?>(null)
    private var versaoTrilhas by mutableIntStateOf(0)

    /** O que esta tocando; vem inteiro no Intent para nao depender do catalogo. */
    private data class Conteudo(
        val tipo: String, val id: Int, val extensao: String, val titulo: String, val capa: String?, val serieId: Int?,
    ) {
        val chave get() = "$tipo:$id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        conteudo = Conteudo(
            tipo = intent.getStringExtra(EXTRA_TIPO) ?: TIPO_FILME,
            id = intent.getIntExtra(EXTRA_ID, -1),
            extensao = intent.getStringExtra(EXTRA_EXTENSAO) ?: "mp4",
            titulo = intent.getStringExtra(EXTRA_TITULO).orEmpty(),
            capa = intent.getStringExtra(EXTRA_CAPA),
            serieId = intent.getIntExtra(EXTRA_SERIE, -1).takeIf { it >= 0 },
        )
        retomarEm = intent.getLongExtra(EXTRA_RETOMAR_EM, 0L)

        setContent {
            TemaCineTV {
                TelaPlayerSobDemanda(
                    player = player,
                    titulo = conteudo.titulo,
                    posicao = posicao,
                    duracao = duracao,
                    tocando = tocando,
                    barraVisivel = barraVisivel || !tocando,
                    botoesVisiveis = botoesVisiveis,
                    erro = erro,
                    versaoTrilhas = versaoTrilhas,
                    aoTrocarAudio = { player?.let { Trilhas.avancar(it, C.TRACK_TYPE_AUDIO) } },
                    aoTrocarLegenda = { player?.let { Trilhas.avancar(it, C.TRACK_TYPE_TEXT) } },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val cliente = aplicacao.cliente() ?: run { finish(); return }
        val url = if (conteudo.tipo == TIPO_EPISODIO) cliente.urlEpisodio(conteudo.id, conteudo.extensao)
        else cliente.urlFilme(conteudo.id, conteudo.extensao)
        player = FabricaPlayer.sobDemanda(this, aplicacao.http, aplicacao.medidorBanda).also {
            it.addListener(ouvinte)
            it.setMediaItem(MediaItem.fromUri(url), retomarEm)
            it.prepare()
            it.playWhenReady = true
        }
        Registro.info("Abrindo ${conteudo.chave} em ${retomarEm / 1000} s")
        mostrarBarra()
        gravacao = lifecycleScope.launch {
            var segundos = 0
            while (isActive) {
                delay(1_000)
                player?.let { posicao = it.currentPosition; duracao = it.duration.coerceAtLeast(0) }
                if (++segundos % 10 == 0) gravarProgresso()
            }
        }
    }

    override fun onStop() {
        gravacao?.cancel()
        player?.let { retomarEm = it.currentPosition }
        // Grava antes de liberar: e a ultima chance de saber onde parou
        runBlocking { gravarProgresso() }
        player?.release()
        player = null
        val http = aplicacao.http
        Thread { http.connectionPool.evictAll() }.start()
        super.onStop()
    }

    private suspend fun gravarProgresso() {
        val atual = player ?: return
        val pos = atual.currentPosition
        val total = atual.duration
        if (total <= 0 || pos < MINIMO_PARA_GUARDAR_MS) return
        val dao = aplicacao.usuario.progresso()
        if (pos >= total * FRACAO_TERMINADO) {
            dao.apagar(conteudo.chave)
            return
        }
        dao.gravar(
            Progresso(
                chave = conteudo.chave, tipo = conteudo.tipo, idConteudo = conteudo.id, titulo = conteudo.titulo,
                capa = conteudo.capa, extensao = conteudo.extensao, posicaoMs = pos, duracaoMs = total,
                atualizadoEm = System.currentTimeMillis(), serieId = conteudo.serieId,
            ),
        )
    }

    override fun dispatchKeyEvent(evento: KeyEvent): Boolean {
        if (evento.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(evento)
        if (botoesVisiveis) {
            if (evento.keyCode == KeyEvent.KEYCODE_BACK || evento.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                botoesVisiveis = false
                return true
            }
            return super.dispatchKeyEvent(evento)
        }
        val atual = player ?: return super.dispatchKeyEvent(evento)
        when (evento.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> pular(atual, -passo(evento))
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> pular(atual, passo(evento))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                atual.playWhenReady = !atual.playWhenReady
                mostrarBarra()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> { botoesVisiveis = true; mostrarBarra() }
            KeyEvent.KEYCODE_DPAD_UP -> mostrarBarra()
            else -> return super.dispatchKeyEvent(evento)
        }
        return true
    }

    /** Segurar a tecla acelera: 10 s por toque, 30 s depois de alguns repetidos. */
    private fun passo(evento: KeyEvent): Long = if (evento.repeatCount >= 5) 30_000L else 10_000L

    private fun pular(atual: ExoPlayer, deltaMs: Long) {
        val destino = (atual.currentPosition + deltaMs).coerceIn(0, atual.duration.coerceAtLeast(0))
        atual.seekTo(destino)
        posicao = destino
        mostrarBarra()
    }

    private fun mostrarBarra() {
        barraVisivel = true
        ocultar?.cancel()
        ocultar = lifecycleScope.launch {
            delay(BARRA_VISIVEL_MS)
            barraVisivel = false
        }
    }

    private val ouvinte = object : Player.Listener {
        override fun onIsPlayingChanged(estaTocando: Boolean) {
            tocando = estaTocando
        }

        override fun onTracksChanged(tracks: Tracks) {
            versaoTrilhas++
            player?.let { Trilhas.registrarEstado(it, C.TRACK_TYPE_TEXT, "trilhas mudaram") }
        }

        override fun onPlaybackStateChanged(estado: Int) {
            if (estado == Player.STATE_ENDED) {
                lifecycleScope.launch { aplicacao.usuario.progresso().apagar(conteudo.chave) }
                // Quem abriu (detalhe da serie) usa o resultado para sugerir o proximo episodio
                setResult(RESULT_OK, Intent().putExtra(RESULTADO_TERMINOU, conteudo.id))
                finish()
            }
        }

        override fun onPlayerError(falha: PlaybackException) {
            Registro.aviso("Falha em ${conteudo.chave}: ${falha.errorCodeName}", falha)
            erro = when (falha.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "O servidor recusou este título. Ele pode ter saído do catálogo ou a conta estar em uso em outro aparelho"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sem conexão com o servidor"
                else -> "Não foi possível reproduzir este título"
            }
        }
    }

    companion object {
        const val TIPO_FILME = "filme"
        const val TIPO_EPISODIO = "episodio"
        private const val EXTRA_TIPO = "tipo"
        private const val EXTRA_ID = "id"
        private const val EXTRA_EXTENSAO = "extensao"
        private const val EXTRA_TITULO = "titulo"
        private const val EXTRA_CAPA = "capa"
        private const val EXTRA_SERIE = "serie"
        private const val EXTRA_RETOMAR_EM = "retomar_em"
        private const val BARRA_VISIVEL_MS = 4_000L
        private const val MINIMO_PARA_GUARDAR_MS = 30_000L
        private const val FRACAO_TERMINADO = 0.95

        /** Extra do resultado: id do conteudo que terminou de tocar. */
        const val RESULTADO_TERMINOU = "terminou"

        fun intencao(
            contexto: Context, tipo: String, id: Int, extensao: String, titulo: String, capa: String?,
            retomarEmMs: Long, serieId: Int? = null,
        ): Intent = Intent(contexto, AtividadePlayerSobDemanda::class.java)
            .putExtra(EXTRA_TIPO, tipo).putExtra(EXTRA_ID, id).putExtra(EXTRA_EXTENSAO, extensao)
            .putExtra(EXTRA_TITULO, titulo).putExtra(EXTRA_CAPA, capa).putExtra(EXTRA_RETOMAR_EM, retomarEmMs)
            .apply { if (serieId != null) putExtra(EXTRA_SERIE, serieId) }

        fun abrir(
            contexto: Context, tipo: String, id: Int, extensao: String, titulo: String, capa: String?, retomarEmMs: Long,
        ) {
            contexto.startActivity(intencao(contexto, tipo, id, extensao, titulo, capa, retomarEmMs))
        }
    }
}
