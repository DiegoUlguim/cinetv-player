package br.projetor.cinetv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import br.projetor.cinetv.AplicacaoCineTV
import br.projetor.cinetv.dados.GuiaCanal
import br.projetor.cinetv.dados.PreferenciaQualidade
import br.projetor.cinetv.dados.Qualidade
import br.projetor.cinetv.dados.Qualidades
import br.projetor.cinetv.dados.Registro
import br.projetor.cinetv.dados.banco.Canal
import br.projetor.cinetv.dados.xtream.ClienteXtream
import br.projetor.cinetv.ui.TemaCineTV
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Player de canais ao vivo.
 * Conta de conexao unica: o fluxo anterior e fechado antes de abrir o proximo, nada e pre-carregado,
 * e tudo e liberado no onStop (HOME, Projectivy, desligamento) para nao prender a vaga do Firestick.
 */
@OptIn(UnstableApi::class)
class AtividadePlayerAoVivo : ComponentActivity() {

    private val aplicacao get() = application as AplicacaoCineTV
    private lateinit var cliente: ClienteXtream
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var abertura: Job? = null
    private var ocultarNome: Job? = null
    private var momentoTecla = 0L
    private var tentativas = 0
    private var formato = "ts"

    // Estado lido pela tela
    private var canais by mutableStateOf<List<Canal>>(emptyList())
    private var indice by mutableIntStateOf(0)
    private var indiceAnterior by mutableIntStateOf(-1)
    private var faixaVisivel by mutableStateOf(false)
    private var listaVisivel by mutableStateOf(false)
    private var nomeVisivel by mutableStateOf(true)
    private var erro by mutableStateOf<String?>(null)
    private var versaoTrilhas by mutableIntStateOf(0)
    private var guiaAtual by mutableStateOf<GuiaCanal?>(null)
    private var ehFavorito by mutableStateOf(false)
    private var consultaGuia: Job? = null

    // Variantes de qualidade (FHD/HD/SD) do canal atual e a que esta tocando
    private var variantes: List<Canal> = emptyList()
    private var indiceVariante = -1
    private var rebaixadoNesteCanal = false
    private var jaMostrouQuadro = false
    private val travamentos = ArrayDeque<Long>()
    private var qualidadeAtual by mutableStateOf<String?>(null)
    private var aviso by mutableStateOf<String?>(null)
    private var ocultarAviso: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cliente = aplicacao.cliente() ?: run { finish(); return }
        formato = intent.getStringExtra(EXTRA_FORMATO) ?: "ts"
        val canalId = intent.getIntExtra(EXTRA_CANAL, -1)
        momentoTecla = SystemClock.elapsedRealtime()

        setContent {
            TemaCineTV {
                TelaPlayerAoVivo(
                    player = player,
                    canais = canais,
                    indice = indice,
                    temAnterior = indiceAnterior >= 0,
                    nomeVisivel = nomeVisivel,
                    faixaVisivel = faixaVisivel,
                    listaVisivel = listaVisivel,
                    erro = erro,
                    versaoTrilhas = versaoTrilhas,
                    guia = guiaAtual,
                    qualidade = qualidadeAtual,
                    aviso = aviso,
                    ehFavorito = ehFavorito,
                    aoFavoritar = {
                        canais.getOrNull(indice)?.let { canal ->
                            lifecycleScope.launch {
                                val ids = aplicacao.banco.catalogo().variantes(canal.grupo).map { it.id }
                                aplicacao.usuario.alternarFavoritoCanal(canal.id, ids, canal.nomeBase, canal.logo)
                            }
                        }
                    },
                    aoCanalAnterior = { if (indiceAnterior >= 0) irPara(indiceAnterior, imediato = true) },
                    aoTrocarAudio = { player?.let { Trilhas.avancar(it, C.TRACK_TYPE_AUDIO) } },
                    aoTrocarLegenda = { player?.let { Trilhas.avancar(it, C.TRACK_TYPE_TEXT) } },
                    aoEscolherDaLista = { novo -> listaVisivel = false; irPara(novo, imediato = true) },
                )
            }
        }

        lifecycleScope.launch {
            // Aberto pelo grupo Favoritos: o zapping percorre so os favoritos
            val doGrupo = if (intent.getBooleanExtra(EXTRA_FAVORITOS, false)) {
                val ids = aplicacao.usuario.favoritos().doTipo("canal").first().map { it.idConteudo }
                aplicacao.banco.catalogo().canaisPorIds(ids)
            } else {
                aplicacao.banco.catalogo().canaisDoGrupoDe(canalId)
            }
            canais = doGrupo
            indice = doGrupo.indexOfFirst { it.id == canalId }.coerceAtLeast(0)
            if (player != null) abrir()
        }
        lifecycleScope.launch {
            snapshotFlow { canais.getOrNull(indice) }.collectLatest { canal ->
                if (canal == null) return@collectLatest
                // Favorito marcado em qualquer variante do grupo conta (inclusive os gravados antes do agrupamento)
                val chaves = aplicacao.banco.catalogo().variantes(canal.grupo).map { "canal:${it.id}" } + "canal:${canal.id}"
                aplicacao.usuario.favoritos().existeAlgum(chaves).collect { ehFavorito = it }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player = FabricaPlayer.aoVivo(this, aplicacao.http, aplicacao.medidorBanda).also { it.addListener(ouvinte) }
        if (canais.isNotEmpty()) abrir()
    }

    override fun onStop() {
        abertura?.cancel()
        player?.release()
        player = null
        val http = aplicacao.http
        Thread { http.connectionPool.evictAll() }.start()
        super.onStop()
    }

    override fun dispatchKeyEvent(evento: KeyEvent): Boolean {
        if (evento.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(evento)
        if (evento.keyCode == KeyEvent.KEYCODE_BACK && (faixaVisivel || listaVisivel)) {
            faixaVisivel = false
            listaVisivel = false
            return true
        }
        if (faixaVisivel || listaVisivel) return super.dispatchKeyEvent(evento)
        when (evento.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> irPara(indice - 1, imediato = false)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> irPara(indice + 1, imediato = false)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> listaVisivel = true
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { faixaVisivel = true; atualizarGuia() }
            else -> return super.dispatchKeyEvent(evento)
        }
        return true
    }

    /**
     * Troca de canal. No zapping (imediato = false) o nome muda na hora e o fluxo so abre
     * depois que as teclas param, para nao abrir e derrubar uma conexao a cada toque.
     */
    private fun irPara(novo: Int, imediato: Boolean) {
        if (canais.isEmpty()) return
        val destino = Math.floorMod(novo, canais.size)
        if (destino != indice) indiceAnterior = indice
        indice = destino
        tentativas = 0
        rebaixadoNesteCanal = false
        momentoTecla = SystemClock.elapsedRealtime()
        mostrarNome()
        abertura?.cancel()
        abertura = lifecycleScope.launch {
            if (!imediato) delay(ESPERA_ZAPPING_MS)
            abrirAgora()
        }
    }

    private fun abrir() {
        abertura?.cancel()
        abertura = lifecycleScope.launch { abrirAgora() }
    }

    private suspend fun abrirAgora() {
        val atual = player ?: return
        val canal = canais.getOrNull(indice) ?: return
        erro = null
        atual.stop()
        atual.clearMediaItems()
        // Fecha as conexoes do fluxo anterior antes de pedir o proximo; fechar TLS faz rede, entao fora da thread da tela
        withContext(Dispatchers.IO) { aplicacao.http.connectionPool.evictAll() }
        if (player !== atual) return
        val variante = escolherVariante(canal)
        val item = MediaItem.Builder()
            .setUri(cliente.urlAoVivo(variante.id, formato))
            .setMimeType(if (formato == "m3u8") MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP2T)
            .build()
        jaMostrouQuadro = false
        travamentos.clear()
        atual.setMediaItem(item)
        atual.prepare()
        atual.playWhenReady = true
        mostrarNome()
        Registro.info("Abrindo canal ${variante.id} ($formato, ${variante.qualidade ?: "unica"})")
    }

    /**
     * Variante a tocar: a regra (automatica pela banda ou fixa dos Ajustes) escolhe entre FHD/HD/SD;
     * depois de um rebaixamento por travamento, fica na variante rebaixada ate trocar de canal.
     */
    private suspend fun escolherVariante(canal: Canal): Canal {
        if (variantes.firstOrNull()?.grupo != canal.grupo) {
            variantes = aplicacao.banco.catalogo().variantes(canal.grupo).ifEmpty { listOf(canal) }
            rebaixadoNesteCanal = false
        }
        val qualidades = variantes.map { v -> v.qualidade?.let { runCatching { Qualidade.valueOf(it) }.getOrNull() } }
        if (!rebaixadoNesteCanal) {
            val preferencia = aplicacao.preferenciaQualidade
            val estimativa = aplicacao.estimativaBanda
            val alvo = Qualidades.alvo(preferencia, estimativa)
            indiceVariante = Qualidades.escolher(qualidades, alvo).coerceAtLeast(0)
            Registro.info("Qualidade: ${preferencia.name}, banda ${estimativa?.let { it / 1000 }} kbps, alvo ${alvo.name}")
        }
        val escolhida = variantes[indiceVariante.coerceIn(variantes.indices)]
        qualidadeAtual = escolhida.qualidade?.let { q ->
            val rotulo = runCatching { Qualidade.valueOf(q).rotulo }.getOrDefault(q)
            if (aplicacao.preferenciaQualidade == PreferenciaQualidade.AUTOMATICA && !rebaixadoNesteCanal) "$rotulo · automática" else rotulo
        }
        return escolhida
    }

    /** Dois travamentos em 60 s depois de o canal ja ter aparecido: desce uma qualidade. */
    private fun registrarTravamento() {
        val agora = SystemClock.elapsedRealtime()
        travamentos.addLast(agora)
        while (travamentos.isNotEmpty() && agora - travamentos.first() > JANELA_TRAVAMENTO_MS) travamentos.removeFirst()
        if (travamentos.size < 2) return
        val qualidades = variantes.map { v -> v.qualidade?.let { runCatching { Qualidade.valueOf(it) }.getOrNull() } }
        val abaixo = Qualidades.abaixo(qualidades, indiceVariante) ?: return
        indiceVariante = abaixo
        rebaixadoNesteCanal = true
        val rotulo = qualidades[abaixo]?.rotulo ?: "menor"
        Registro.info("Travou ${travamentos.size}x em 60 s: descendo para $rotulo")
        mostrarAviso("Internet lenta: qualidade reduzida para $rotulo")
        abrir()
    }

    private fun mostrarAviso(texto: String) {
        aviso = texto
        ocultarAviso?.cancel()
        ocultarAviso = lifecycleScope.launch {
            delay(AVISO_VISIVEL_MS)
            aviso = null
        }
    }

    /** Guia do canal em foco: o do cache aparece na hora; a consulta so sai depois que o zapping para. */
    private fun atualizarGuia() {
        val canal = canais.getOrNull(indice) ?: return
        guiaAtual = aplicacao.guia.emCache(canal)
        consultaGuia?.cancel()
        consultaGuia = lifecycleScope.launch {
            delay(ESPERA_ZAPPING_MS)
            guiaAtual = aplicacao.guia.obter(canal)
        }
    }

    private fun mostrarNome() {
        atualizarGuia()
        nomeVisivel = true
        ocultarNome?.cancel()
        ocultarNome = lifecycleScope.launch {
            delay(NOME_VISIVEL_MS)
            nomeVisivel = false
        }
    }

    private val ouvinte = object : Player.Listener {
        override fun onPlaybackStateChanged(estado: Int) {
            if (estado == Player.STATE_BUFFERING && jaMostrouQuadro) registrarTravamento()
        }

        override fun onRenderedFirstFrame() {
            jaMostrouQuadro = true
            if (tentativas > 0) Registro.info("Canal abriu depois de $tentativas nova(s) tentativa(s)")
            Registro.info("Troca de canal: ${SystemClock.elapsedRealtime() - momentoTecla} ms da tecla ao 1o quadro ($formato)")
        }

        override fun onTracksChanged(tracks: Tracks) {
            versaoTrilhas++
            player?.let { Trilhas.registrarEstado(it, C.TRACK_TYPE_TEXT, "trilhas mudaram") }
        }

        override fun onPlayerError(falha: PlaybackException) {
            Registro.aviso("Falha no canal ${canais.getOrNull(indice)?.id}: ${falha.errorCodeName}", falha)
            // O decodificador AAC do aparelho as vezes falha ao entrar no meio do fluxo ao vivo;
            // reabrir o mesmo canal costuma funcionar (medido em 24/10)
            if (falha.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED && tentativas < MAX_TENTATIVAS) {
                tentativas++
                abrir()
                return
            }
            erro = when (falha.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "O servidor recusou este canal. Ele pode estar fora do ar ou a conta em uso em outro aparelho"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sem conexão com o servidor"
                else -> "Não foi possível abrir este canal"
            }
        }
    }

    companion object {
        private const val EXTRA_CANAL = "canal"
        private const val EXTRA_FORMATO = "formato"
        private const val ESPERA_ZAPPING_MS = 400L
        private const val NOME_VISIVEL_MS = 3_000L
        private const val MAX_TENTATIVAS = 2
        private const val JANELA_TRAVAMENTO_MS = 60_000L
        private const val AVISO_VISIVEL_MS = 5_000L

        private const val EXTRA_FAVORITOS = "favoritos"

        fun abrir(contexto: Context, canalId: Int, deFavoritos: Boolean = false) {
            contexto.startActivity(
                Intent(contexto, AtividadePlayerAoVivo::class.java).putExtra(EXTRA_CANAL, canalId).putExtra(EXTRA_FAVORITOS, deFavoritos),
            )
        }
    }
}
