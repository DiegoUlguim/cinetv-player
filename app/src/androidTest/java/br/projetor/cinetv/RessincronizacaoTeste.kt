package br.projetor.cinetv

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.projetor.cinetv.dados.Credenciais
import br.projetor.cinetv.dados.ResumoSincronizacao
import br.projetor.cinetv.dados.Sincronizador
import br.projetor.cinetv.dados.banco.BancoCineTV
import br.projetor.cinetv.dados.usuario.BancoUsuario
import br.projetor.cinetv.dados.usuario.Favorito
import br.projetor.cinetv.dados.usuario.Progresso
import br.projetor.cinetv.dados.xtream.ClienteXtream
import br.projetor.cinetv.dados.xtream.ErroServidor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RessincronizacaoTeste {

    private lateinit var contexto: Context
    private lateinit var servidor: MockWebServer
    private lateinit var catalogo: BancoCineTV
    private lateinit var usuario: BancoUsuario
    private lateinit var sincronizador: Sincronizador

    /** Liga a falha da segunda sincronizacao: get_series devolve 500 e os filmes vem vazios. */
    @Volatile
    private var falharSeries = false

    private val respostas = mapOf(
        null to """{"user_info":{"auth":1,"status":"Active","exp_date":"1798761600"}}""",
        "get_live_categories" to """[
            {"category_id":"1","category_name":"Abertos"},
            {"category_id":"9","category_name":"XXX: +18 | Adultos"}
        ]""",
        "get_live_streams" to """[
            {"stream_id":101,"name":"Canal X FHD","category_id":"1"},
            {"stream_id":102,"name":"Canal X HD","category_id":"1","epg_channel_id":"canal.x"},
            {"stream_id":103,"name":"Canal X SD","category_id":"1"},
            {"stream_id":104,"name":"Canal Y","category_id":"1"},
            {"stream_id":105,"name":"Canal Adulto","category_id":"9"}
        ]""",
        "get_vod_categories" to """[
            {"category_id":"2","category_name":"Ação"},
            {"category_id":"8","category_name":"Adultos +18"}
        ]""",
        "get_vod_streams" to """[
            {"stream_id":201,"name":"Filme A","category_id":"2","container_extension":"mp4"},
            {"stream_id":202,"name":"Filme B","category_id":"2","container_extension":"mkv"},
            {"stream_id":203,"name":"Filme Adulto","category_id":"8"}
        ]""",
        "get_series_categories" to """[
            {"category_id":"3","category_name":"Drama"},
            {"category_id":"7","category_name":"XXX Adultos"}
        ]""",
        "get_series" to """[
            {"series_id":301,"name":"Serie A","category_id":"3"},
            {"series_id":302,"name":"Serie B","category_id":"3"},
            {"series_id":303,"name":"Serie Adulta","category_id":"7"}
        ]""",
    )

    private val rotas = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val acao = request.requestUrl?.queryParameter("action")
            if (falharSeries && acao == "get_series") return json("""{"erro":"interno"}""").setResponseCode(500)
            if (falharSeries && acao == "get_vod_streams") return json("[]")
            val corpo = respostas[acao] ?: return json("{}").setResponseCode(404)
            return json(corpo)
        }
    }

    private fun json(corpo: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(corpo)

    @Before
    fun iniciar() {
        contexto = InstrumentationRegistry.getInstrumentation().targetContext
        servidor = MockWebServer()
        servidor.dispatcher = rotas
        servidor.start()
        catalogo = Room.inMemoryDatabaseBuilder(contexto, BancoCineTV::class.java).build()
        usuario = Room.inMemoryDatabaseBuilder(contexto, BancoUsuario::class.java).build()
        val credenciais = Credenciais(servidor.url("/").toString().trimEnd('/'), "joao", "segredo")
        sincronizador = Sincronizador(catalogo, ClienteXtream(OkHttpClient(), credenciais))
    }

    @After
    fun encerrar() {
        catalogo.close()
        usuario.close()
        servidor.shutdown()
    }

    private fun contar(sql: String): Int = catalogo.query(sql, null).use { it.moveToFirst(); it.getInt(0) }

    private data class Contagens(val linhasCanal: Int, val representantes: Int, val adultos: Int, val filmes: Int, val series: Int, val categorias: Int)

    private fun contagens() = Contagens(
        linhasCanal = contar("SELECT COUNT(*) FROM canal"),
        representantes = contar("SELECT COUNT(*) FROM canal WHERE representante = 1"),
        adultos = contar(
            "SELECT (SELECT COUNT(*) FROM canal WHERE id = 105) + (SELECT COUNT(*) FROM filme WHERE id = 203) + " +
                "(SELECT COUNT(*) FROM serie WHERE id = 303) + (SELECT COUNT(*) FROM categoria WHERE id IN ('7','8','9'))",
        ),
        filmes = contar("SELECT COUNT(*) FROM filme"),
        series = contar("SELECT COUNT(*) FROM serie"),
        categorias = contar("SELECT COUNT(*) FROM categoria"),
    )

    private val esperado = Contagens(linhasCanal = 4, representantes = 2, adultos = 0, filmes = 2, series = 2, categorias = 3)

    @Test
    fun primeiraSincronizacaoAgrupaOTrioEEscondeAdultos() = runBlocking<Unit> {
        val resumo = sincronizador.sincronizar()
        assertEquals(ResumoSincronizacao(2, 2, 2, 0, resumo.milissegundos), resumo)
        assertEquals(esperado, contagens())
        assertEquals(
            "o representante do trio e a primeira variante e herda o guia",
            "canal.x",
            catalogo.query("SELECT epgId FROM canal WHERE id = 101 AND representante = 1", null).use { it.moveToFirst(); it.getString(0) },
        )
    }

    @Test
    fun ressincronizarDuasVezesMantemContagensFavoritosEProgresso() = runBlocking<Unit> {
        usuario.favoritos().gravar(Favorito("canal:101", "canal", 101, "Canal X", null, 1L))
        usuario.favoritos().gravar(Favorito("filme:201", "filme", 201, "Filme A", null, 2L))
        usuario.progresso().gravar(Progresso("filme:201", "filme", 201, "Filme A", null, "mp4", 34_000L, 6_000_000L, 3L))

        sincronizador.sincronizar()
        val primeira = contagens()
        sincronizador.sincronizar()
        val segunda = contagens()

        assertEquals(esperado, primeira)
        assertEquals(primeira, segunda)
        assertEquals(true, usuario.favoritos().existeAgora("canal:101"))
        assertEquals(true, usuario.favoritos().existeAgora("filme:201"))
        assertEquals(34_000L, usuario.progresso().obter("filme:201")?.posicaoMs)
    }

    @Test
    fun falhaNoMeioDaSegundaSincronizacaoMantemOCatalogoDaPrimeira() = runBlocking<Unit> {
        sincronizador.sincronizar()
        val ultimaAntes = catalogo.catalogo().metadado(Sincronizador.CHAVE_ULTIMA)

        falharSeries = true
        try {
            sincronizador.sincronizar()
            fail("esperava ErroServidor")
        } catch (e: ErroServidor) {
            assertEquals(500, e.codigoHttp)
        }

        assertEquals("filmes vazios da segunda tentativa foram desfeitos", esperado, contagens())
        assertEquals(ultimaAntes, catalogo.catalogo().metadado(Sincronizador.CHAVE_ULTIMA))
    }

    @Test
    fun migracao1Para2CriaFavoritosEMantemProgresso() = runBlocking<Unit> {
        val nome = "usuario-migracao-teste.db"
        contexto.deleteDatabase(nome)
        val arquivo = contexto.getDatabasePath(nome).apply { parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(arquivo, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `progresso` (`chave` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
                    "`idConteudo` INTEGER NOT NULL, `titulo` TEXT NOT NULL, `capa` TEXT, `extensao` TEXT NOT NULL, " +
                    "`posicaoMs` INTEGER NOT NULL, `duracaoMs` INTEGER NOT NULL, `atualizadoEm` INTEGER NOT NULL, " +
                    "`serieId` INTEGER, PRIMARY KEY(`chave`))",
            )
            // Na subida de versao o Room nao confere o hash antigo, so grava o novo depois da migracao
            v1.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            v1.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'hash-da-v1')")
            v1.execSQL(
                "INSERT INTO progresso VALUES ('filme:201', 'filme', 201, 'Filme A', NULL, 'mp4', 34000, 6000000, 3, NULL)",
            )
            v1.version = 1
        }

        val migrado = Room.databaseBuilder(contexto, BancoUsuario::class.java, nome)
            .addMigrations(BancoUsuario.MIGRACAO_1_2)
            .build()
        try {
            val progresso = migrado.progresso().obter("filme:201")
            assertNotNull("progresso da v1 sobreviveu", progresso)
            assertEquals(34_000L, progresso!!.posicaoMs)
            migrado.favoritos().gravar(Favorito("serie:301", "serie", 301, "Serie A", null, 1L))
            assertEquals(true, migrado.favoritos().existeAgora("serie:301"))
            assertEquals(2, migrado.openHelper.readableDatabase.version)
        } finally {
            migrado.close()
            contexto.deleteDatabase(nome)
        }
    }
}
