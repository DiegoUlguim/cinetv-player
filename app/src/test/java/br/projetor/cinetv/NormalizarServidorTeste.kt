package br.projetor.cinetv

import br.projetor.cinetv.dados.ArmazenamentoCredenciais.Companion.normalizarServidor
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizarServidorTeste {

    @Test
    fun aceitaHostSemEsquema() {
        assertEquals("https://exemplo.cc", normalizarServidor("exemplo.cc"))
    }

    @Test
    fun mantemPortaEEsquema() {
        assertEquals("http://exemplo.cc:8080", normalizarServidor(" http://exemplo.cc:8080/ "))
    }

    @Test
    fun descartaCaminhoColado() {
        assertEquals(
            "https://exemplo.cc",
            normalizarServidor("https://exemplo.cc/player_api.php?username=a&password=b"),
        )
    }
}
