package br.projetor.cinetv.dados.banco

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index

/** tipo: "live", "vod" ou "series" (o mesmo id pode existir nos tres). */
@Entity(tableName = "categoria", primaryKeys = ["tipo", "id"])
data class Categoria(
    val tipo: String,
    val id: String,
    val nome: String,
    val ordem: Int,
)

/**
 * Canal do servidor. O mesmo canal em FHD/HD/SD forma um grupo (categoria + nome sem a qualidade);
 * as listas mostram so o representante (a primeira variante) e o player escolhe a variante na hora.
 */
@Entity(
    tableName = "canal",
    primaryKeys = ["id"],
    indices = [Index("categoriaId", "representante", "ordem"), Index("grupo")],
)
data class Canal(
    val id: Int,
    val nome: String,
    val categoriaId: String,
    val logo: String?,
    val epgId: String?,
    val ordem: Int,
    val nomeBase: String = nome,
    /** Nome do enum Qualidade, ou null quando o nome nao traz qualidade. */
    val qualidade: String? = null,
    val grupo: String = "$categoriaId|$nome",
    val representante: Boolean = true,
)

@Entity(tableName = "filme", primaryKeys = ["id"], indices = [Index("categoriaId", "ordem")])
data class Filme(
    val id: Int,
    val nome: String,
    val categoriaId: String,
    val capa: String?,
    val nota: Double?,
    val ano: String?,
    val extensao: String,
    val adicionadoEm: Long?,
    val ordem: Int,
)

@Entity(tableName = "serie", primaryKeys = ["id"], indices = [Index("categoriaId", "ordem")])
data class Serie(
    val id: Int,
    val nome: String,
    val categoriaId: String,
    val capa: String?,
    val nota: Double?,
    val ano: String?,
    val ordem: Int,
)

/** Detalhe do filme (get_vod_info), buscado na primeira abertura e guardado como cache. */
@Entity(tableName = "detalhe_filme", primaryKeys = ["id"])
data class DetalheFilme(
    val id: Int,
    val sinopse: String?,
    val duracaoSegundos: Int?,
    val genero: String?,
    val elenco: String?,
    val diretor: String?,
    val fundo: String?,
    val capaGrande: String?,
    val lancamento: String?,
)

// Indices de busca: FTS4 com conteudo externo (o Room mantem por gatilhos) e sem acento,
// para "acao" achar "Ação". O rowid de cada tabela e o id do servidor.

@Fts4(contentEntity = Canal::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=2"])
@Entity(tableName = "canal_fts")
data class CanalFts(val nome: String)

@Fts4(contentEntity = Filme::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=2"])
@Entity(tableName = "filme_fts")
data class FilmeFts(val nome: String)

@Fts4(contentEntity = Serie::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=2"])
@Entity(tableName = "serie_fts")
data class SerieFts(val nome: String)

/** Pares chave/valor do app (data da ultima sincronizacao, contagens). */
@Entity(tableName = "metadado", primaryKeys = ["chave"])
data class Metadado(val chave: String, val valor: String)
