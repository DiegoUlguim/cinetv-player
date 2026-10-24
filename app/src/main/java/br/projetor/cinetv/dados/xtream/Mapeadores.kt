package br.projetor.cinetv.dados.xtream

import br.projetor.cinetv.dados.banco.Canal
import br.projetor.cinetv.dados.banco.Categoria
import br.projetor.cinetv.dados.banco.DetalheFilme
import br.projetor.cinetv.dados.banco.Filme
import br.projetor.cinetv.dados.banco.Serie
import kotlinx.serialization.json.JsonObject

/** Conversao dos itens do Xtream para as entidades. Item sem id ou nome devolve null e e contado como ignorado. */
object Mapeadores {

    /** Categorias adultas ficam fora do app, inclusive da busca (decisao do dono, 24/10). */
    private val ADULTA = Regex("""xxx|\+ ?18|18 ?\+|adult""", RegexOption.IGNORE_CASE)

    fun ehAdulta(nomeCategoria: String): Boolean = ADULTA.containsMatchIn(nomeCategoria)

    fun categoria(tipo: String, item: JsonObject, ordem: Int): Categoria? {
        val id = item.texto("category_id") ?: return null
        val nome = item.texto("category_name") ?: return null
        return Categoria(tipo = tipo, id = id, nome = nome, ordem = ordem)
    }

    fun canal(item: JsonObject, ordem: Int): Canal? {
        val id = item.inteiro("stream_id") ?: return null
        val nome = item.texto("name") ?: return null
        return Canal(
            id = id,
            nome = nome,
            categoriaId = item.texto("category_id") ?: "",
            logo = item.texto("stream_icon"),
            epgId = item.texto("epg_channel_id"),
            ordem = ordem,
        )
    }

    fun serie(item: JsonObject, ordem: Int): Serie? {
        val id = item.inteiro("series_id") ?: return null
        val nome = item.texto("name") ?: item.texto("title") ?: return null
        return Serie(
            id = id,
            nome = nome,
            categoriaId = item.texto("category_id") ?: "",
            capa = item.texto("cover") ?: item.texto("logo"),
            nota = item.decimal("rating_5based")?.takeIf { it > 0 },
            ano = item.texto("year") ?: item.texto("release_date")?.take(4),
            ordem = ordem,
        )
    }

    fun detalheFilme(id: Int, info: JsonObject): DetalheFilme = DetalheFilme(
        id = id,
        sinopse = info.texto("plot") ?: info.texto("description"),
        duracaoSegundos = info.inteiro("duration_secs"),
        genero = info.texto("genre"),
        elenco = info.texto("cast") ?: info.texto("actors"),
        diretor = info.texto("director"),
        fundo = info.primeiroTexto("backdrop_path"),
        capaGrande = info.texto("cover_big") ?: info.texto("movie_image"),
        lancamento = info.texto("releasedate") ?: info.texto("release_date"),
    )

    fun filme(item: JsonObject, ordem: Int): Filme? {
        val id = item.inteiro("stream_id") ?: return null
        val nome = item.texto("name") ?: item.texto("title") ?: return null
        return Filme(
            id = id,
            nome = nome,
            categoriaId = item.texto("category_id") ?: "",
            capa = item.texto("stream_icon") ?: item.texto("logo"),
            nota = item.decimal("rating_5based")?.takeIf { it > 0 },
            ano = item.texto("year") ?: item.texto("release_date")?.take(4),
            extensao = item.texto("container_extension") ?: "mp4",
            adicionadoEm = item.longo("added"),
            ordem = ordem,
        )
    }
}
