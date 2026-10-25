package br.projetor.cinetv.dados

import android.util.Base64
import android.util.LruCache
import br.projetor.cinetv.dados.banco.Canal
import br.projetor.cinetv.dados.xtream.ClienteXtream
import br.projetor.cinetv.dados.xtream.ErroServidor
import br.projetor.cinetv.dados.xtream.LeitorJson
import br.projetor.cinetv.dados.xtream.longo
import br.projetor.cinetv.dados.xtream.texto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Programa do guia, com inicio e fim em epoch (ms). */
data class Programa(val titulo: String, val inicio: Long, val fim: Long)

data class GuiaCanal(val agora: Programa?, val aSeguir: Programa?)

/**
 * "Agora / a seguir" pelo get_short_epg, um canal por vez.
 * Para nao disparar o Cloudflare com a rolagem: so canais com epg_channel_id (1.020 de 1.777),
 * no maximo 2 consultas simultaneas, e cache ate o programa atual acabar.
 */
class RepositorioGuia(private val cliente: () -> ClienteXtream?) {

    private class Entrada(val guia: GuiaCanal?, val validoAte: Long)

    private val cache = LruCache<Int, Entrada>(400)
    private val simultaneas = Semaphore(2)

    /** Valor ja em cache, sem rede (para a primeira composicao do item). */
    fun emCache(canal: Canal): GuiaCanal? =
        cache.get(canal.id)?.takeIf { System.currentTimeMillis() < it.validoAte }?.guia

    suspend fun obter(canal: Canal): GuiaCanal? {
        if (canal.epgId == null) return null
        cache.get(canal.id)?.let { if (System.currentTimeMillis() < it.validoAte) return it.guia }
        val api = cliente() ?: return null
        return simultaneas.withPermit {
            // Outra consulta do mesmo canal pode ter terminado enquanto esta esperava a vez
            cache.get(canal.id)?.let { if (System.currentTimeMillis() < it.validoAte) return@withPermit it.guia }
            val agora = System.currentTimeMillis()
            val entrada = try {
                val guia = withContext(Dispatchers.IO) { consultar(api, canal.id) }
                // null: o servidor diz que o canal nao tem guia, como no 404
                if (guia == null) Entrada(null, agora + SEM_GUIA_MS)
                else Entrada(guia, guia.agora?.fim ?: (agora + SEM_PROGRAMA_MS))
            } catch (e: CancellationException) {
                // Item saiu da tela no meio da consulta: nada vai para o cache
                throw e
            } catch (e: ErroServidor) {
                // 404 e a resposta do servidor para canal sem guia: nao e falha
                if (e.codigoHttp == 404) {
                    Entrada(null, agora + SEM_GUIA_MS)
                } else {
                    Registro.aviso("Guia do canal ${canal.id} indisponivel", e)
                    Entrada(null, agora + APOS_FALHA_MS)
                }
            } catch (e: Exception) {
                Registro.aviso("Guia do canal ${canal.id} indisponivel", e)
                Entrada(null, agora + APOS_FALHA_MS)
            }
            cache.put(canal.id, entrada)
            entrada.guia
        }
    }

    private fun consultar(api: ClienteXtream, canalId: Int): GuiaCanal? {
        Registro.info("Guia: consulta do canal $canalId")
        val texto = api.abrir("get_short_epg", mapOf("stream_id" to canalId.toString(), "limit" to "4"))
            .use { it.readBytes().toString(Charsets.UTF_8) }
        // Ha servidor que responde [] (200) para canal sem guia, em vez de 404 ou {"epg_listings": []}
        val raiz = LeitorJson.json.parseToJsonElement(texto) as? JsonObject ?: return null
        val lista = raiz["epg_listings"] as? JsonArray
        val agora = System.currentTimeMillis()
        val programas = lista.orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::programa) }
            .filter { it.fim > agora }
            .sortedBy { it.inicio }
        val atual = programas.firstOrNull { it.inicio <= agora }
        val seguinte = programas.firstOrNull { it.inicio >= (atual?.fim ?: agora) && it != atual }
        return GuiaCanal(atual, seguinte)
    }

    private fun programa(item: JsonObject): Programa? {
        val inicio = item.longo("start_timestamp") ?: return null
        val fim = item.longo("stop_timestamp") ?: return null
        val titulo = item.texto("title")?.let(::decodificar) ?: return null
        return Programa(titulo, inicio * 1000, fim * 1000)
    }

    /** Titulos do Xtream vem em base64 (UTF-8); se nao for base64 valido, usa o texto como veio. */
    private fun decodificar(bruto: String): String =
        runCatching { String(Base64.decode(bruto, Base64.DEFAULT), Charsets.UTF_8).trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: bruto

    companion object {
        private const val SEM_PROGRAMA_MS = 15 * 60 * 1000L
        private const val APOS_FALHA_MS = 5 * 60 * 1000L
        private const val SEM_GUIA_MS = 30 * 60 * 1000L
    }
}
