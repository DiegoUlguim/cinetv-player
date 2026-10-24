package br.projetor.cinetv.dados.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class Episodio(
    val id: Int,
    val temporada: Int,
    val numero: Int,
    val titulo: String,
    val extensao: String,
    val duracaoSegundos: Int?,
    val sinopse: String?,
)

data class DetalheSerie(
    val sinopse: String?,
    val genero: String?,
    val elenco: String?,
    val fundo: String?,
    /** Temporadas em ordem, so as que tem episodio. */
    val temporadas: List<Int>,
    val episodios: Map<Int, List<Episodio>>,
) {
    /** Proximo episodio na ordem da serie (mesma temporada ou primeiro da seguinte). */
    fun proximoDepois(episodioId: Int): Episodio? {
        val todos = temporadas.flatMap { episodios[it].orEmpty() }
        val posicao = todos.indexOfFirst { it.id == episodioId }
        return if (posicao >= 0) todos.getOrNull(posicao + 1) else null
    }

    fun episodio(id: Int): Episodio? = episodios.values.firstNotNullOfOrNull { lista -> lista.firstOrNull { it.id == id } }
}

/** Leitura do get_series_info: "info" com os dados da serie e "episodes" agrupado por temporada ("1": [...]). */
object LeitorSerie {

    fun ler(raiz: JsonObject): DetalheSerie {
        val info = raiz["info"] as? JsonObject
        val agrupados = raiz["episodes"] as? JsonObject
        val episodios = mutableMapOf<Int, List<Episodio>>()
        agrupados?.forEach { (chaveTemporada, lista) ->
            val numeroTemporada = chaveTemporada.toIntOrNull() ?: return@forEach
            val daTemporada = (lista as? JsonArray).orEmpty().mapNotNull { elemento ->
                val item = elemento as? JsonObject ?: return@mapNotNull null
                val detalhe = item["info"] as? JsonObject
                Episodio(
                    id = item.inteiro("id") ?: return@mapNotNull null,
                    temporada = item.inteiro("season") ?: numeroTemporada,
                    numero = item.inteiro("episode_num") ?: 0,
                    titulo = item.texto("title") ?: "Episódio ${item.inteiro("episode_num") ?: ""}".trim(),
                    extensao = item.texto("container_extension") ?: "mp4",
                    duracaoSegundos = detalhe?.inteiro("duration_secs"),
                    sinopse = detalhe?.texto("plot"),
                )
            }.sortedBy { it.numero }
            if (daTemporada.isNotEmpty()) episodios[numeroTemporada] = daTemporada
        }
        return DetalheSerie(
            sinopse = info?.texto("plot"),
            genero = info?.texto("genre"),
            elenco = info?.texto("cast"),
            fundo = info?.primeiroTexto("backdrop_path") ?: info?.texto("cover"),
            temporadas = episodios.keys.sorted(),
            episodios = episodios,
        )
    }
}
