package br.projetor.cinetv

import br.projetor.cinetv.dados.xtream.LeitorJson
import br.projetor.cinetv.dados.xtream.LeitorSerie
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Estrutura igual a do get_series_info do servidor de teste (24/10), reduzida. */
class LeitorSerieTeste {

    private val amostra = """
        {
          "seasons": [{"season_number": 1, "name": "Temporada 1"}],
          "info": {"plot": "Sinopse", "genre": "Drama", "backdrop_path": ["http://x/f.jpg"], "cover": "http://x/c.jpg"},
          "episodes": {
            "2": [
              {"id": "900", "episode_num": "1", "title": "S2E1", "container_extension": "mkv", "season": 2, "info": {"duration_secs": 2400}}
            ],
            "1": [
              {"id": "377288", "episode_num": "2", "title": "Episódio 2", "container_extension": "mp4", "season": 1, "info": {"plot": ""}},
              {"id": "377287", "episode_num": "1", "title": "Episódio 1", "container_extension": "mp4", "season": 1, "info": []},
              {"id": "", "episode_num": "3", "title": "Sem id"}
            ],
            "x": [{"id": "1"}]
          }
        }
    """.trimIndent()

    private fun ler() = LeitorSerie.ler(LeitorJson.json.parseToJsonElement(amostra) as JsonObject)

    @Test
    fun temporadasEmOrdemEEpisodiosOrdenados() {
        val serie = ler()
        assertEquals(listOf(1, 2), serie.temporadas)
        assertEquals(listOf(377287, 377288), serie.episodios[1]!!.map { it.id })
        assertEquals("mkv", serie.episodios[2]!![0].extensao)
        assertEquals(2400, serie.episodios[2]!![0].duracaoSegundos)
        assertEquals("http://x/f.jpg", serie.fundo)
    }

    @Test
    fun proximoEpisodioAtravessaTemporada() {
        val serie = ler()
        assertEquals(377288, serie.proximoDepois(377287)!!.id)
        assertEquals(900, serie.proximoDepois(377288)!!.id)
        assertNull(serie.proximoDepois(900))
    }

    @Test
    fun infoDoEpisodioComoListaVaziaNaoQuebra() {
        assertNull(ler().episodio(377287)!!.duracaoSegundos)
    }
}
