package br.projetor.cinetv

import br.projetor.cinetv.dados.xtream.LeitorJson
import br.projetor.cinetv.dados.xtream.Mapeadores
import br.projetor.cinetv.dados.xtream.primeiroTexto
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amostras com as variacoes de tipo encontradas no JSON real do servidor (24/10). */
class LeitorToleranteTeste {

    private fun ler(json: String): Pair<List<JsonObject>, Int> {
        val itens = mutableListOf<JsonObject>()
        val ignorados = LeitorJson.percorrerArray(json.byteInputStream()) { itens += it }
        return itens to ignorados
    }

    @Test
    fun canalComEpgNuloOuVazioEIconeVazio() {
        val (itens, _) = ler(
            """[
              {"stream_id":10,"name":"Canal A","category_id":"5","stream_icon":"","epg_channel_id":null},
              {"stream_id":"11","name":"Canal B","category_id":"5","stream_icon":"http://x/l.png","epg_channel_id":""}
            ]""",
        )
        val a = Mapeadores.canal(itens[0], 0)!!
        val b = Mapeadores.canal(itens[1], 1)!!
        assertNull(a.logo)
        assertNull(a.epgId)
        assertEquals(11, b.id)
        assertNull(b.epgId)
    }

    @Test
    fun filmeComNotaInteiraOuDecimalEListasVazias() {
        val (itens, _) = ler(
            """[
              {"stream_id":1,"name":"F1","category_id":"9","rating_5based":4,"backdrop_path":[],"container_extension":"mp4","added":"1700000000"},
              {"stream_id":2,"name":"F2","category_id":"9","rating_5based":3.5,"backdrop_path":["http://x/b.jpg"],"release_date":"","year":"2021"},
              {"stream_id":3,"name":"F3","category_id":"9","rating_5based":0,"container_extension":""}
            ]""",
        )
        val f1 = Mapeadores.filme(itens[0], 0)!!
        val f2 = Mapeadores.filme(itens[1], 1)!!
        val f3 = Mapeadores.filme(itens[2], 2)!!
        assertEquals(4.0, f1.nota!!, 0.001)
        assertEquals(1700000000L, f1.adicionadoEm)
        assertNull(itens[0].primeiroTexto("backdrop_path"))
        assertEquals(3.5, f2.nota!!, 0.001)
        assertEquals("2021", f2.ano)
        assertEquals("http://x/b.jpg", itens[1].primeiroTexto("backdrop_path"))
        assertNull("nota 0 significa sem nota", f3.nota)
        assertEquals("extensao vazia cai no padrao", "mp4", f3.extensao)
    }

    @Test
    fun itemSemIdOuNomeEItemQueNaoEObjetoSaoIgnorados() {
        val (itens, naoObjetos) = ler(
            """[
              {"stream_id":"","name":"Sem id"},
              {"stream_id":5,"name":""},
              "lixo",
              {"stream_id":6,"name":"Ok","category_id":"1"}
            ]""",
        )
        assertEquals(1, naoObjetos)
        assertNull(Mapeadores.canal(itens[0], 0))
        assertNull(Mapeadores.canal(itens[1], 1))
        assertEquals(6, Mapeadores.canal(itens[2], 2)!!.id)
    }

    @Test
    fun categoriasAdultasDoServidor() {
        assertTrue(Mapeadores.ehAdulta("XXX: +18 | Adultos"))
        assertTrue(Mapeadores.ehAdulta("XXX | +18 Adultos - Sexy Hot"))
        assertTrue(Mapeadores.ehAdulta("XXX: +18 Reality"))
        assertFalse(Mapeadores.ehAdulta("Filmes | Ação"))
        assertFalse(Mapeadores.ehAdulta("Canais | Hotelaria e Turismo"))
        assertFalse(Mapeadores.ehAdulta("Séries | 2018"))
    }
}
