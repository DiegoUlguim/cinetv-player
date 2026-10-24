package br.projetor.cinetv.dados

import br.projetor.cinetv.dados.banco.BancoCineTV
import br.projetor.cinetv.dados.banco.Canal
import br.projetor.cinetv.dados.banco.Categoria
import br.projetor.cinetv.dados.banco.Filme
import br.projetor.cinetv.dados.banco.Metadado
import br.projetor.cinetv.dados.banco.Serie
import br.projetor.cinetv.dados.xtream.ClienteXtream
import br.projetor.cinetv.dados.xtream.LeitorJson
import br.projetor.cinetv.dados.xtream.Mapeadores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de uma sincronizacao, para o log e para a tela de ajustes. */
data class ResumoSincronizacao(val canais: Int, val filmes: Int, val series: Int, val ignorados: Int, val milissegundos: Long)

/**
 * Baixa as listas do servidor e troca o catalogo inteiro numa transacao unica.
 * Com WAL, a tela continua lendo a versao anterior ate o commit; se o processo morrer no meio,
 * a transacao e desfeita e nada pela metade aparece.
 */
class Sincronizador(private val banco: BancoCineTV, private val cliente: ClienteXtream) {

    private val dao = banco.catalogo()

    suspend fun precisaSincronizar(): Boolean {
        val ultima = dao.metadado(CHAVE_ULTIMA)?.toLongOrNull() ?: return true
        return System.currentTimeMillis() - ultima > VALIDADE_MS || dao.totalCanais() == 0
    }

    suspend fun sincronizar(): ResumoSincronizacao = withContext(Dispatchers.IO) {
        val inicio = System.currentTimeMillis()
        // Valida o login antes de apagar qualquer coisa e guarda a validade para os Ajustes
        val conta = cliente.conta()
        var canais = 0
        var filmes = 0
        var series = 0
        var ignorados = 0
        banco.runInTransaction {
            val adultasAoVivo = gravarCategorias("live", "get_live_categories")
            dao.apagarCanais()
            // A lista de canais e pequena (~1.800): fica inteira em memoria para agrupar as qualidades
            val todosCanais = ArrayList<Canal>()
            ignorados += percorrer("get_live_streams") { item, ordem ->
                val canal = Mapeadores.canal(item, ordem)
                when {
                    canal == null -> false
                    canal.categoriaId in adultasAoVivo -> true
                    else -> { todosCanais += canal; true }
                }
            }
            val agrupados = Agrupamento.agrupar(todosCanais)
            agrupados.chunked(LOTE).forEach(dao::inserirCanais)
            canais = agrupados.count { it.representante }

            val adultasFilmes = gravarCategorias("vod", "get_vod_categories")
            dao.apagarFilmes()
            val loteFilmes = ArrayList<Filme>(LOTE)
            ignorados += percorrer("get_vod_streams") { item, ordem ->
                val filme = Mapeadores.filme(item, ordem)
                when {
                    filme == null -> false
                    filme.categoriaId in adultasFilmes -> true
                    else -> {
                        loteFilmes += filme
                        filmes++
                        if (loteFilmes.size == LOTE) { dao.inserirFilmes(loteFilmes); loteFilmes.clear() }
                        true
                    }
                }
            }
            dao.inserirFilmes(loteFilmes)

            val adultasSeries = gravarCategorias("series", "get_series_categories")
            dao.apagarSeries()
            val loteSeries = ArrayList<Serie>(LOTE)
            ignorados += percorrer("get_series") { item, ordem ->
                val serie = Mapeadores.serie(item, ordem)
                when {
                    serie == null -> false
                    serie.categoriaId in adultasSeries -> true
                    else -> {
                        loteSeries += serie
                        series++
                        if (loteSeries.size == LOTE) { dao.inserirSeries(loteSeries); loteSeries.clear() }
                        true
                    }
                }
            }
            dao.inserirSeries(loteSeries)

            dao.gravarMetadado(Metadado(CHAVE_ULTIMA, System.currentTimeMillis().toString()))
            dao.gravarMetadado(Metadado(CHAVE_CONTA_STATUS, conta.status.orEmpty()))
            dao.gravarMetadado(Metadado(CHAVE_CONTA_EXPIRA, conta.expiraEm?.toString().orEmpty()))
        }
        val resumo = ResumoSincronizacao(canais, filmes, series, ignorados, System.currentTimeMillis() - inicio)
        Registro.info("Sincronizacao: $resumo")
        resumo
    }

    /** Grava as categorias de um tipo, sem as adultas, e devolve os ids das adultas para filtrar os itens. */
    private fun gravarCategorias(tipo: String, acao: String): Set<String> {
        val categorias = ArrayList<Categoria>()
        val adultas = HashSet<String>()
        cliente.abrir(acao).use { entrada ->
            LeitorJson.percorrerArray(entrada) { item ->
                val categoria = Mapeadores.categoria(tipo, item, categorias.size + adultas.size) ?: return@percorrerArray
                if (Mapeadores.ehAdulta(categoria.nome)) adultas += categoria.id else categorias += categoria
            }
        }
        dao.apagarCategorias(tipo)
        dao.inserirCategorias(categorias)
        return adultas
    }

    /** Percorre uma lista grande; aoItem devolve false quando o item foi ignorado por estar malformado. */
    private fun percorrer(acao: String, aoItem: (kotlinx.serialization.json.JsonObject, Int) -> Boolean): Int {
        var ordem = 0
        var ignorados = 0
        cliente.abrir(acao).use { entrada ->
            ignorados += LeitorJson.percorrerArray(entrada) { item ->
                if (!aoItem(item, ordem++)) ignorados++
            }
        }
        return ignorados
    }

    companion object {
        const val CHAVE_ULTIMA = "ultima_sincronizacao"
        const val CHAVE_CONTA_STATUS = "conta_status"
        const val CHAVE_CONTA_EXPIRA = "conta_expira"
        private const val VALIDADE_MS = 24L * 60 * 60 * 1000
        private const val LOTE = 1000
    }
}
