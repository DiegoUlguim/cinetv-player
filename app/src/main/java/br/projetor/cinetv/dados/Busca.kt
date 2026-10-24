package br.projetor.cinetv.dados

/** Converte o texto digitado na expressao do MATCH do FTS4. */
object Busca {

    /**
     * Cada palavra vira prefixo ("acao" -> acao*), todas obrigatorias. Pontuacao e operadores
     * do FTS (aspas, -, OR, NEAR) sao descartados para o usuario nunca montar uma consulta invalida.
     * Devolve null quando nao sobra nada para buscar.
     */
    fun consultaFts(texto: String): String? {
        val palavras = texto.lowercase()
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotBlank() && it !in OPERADORES }
        if (palavras.isEmpty()) return null
        return palavras.joinToString(" ") { "$it*" }
    }

    private val OPERADORES = setOf("or", "and", "not", "near")
}
