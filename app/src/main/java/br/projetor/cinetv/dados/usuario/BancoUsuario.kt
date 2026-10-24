package br.projetor.cinetv.dados.usuario

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Ponto de parada de um filme ou episodio. Guarda o id do servidor, sem chave estrangeira:
 * ressincronizar ou recriar o catalogo nao apaga o progresso.
 * chave: "filme:<id>" ou "episodio:<id>".
 */
@Entity(tableName = "progresso", primaryKeys = ["chave"])
data class Progresso(
    val chave: String,
    val tipo: String,
    val idConteudo: Int,
    val titulo: String,
    val capa: String?,
    val extensao: String,
    val posicaoMs: Long,
    val duracaoMs: Long,
    val atualizadoEm: Long,
    /** Para episodio: a serie, para voltar a ela e sugerir o proximo. */
    val serieId: Int? = null,
)

/** Favorito de canal, filme ou serie. chave: "<tipo>:<id>", tipo: "canal", "filme" ou "serie". */
@Entity(tableName = "favorito", primaryKeys = ["chave"])
data class Favorito(
    val chave: String,
    val tipo: String,
    val idConteudo: Int,
    val nome: String,
    val capa: String?,
    val adicionadoEm: Long,
)

@Dao
interface ProgressoDao {

    @Query("SELECT * FROM progresso WHERE chave = :chave")
    suspend fun obter(chave: String): Progresso?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun gravar(progresso: Progresso)

    @Query("DELETE FROM progresso WHERE chave = :chave")
    suspend fun apagar(chave: String)

    @Query("SELECT * FROM progresso WHERE serieId = :serieId ORDER BY atualizadoEm DESC")
    suspend fun daSerie(serieId: Int): List<Progresso>

    @Query("SELECT * FROM progresso WHERE tipo = :tipo ORDER BY atualizadoEm DESC LIMIT :limite")
    fun recentes(tipo: String, limite: Int): Flow<List<Progresso>>
}

@Dao
interface FavoritoDao {

    @Query("SELECT * FROM favorito WHERE tipo = :tipo ORDER BY adicionadoEm DESC")
    fun doTipo(tipo: String): Flow<List<Favorito>>

    @Query("SELECT COUNT(*) > 0 FROM favorito WHERE chave = :chave")
    fun existe(chave: String): Flow<Boolean>

    @Query("SELECT COUNT(*) > 0 FROM favorito WHERE chave = :chave")
    suspend fun existeAgora(chave: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM favorito WHERE chave IN (:chaves)")
    fun existeAlgum(chaves: List<String>): Flow<Boolean>

    @Query("SELECT COUNT(*) > 0 FROM favorito WHERE chave IN (:chaves)")
    suspend fun existeAlgumAgora(chaves: List<String>): Boolean

    @Query("DELETE FROM favorito WHERE chave IN (:chaves)")
    suspend fun apagarTodos(chaves: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun gravar(favorito: Favorito)

    @Query("DELETE FROM favorito WHERE chave = :chave")
    suspend fun apagar(chave: String)
}

/** Dados do usuario. Mudanca de esquema aqui exige Migration escrita: nunca destrutiva. */
@Database(entities = [Progresso::class, Favorito::class], version = 2, exportSchema = false)
abstract class BancoUsuario : RoomDatabase() {

    abstract fun progresso(): ProgressoDao

    abstract fun favoritos(): FavoritoDao

    /** Liga ou desliga o favorito; devolve true quando passou a ser favorito. */
    suspend fun alternarFavorito(tipo: String, id: Int, nome: String, capa: String?): Boolean {
        val chave = "$tipo:$id"
        val dao = favoritos()
        return if (dao.existeAgora(chave)) {
            dao.apagar(chave)
            false
        } else {
            dao.gravar(Favorito(chave, tipo, id, nome, capa, System.currentTimeMillis()))
            true
        }
    }

    /**
     * Favorito de canal vale para o grupo de qualidade (FHD/HD/SD): qualquer variante marcada conta
     * como favorito, e desmarcar remove todas. Marca sempre pelo representante.
     */
    suspend fun alternarFavoritoCanal(representanteId: Int, idsDoGrupo: List<Int>, nome: String, logo: String?): Boolean {
        val chaves = (idsDoGrupo + representanteId).distinct().map { "canal:$it" }
        val dao = favoritos()
        return if (dao.existeAlgumAgora(chaves)) {
            dao.apagarTodos(chaves)
            false
        } else {
            dao.gravar(Favorito("canal:$representanteId", "canal", representanteId, nome, logo, System.currentTimeMillis()))
            true
        }
    }

    companion object {
        /** v1 -> v2: tabela de favoritos (24/10). */
        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorito` (`chave` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
                        "`idConteudo` INTEGER NOT NULL, `nome` TEXT NOT NULL, `capa` TEXT, " +
                        "`adicionadoEm` INTEGER NOT NULL, PRIMARY KEY(`chave`))",
                )
            }
        }

        fun criar(contexto: Context): BancoUsuario =
            Room.databaseBuilder(contexto, BancoUsuario::class.java, "usuario.db")
                .addMigrations(MIGRACAO_1_2)
                .build()
    }
}
