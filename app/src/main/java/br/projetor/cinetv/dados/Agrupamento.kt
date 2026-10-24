package br.projetor.cinetv.dados

import br.projetor.cinetv.dados.banco.Canal

/** Agrupa as variantes de qualidade do mesmo canal (mesma categoria e mesmo nome sem a qualidade). */
object Agrupamento {

    /**
     * Preenche nomeBase, qualidade, grupo e representante. O representante e a primeira variante na ordem
     * do servidor e herda o primeiro guia (epg_channel_id) nao nulo do grupo, porque as variantes nem
     * sempre trazem o guia todas.
     */
    fun agrupar(canais: List<Canal>): List<Canal> {
        val separados = canais.map { canal ->
            val (base, qualidade) = Qualidades.separar(canal.nome)
            canal.copy(
                nomeBase = base,
                qualidade = qualidade?.name,
                grupo = canal.categoriaId + "|" + base.lowercase(),
                representante = false,
            )
        }
        val guiaDoGrupo = separados.groupBy { it.grupo }.mapValues { (_, lista) -> lista.firstNotNullOfOrNull { it.epgId } }
        val vistos = HashSet<String>()
        return separados.map { canal ->
            if (vistos.add(canal.grupo)) canal.copy(representante = true, epgId = canal.epgId ?: guiaDoGrupo[canal.grupo])
            else canal
        }
    }
}
