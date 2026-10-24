package br.projetor.cinetv.dados

import br.projetor.cinetv.dados.banco.CatalogoDao
import br.projetor.cinetv.dados.xtream.ClienteXtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Mede a internet baixando o comeco de um filme. O medidor de banda do player nao serve para isso:
 * no ao vivo a CDN entrega o video no ritmo da transmissao, entao ele mede o bitrate do canal (~2,4 Mbps
 * medidos em 24/10), e a qualidade automatica cairia para SD e nunca mais subiria.
 * Filme vem por Range e sem ritmo: o tempo do download e a velocidade real ate a CDN.
 */
class TesteVelocidade(private val http: OkHttpClient, private val dao: CatalogoDao) {

    /** Velocidade em bits por segundo, ou null se nao deu para medir. */
    suspend fun medir(cliente: ClienteXtream): Long? = withContext(Dispatchers.IO) {
        val filme = dao.algumFilme() ?: return@withContext null
        val requisicao = Request.Builder()
            .url(cliente.urlFilme(filme.id, filme.extensao))
            .header("Range", "bytes=0-${TAMANHO_BYTES - 1}")
            .build()
        runCatching {
            http.newCall(requisicao).execute().use { resposta ->
                if (!resposta.isSuccessful) return@use null
                val entrada = resposta.body!!.byteStream()
                val bloco = ByteArray(64 * 1024)
                // Conta a partir do primeiro byte: conexao, TLS e redirecionamento nao entram na conta
                var lidos = entrada.read(bloco).toLong().coerceAtLeast(0)
                val inicio = System.nanoTime()
                var lidosDepois = 0L
                while (true) {
                    val n = entrada.read(bloco)
                    if (n < 0) break
                    lidosDepois += n
                    lidos += n
                }
                val segundos = (System.nanoTime() - inicio) / 1e9
                if (lidosDepois < MINIMO_BYTES || segundos <= 0) null else (lidosDepois * 8 / segundos).toLong()
            }
        }.onFailure { Registro.aviso("Teste de velocidade falhou", it) }.getOrNull()
    }

    companion object {
        private const val TAMANHO_BYTES = 4L * 1024 * 1024
        private const val MINIMO_BYTES = 1L * 1024 * 1024
    }
}
