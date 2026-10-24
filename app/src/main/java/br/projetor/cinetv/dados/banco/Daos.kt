package br.projetor.cinetv.dados.banco

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogoDao {

    @Query("SELECT * FROM categoria WHERE tipo = :tipo ORDER BY ordem")
    fun categorias(tipo: String): Flow<List<Categoria>>

    /** Categorias que tem pelo menos um item (o servidor manda categorias vazias). */
    @Query("SELECT c.* FROM categoria c WHERE c.tipo = 'vod' AND EXISTS (SELECT 1 FROM filme f WHERE f.categoriaId = c.id) ORDER BY c.ordem")
    fun categoriasDeFilmeComItens(): Flow<List<Categoria>>

    // Listas de canais mostram so o representante de cada grupo de qualidade (FHD/HD/SD)

    @Query("SELECT * FROM canal WHERE categoriaId = :categoriaId AND representante = 1 ORDER BY ordem")
    fun canais(categoriaId: String): PagingSource<Int, Canal>

    /** Canais da mesma categoria para o zapping do player (o maior grupo tem algumas centenas). */
    @Query("SELECT * FROM canal WHERE categoriaId = (SELECT categoriaId FROM canal WHERE id = :canalId) AND representante = 1 ORDER BY ordem")
    suspend fun canaisDoGrupoDe(canalId: Int): List<Canal>

    /** Variantes de qualidade de um canal, na ordem do servidor. */
    @Query("SELECT * FROM canal WHERE grupo = :grupo ORDER BY ordem")
    suspend fun variantes(grupo: String): List<Canal>

    @Query("SELECT * FROM filme WHERE categoriaId = :categoriaId ORDER BY ordem")
    fun filmes(categoriaId: String): PagingSource<Int, Filme>

    @Query("SELECT c.* FROM categoria c WHERE c.tipo = 'series' AND EXISTS (SELECT 1 FROM serie s WHERE s.categoriaId = c.id) ORDER BY c.ordem")
    fun categoriasDeSerieComItens(): Flow<List<Categoria>>

    @Query("SELECT * FROM serie WHERE categoriaId = :categoriaId ORDER BY ordem")
    fun series(categoriaId: String): PagingSource<Int, Serie>

    @Query("SELECT * FROM serie WHERE id = :id")
    suspend fun serie(id: Int): Serie?

    @Query("DELETE FROM serie")
    fun apagarSeries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserirSeries(series: List<Serie>)

    /** Um filme qualquer em mp4, para o teste de velocidade. */
    @Query("SELECT * FROM filme WHERE extensao = 'mp4' ORDER BY ordem LIMIT 1")
    suspend fun algumFilme(): Filme?

    @Query("SELECT * FROM filme WHERE id = :id")
    suspend fun filme(id: Int): Filme?

    @Query("SELECT * FROM detalhe_filme WHERE id = :id")
    suspend fun detalheFilme(id: Int): DetalheFilme?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun gravarDetalheFilme(detalhe: DetalheFilme)

    // Busca: :consulta ja vem no formato do MATCH (ver Busca.consultaFts)

    /** Qualquer variante que bata leva ao representante do grupo, uma vez so. */
    @Query(
        "SELECT DISTINCT rep.* FROM canal_fts JOIN canal v ON v.rowid = canal_fts.rowid " +
            "JOIN canal rep ON rep.grupo = v.grupo AND rep.representante = 1 " +
            "WHERE canal_fts MATCH :consulta ORDER BY rep.nomeBase LIMIT 40",
    )
    suspend fun buscarCanais(consulta: String): List<Canal>

    @Query("SELECT filme.* FROM filme JOIN filme_fts ON filme.rowid = filme_fts.rowid WHERE filme_fts MATCH :consulta ORDER BY filme.nome LIMIT 60")
    suspend fun buscarFilmes(consulta: String): List<Filme>

    @Query("SELECT serie.* FROM serie JOIN serie_fts ON serie.rowid = serie_fts.rowid WHERE serie_fts MATCH :consulta ORDER BY serie.nome LIMIT 60")
    suspend fun buscarSeries(consulta: String): List<Serie>

    /**
     * Canais favoritos na ordem do catalogo (ids vem do banco do usuario). Um favorito gravado numa
     * variante (ex.: o SD) aparece pelo representante do grupo.
     */
    @Query("SELECT * FROM canal WHERE representante = 1 AND grupo IN (SELECT grupo FROM canal WHERE id IN (:ids)) ORDER BY categoriaId, ordem")
    suspend fun canaisPorIds(ids: List<Int>): List<Canal>

    @Query("SELECT * FROM canal WHERE representante = 1 AND grupo IN (SELECT grupo FROM canal WHERE id IN (:ids)) ORDER BY categoriaId, ordem")
    fun canaisPorIdsPaginado(ids: List<Int>): PagingSource<Int, Canal>

    @Query("SELECT * FROM filme WHERE id IN (:ids)")
    suspend fun filmesPorIds(ids: List<Int>): List<Filme>

    @Query("SELECT * FROM serie WHERE id IN (:ids)")
    suspend fun seriesPorIds(ids: List<Int>): List<Serie>

    @Query("SELECT COUNT(*) FROM canal")
    suspend fun totalCanais(): Int

    @Query("SELECT COUNT(*) FROM filme")
    suspend fun totalFilmes(): Int

    @Query("SELECT valor FROM metadado WHERE chave = :chave")
    suspend fun metadado(chave: String): String?

    @Query("SELECT valor FROM metadado WHERE chave = :chave")
    fun metadadoAoVivo(chave: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun gravarMetadado(metadado: Metadado)

    // Escritas da sincronizacao: sempre dentro da transacao unica do Sincronizador

    @Query("DELETE FROM categoria WHERE tipo = :tipo")
    fun apagarCategorias(tipo: String)

    @Query("DELETE FROM canal")
    fun apagarCanais()

    @Query("DELETE FROM filme")
    fun apagarFilmes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserirCategorias(categorias: List<Categoria>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserirCanais(canais: List<Canal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun inserirFilmes(filmes: List<Filme>)
}
