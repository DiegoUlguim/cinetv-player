package br.projetor.cinetv.dados

import android.content.Context

/** Login do servidor Xtream. O servidor e a URL base, sem barra no fim (ex.: https://host). */
data class Credenciais(val servidor: String, val usuario: String, val senha: String)

/** Guarda o login em SharedPreferences privadas do app (sem backup: allowBackup=false). */
class ArmazenamentoCredenciais(contexto: Context) {

    private val preferencias = contexto.getSharedPreferences("login", Context.MODE_PRIVATE)

    fun ler(): Credenciais? {
        val servidor = preferencias.getString(CHAVE_SERVIDOR, null) ?: return null
        val usuario = preferencias.getString(CHAVE_USUARIO, null) ?: return null
        val senha = preferencias.getString(CHAVE_SENHA, null) ?: return null
        return Credenciais(servidor, usuario, senha)
    }

    fun salvar(credenciais: Credenciais) {
        preferencias.edit()
            .putString(CHAVE_SERVIDOR, normalizarServidor(credenciais.servidor))
            .putString(CHAVE_USUARIO, credenciais.usuario.trim())
            .putString(CHAVE_SENHA, credenciais.senha.trim())
            .apply()
        Registro.registrarSegredos(credenciais.usuario.trim(), credenciais.senha.trim())
    }

    companion object {
        private const val CHAVE_SERVIDOR = "servidor"
        private const val CHAVE_USUARIO = "usuario"
        private const val CHAVE_SENHA = "senha"

        /** Aceita "host", "host:porta" ou URL completa; devolve sempre "esquema://host[:porta]". */
        fun normalizarServidor(bruto: String): String {
            val semEspaco = bruto.trim().trimEnd('/')
            val comEsquema = if (semEspaco.startsWith("http://") || semEspaco.startsWith("https://")) {
                semEspaco
            } else {
                "https://$semEspaco"
            }
            // Descarta caminho colado por engano (ex.: .../player_api.php?...)
            val inicioCaminho = comEsquema.indexOf('/', comEsquema.indexOf("://") + 3)
            return if (inicioCaminho > 0) comEsquema.substring(0, inicioCaminho) else comEsquema
        }
    }
}
