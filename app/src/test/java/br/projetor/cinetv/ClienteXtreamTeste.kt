package br.projetor.cinetv

import br.projetor.cinetv.dados.Credenciais
import br.projetor.cinetv.dados.xtream.ClienteXtream
import br.projetor.cinetv.dados.xtream.ErroServidor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ClienteXtreamTeste {

    private lateinit var servidor: MockWebServer
    private lateinit var cliente: ClienteXtream

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        val credenciais = Credenciais(servidor.url("/").toString().trimEnd('/'), "joao", "a/b c")
        cliente = ClienteXtream(OkHttpClient(), credenciais)
    }

    @After
    fun encerrar() {
        servidor.shutdown()
    }

    private fun responder(codigo: Int, corpo: String, tipo: String = "application/json") {
        servidor.enqueue(MockResponse().setResponseCode(codigo).setHeader("Content-Type", tipo).setBody(corpo))
    }

    private fun erroDe(bloco: () -> Unit): ErroServidor {
        try {
            bloco()
        } catch (e: ErroServidor) {
            return e
        }
        fail("esperava ErroServidor")
        throw AssertionError()
    }

    @Test
    fun urlsDeFluxoLevamTipoUsuarioSenhaEArquivo() {
        val base = servidor.url("/").toString().trimEnd('/')
        assertEquals("$base/live/joao/a%2Fb%20c/123.ts", cliente.urlAoVivo(123))
        assertEquals("$base/live/joao/a%2Fb%20c/123.m3u8", cliente.urlAoVivo(123, "m3u8"))
        assertEquals("$base/movie/joao/a%2Fb%20c/456.mkv", cliente.urlFilme(456, "mkv"))
        assertEquals("$base/series/joao/a%2Fb%20c/789.mp4", cliente.urlEpisodio(789, "mp4"))
    }

    @Test
    fun abrirChamaPlayerApiComLoginAcaoEParametros() {
        responder(200, """{"info":{}}""")
        val corpo = cliente.abrir("get_vod_info", mapOf("vod_id" to "42")).use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals("""{"info":{}}""", corpo)

        val url = servidor.takeRequest().requestUrl!!
        assertEquals("/player_api.php", url.encodedPath)
        assertEquals("joao", url.queryParameter("username"))
        assertEquals("a/b c", url.queryParameter("password"))
        assertEquals("get_vod_info", url.queryParameter("action"))
        assertEquals("42", url.queryParameter("vod_id"))
    }

    @Test
    fun abrirSemAcaoNaoMandaAction() {
        responder(200, "{}")
        cliente.abrir().close()
        val url = servidor.takeRequest().requestUrl!!
        assertNull(url.queryParameter("action"))
        assertEquals("joao", url.queryParameter("username"))
    }

    @Test
    fun htmlComCodigo403ViraBloqueio() {
        responder(403, "<html>Just a moment...</html>", "text/html; charset=UTF-8")
        val erro = erroDe { cliente.abrir("get_live_streams") }
        assertTrue(erro.mensagemUsuario, erro.mensagemUsuario.contains("bloqueou"))
    }

    @Test
    fun htmlComCodigo200TambemViraBloqueio() {
        responder(200, "<html>desafio</html>", "text/html")
        val erro = erroDe { cliente.abrir("get_live_streams") }
        assertTrue(erro.mensagemUsuario, erro.mensagemUsuario.contains("bloqueou"))
    }

    @Test
    fun codigo401ViraLoginRecusado() {
        responder(401, "{}")
        val erro = erroDe { cliente.abrir("get_live_streams") }
        assertTrue(erro.mensagemUsuario, erro.mensagemUsuario.contains("recusados"))
    }

    @Test
    fun codigo404JsonLevaOCodigoHttp() {
        responder(404, """{"erro":"nao achou"}""")
        val erro = erroDe { cliente.abrir("get_short_epg") }
        assertEquals(404, erro.codigoHttp)
    }

    @Test
    fun contaLeStatusEValidadeEmTexto() {
        responder(200, """{"user_info":{"auth":1,"status":"Active","exp_date":"1798761600","max_connections":"1"}}""")
        val conta = cliente.conta()
        assertEquals("Active", conta.status)
        assertEquals(1798761600L, conta.expiraEm)
        assertEquals(1, conta.conexoes)
    }

    @Test
    fun contaComAuthZeroViraLoginRecusado() {
        responder(200, """{"user_info":{"auth":0}}""")
        val erro = erroDe { cliente.conta() }
        assertTrue(erro.mensagemUsuario, erro.mensagemUsuario.contains("recusados"))
    }
}
