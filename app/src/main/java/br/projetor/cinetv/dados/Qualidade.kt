package br.projetor.cinetv.dados

/** Qualidade declarada no nome do canal pelo servidor ("… FHD", "… HD", "… SD"). */
enum class Qualidade(val rotulo: String, val nivel: Int) {
    SD("SD", 1), HD("HD", 2), FHD("FHD", 3), UHD("4K", 4);
}

/** Escolha do dono nos Ajustes. */
enum class PreferenciaQualidade(val rotulo: String) {
    AUTOMATICA("Automática"), FHD("FHD"), HD("HD"), SD("SD");
}

object Qualidades {

    private val SUFIXO = Regex("""[\s\-|]+(FHD|FULL ?HD|UHD|4K|HD|SD)\s*$""", RegexOption.IGNORE_CASE)

    // Limites medidos em 24/10 pelo PCR de 5 trios: FHD 2,7–6,5 Mbps, HD 1,6–4,2, SD 0,7–2,7 (margem de 1,5x)
    const val LIMITE_FHD_BPS = 10_000_000L
    const val LIMITE_HD_BPS = 6_000_000L

    /** Separa "01 - Globo SP Capital FHD" em ("01 - Globo SP Capital", FHD). Sem sufixo: (nome, null). */
    fun separar(nome: String): Pair<String, Qualidade?> {
        val achado = SUFIXO.find(nome) ?: return nome.trim() to null
        val qualidade = when (achado.groupValues[1].uppercase().replace(" ", "")) {
            "FHD", "FULLHD" -> Qualidade.FHD
            "UHD", "4K" -> Qualidade.UHD
            "HD" -> Qualidade.HD
            else -> Qualidade.SD
        }
        val base = nome.substring(0, achado.range.first).trim()
        return if (base.isEmpty()) nome.trim() to null else base to qualidade
    }

    /** Qualidade que a regra quer usar. Sem estimativa de banda, HD (o meio-termo seguro). */
    fun alvo(preferencia: PreferenciaQualidade, estimativaBps: Long?): Qualidade = when (preferencia) {
        PreferenciaQualidade.FHD -> Qualidade.FHD
        PreferenciaQualidade.HD -> Qualidade.HD
        PreferenciaQualidade.SD -> Qualidade.SD
        PreferenciaQualidade.AUTOMATICA -> when {
            estimativaBps == null -> Qualidade.HD
            estimativaBps >= LIMITE_FHD_BPS -> Qualidade.FHD
            estimativaBps >= LIMITE_HD_BPS -> Qualidade.HD
            else -> Qualidade.SD
        }
    }

    /**
     * Indice da variante a abrir: a maior qualidade que nao passa do alvo; se todas passam, a menor.
     * Variante sem qualidade no nome conta como HD.
     */
    fun escolher(disponiveis: List<Qualidade?>, alvo: Qualidade): Int {
        if (disponiveis.isEmpty()) return -1
        val niveis = disponiveis.map { (it ?: Qualidade.HD).nivel }
        val cabem = niveis.withIndex().filter { it.value <= alvo.nivel }
        return (cabem.maxByOrNull { it.value } ?: niveis.withIndex().minBy { it.value }).index
    }

    /** Proxima variante abaixo da atual, para quando a internet nao da conta. */
    fun abaixo(disponiveis: List<Qualidade?>, atual: Int): Int? {
        val nivelAtual = (disponiveis.getOrNull(atual) ?: Qualidade.HD).nivel
        return disponiveis.withIndex()
            .filter { (it.value ?: Qualidade.HD).nivel < nivelAtual }
            .maxByOrNull { (it.value ?: Qualidade.HD).nivel }?.index
    }
}
