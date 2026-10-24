package br.projetor.cinetv

import br.projetor.cinetv.dados.Busca
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuscaTeste {

    @Test
    fun cadaPalavraViraPrefixo() {
        assertEquals("homem* aranha*", Busca.consultaFts("Homem Aranha"))
    }

    @Test
    fun pontuacaoEOperadoresDoFtsSaoDescartados() {
        assertEquals("ação* tv*", Busca.consultaFts("\"Ação\" - OR TV!"))
        assertEquals("sp2*", Busca.consultaFts("SP2"))
    }

    @Test
    fun textoSemPalavraNaoBusca() {
        assertNull(Busca.consultaFts("  -- \"\" "))
    }
}
