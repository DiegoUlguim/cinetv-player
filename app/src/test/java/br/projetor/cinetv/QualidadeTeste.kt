package br.projetor.cinetv

import br.projetor.cinetv.dados.PreferenciaQualidade
import br.projetor.cinetv.dados.Qualidade
import br.projetor.cinetv.dados.Qualidades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualidadeTeste {

    @Test
    fun separaSufixoDeQualidadeDoNome() {
        assertEquals("01 - Globo SP Capital" to Qualidade.FHD, Qualidades.separar("01 - Globo SP Capital FHD"))
        assertEquals("+SBT: Kids" to Qualidade.SD, Qualidades.separar("+SBT: Kids SD"))
        assertEquals("ESPN" to Qualidade.UHD, Qualidades.separar("ESPN 4K"))
        assertEquals("Canal" to Qualidade.HD, Qualidades.separar("Canal | HD"))
    }

    @Test
    fun nomeSemSufixoOuSoComSufixoFicaInteiro() {
        assertEquals("Pluto TV Anime Ação" to null, Qualidades.separar("Pluto TV Anime Ação"))
        assertEquals("HD" to null, Qualidades.separar("HD"))
        assertEquals("SHD Esportes" to null, Qualidades.separar("SHD Esportes"))
    }

    @Test
    fun regraAutomaticaPelaBanda() {
        assertEquals(Qualidade.FHD, Qualidades.alvo(PreferenciaQualidade.AUTOMATICA, 25_000_000))
        assertEquals(Qualidade.HD, Qualidades.alvo(PreferenciaQualidade.AUTOMATICA, 7_000_000))
        assertEquals(Qualidade.SD, Qualidades.alvo(PreferenciaQualidade.AUTOMATICA, 3_000_000))
        assertEquals(Qualidade.HD, Qualidades.alvo(PreferenciaQualidade.AUTOMATICA, null))
        assertEquals(Qualidade.SD, Qualidades.alvo(PreferenciaQualidade.SD, 50_000_000))
    }

    @Test
    fun escolheAMaiorQueCabeNoAlvoOuAMenorDisponivel() {
        val trio = listOf(Qualidade.FHD, Qualidade.HD, Qualidade.SD)
        assertEquals(0, Qualidades.escolher(trio, Qualidade.FHD))
        assertEquals(1, Qualidades.escolher(trio, Qualidade.HD))
        assertEquals(2, Qualidades.escolher(trio, Qualidade.SD))
        // So FHD e UHD disponiveis e alvo SD: abre a menor
        assertEquals(0, Qualidades.escolher(listOf(Qualidade.FHD, Qualidade.UHD), Qualidade.SD))
        assertEquals(0, Qualidades.escolher(listOf(null), Qualidade.SD))
    }

    @Test
    fun desceUmaQualidadeQuandoTrava() {
        val trio = listOf(Qualidade.FHD, Qualidade.HD, Qualidade.SD)
        assertEquals(1, Qualidades.abaixo(trio, 0))
        assertEquals(2, Qualidades.abaixo(trio, 1))
        assertNull(Qualidades.abaixo(trio, 2))
    }
}
