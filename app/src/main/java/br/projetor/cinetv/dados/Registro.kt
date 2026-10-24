package br.projetor.cinetv.dados

import android.util.Log

/**
 * Log do app com mascaramento: as URLs do Xtream levam usuario e senha no caminho
 * (/live/{u}/{p}/...), entao todo texto passa por aqui antes de ir para o logcat.
 */
object Registro {

    private const val TAG = "CineTV"

    @Volatile
    private var segredos: List<String> = emptyList()

    fun registrarSegredos(vararg valores: String) {
        segredos = valores.filter { it.length >= 3 }
    }

    fun mascarar(texto: String): String {
        var resultado = texto
        for (segredo in segredos) {
            resultado = resultado.replace(segredo, "***")
        }
        return resultado
    }

    fun info(mensagem: String) {
        Log.i(TAG, mascarar(mensagem))
    }

    fun aviso(mensagem: String, erro: Throwable? = null) {
        Log.w(TAG, mascarar(mensagem) + (erro?.let { ": " + mascarar(it.toString()) } ?: ""))
    }
}
