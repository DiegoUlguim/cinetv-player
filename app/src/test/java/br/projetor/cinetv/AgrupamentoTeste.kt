package br.projetor.cinetv

import br.projetor.cinetv.dados.Agrupamento
import br.projetor.cinetv.dados.banco.Canal
import org.junit.Assert.assertEquals
import org.junit.Test

class AgrupamentoTeste {

    private fun canal(id: Int, nome: String, categoria: String = "1", epg: String? = null) =
        Canal(id = id, nome = nome, categoriaId = categoria, logo = null, epgId = epg, ordem = id)

    @Test
    fun trioViraUmRepresentanteComOGuiaDoGrupo() {
        val resultado = Agrupamento.agrupar(
            listOf(
                canal(1, "01 - Globo SP Capital FHD"),
                canal(2, "01 - Globo SP Capital HD", epg = "globo.sp"),
                canal(3, "01 - Globo SP Capital SD"),
                canal(4, "Pluto TV Anime Ação"),
            ),
        )
        val representantes = resultado.filter { it.representante }
        assertEquals(listOf(1, 4), representantes.map { it.id })
        assertEquals("01 - Globo SP Capital", representantes[0].nomeBase)
        assertEquals("globo.sp", representantes[0].epgId)
        assertEquals(setOf("FHD", "HD", "SD"), resultado.filter { it.grupo == representantes[0].grupo }.mapNotNull { it.qualidade }.toSet())
    }

    @Test
    fun mesmoNomeEmCategoriasDiferentesNaoSeJunta() {
        val resultado = Agrupamento.agrupar(listOf(canal(1, "ESPN HD", "esportes"), canal(2, "ESPN SD", "abertos")))
        assertEquals(2, resultado.count { it.representante })
    }
}
