package br.projetor.cinetv.dados.xtream

import br.projetor.cinetv.dados.Credenciais
import br.projetor.cinetv.dados.Registro
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

/** Erro do servidor ja traduzido para a mensagem que o usuario vai ler. */
class ErroServidor(val mensagemUsuario: String, causa: Throwable? = null, val codigoHttp: Int? = null) :
    IOException(mensagemUsuario, causa)

/** Acesso a API player_api.php e montagem das URLs de reproducao do Xtream. */
class ClienteXtream(private val http: OkHttpClient, private val credenciais: Credenciais) {

    private val base: HttpUrl = credenciais.servidor.toHttpUrl()

    /** Abre a resposta de uma acao da API. Quem chama fecha o InputStream. */
    fun abrir(acao: String? = null, parametros: Map<String, String> = emptyMap()): InputStream {
        val url = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", credenciais.usuario)
            .addQueryParameter("password", credenciais.senha)
            .apply { if (acao != null) addQueryParameter("action", acao) }
            .apply { parametros.forEach { (chave, valor) -> addQueryParameter(chave, valor) } }
            .build()
        val resposta = try {
            http.newCall(Request.Builder().url(url).build()).execute()
        } catch (e: IOException) {
            throw ErroServidor("Sem conexão com o servidor", e)
        }
        validar(resposta, acao)
        return resposta.body!!.byteStream()
    }

    private fun validar(resposta: Response, acao: String?) {
        val tipo = resposta.header("Content-Type").orEmpty()
        if (!resposta.isSuccessful || !tipo.contains("json")) {
            Registro.aviso("API ${acao ?: "login"} respondeu ${resposta.code} ($tipo)")
            resposta.close()
            // HTML no lugar de JSON e desafio ou bloqueio do Cloudflare, nunca lista vazia
            throw when {
                resposta.code == 401 || resposta.code == 403 && !tipo.contains("html") -> ErroServidor("Usuário ou senha recusados pelo servidor")
                tipo.contains("html") || resposta.code == 403 -> ErroServidor("O servidor bloqueou o acesso. Tente de novo em alguns minutos")
                else -> ErroServidor("O servidor respondeu com erro ${resposta.code}", codigoHttp = resposta.code)
            }
        }
    }

    /** Situacao da conta (login sem action): status e validade em epoch (s). */
    data class Conta(val status: String?, val expiraEm: Long?, val conexoes: Int?)

    fun conta(): Conta {
        val texto = abrir().use { it.readBytes().toString(Charsets.UTF_8) }
        val raiz = LeitorJson.json.parseToJsonElement(texto) as? kotlinx.serialization.json.JsonObject
        val usuario = raiz?.get("user_info") as? kotlinx.serialization.json.JsonObject
            ?: throw ErroServidor("Usuário ou senha recusados pelo servidor")
        if (usuario.inteiro("auth") == 0) throw ErroServidor("Usuário ou senha recusados pelo servidor")
        return Conta(usuario.texto("status"), usuario.longo("exp_date"), usuario.inteiro("max_connections"))
    }

    /** get_vod_info: o objeto "info" do filme, ou null se o servidor nao tiver detalhe. */
    fun infoFilme(id: Int): kotlinx.serialization.json.JsonObject? {
        val texto = abrir("get_vod_info", mapOf("vod_id" to id.toString())).use { it.readBytes().toString(Charsets.UTF_8) }
        val raiz = LeitorJson.json.parseToJsonElement(texto) as? kotlinx.serialization.json.JsonObject
        return raiz?.get("info") as? kotlinx.serialization.json.JsonObject
    }

    /** get_series_info: temporadas e episodios da serie. */
    fun infoSerie(id: Int): DetalheSerie {
        val texto = abrir("get_series_info", mapOf("series_id" to id.toString())).use { it.readBytes().toString(Charsets.UTF_8) }
        val raiz = LeitorJson.json.parseToJsonElement(texto) as? kotlinx.serialization.json.JsonObject
            ?: throw ErroServidor("O servidor não trouxe os episódios desta série")
        return LeitorSerie.ler(raiz)
    }

    fun urlAoVivo(id: Int, extensao: String = "ts"): String = urlFluxo("live", "$id.$extensao")

    fun urlFilme(id: Int, extensao: String): String = urlFluxo("movie", "$id.$extensao")

    fun urlEpisodio(id: Int, extensao: String): String = urlFluxo("series", "$id.$extensao")

    private fun urlFluxo(tipo: String, arquivo: String): String = base.newBuilder()
        .addPathSegment(tipo)
        .addPathSegment(credenciais.usuario)
        .addPathSegment(credenciais.senha)
        .addPathSegment(arquivo)
        .build()
        .toString()
}
