package br.projetor.cinetv.dados.banco

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Categoria::class, Canal::class, Filme::class, Serie::class, DetalheFilme::class, Metadado::class,
        CanalFts::class, FilmeFts::class, SerieFts::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class BancoCineTV : RoomDatabase() {

    abstract fun catalogo(): CatalogoDao

    companion object {
        fun criar(contexto: Context): BancoCineTV =
            Room.databaseBuilder(contexto, BancoCineTV::class.java, "cinetv.db")
                // Catalogo e cache do servidor: em mudanca de esquema, recria e sincroniza de novo.
                // Dados do usuario (progresso, favoritos) ficam no BancoUsuario, que nunca e recriado.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
