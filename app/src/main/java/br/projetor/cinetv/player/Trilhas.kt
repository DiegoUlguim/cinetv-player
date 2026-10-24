package br.projetor.cinetv.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import br.projetor.cinetv.dados.Registro
import java.util.Locale

/** Troca de audio e legenda por ciclo: cada toque no botao passa para a proxima opcao. */
object Trilhas {

    data class Opcao(val rotulo: String, val grupo: Int, val faixa: Int)

    fun opcoes(player: Player, tipo: Int): List<Opcao> {
        val resultado = mutableListOf<Opcao>()
        player.currentTracks.groups.forEachIndexed { indiceGrupo, grupo ->
            if (grupo.type != tipo) return@forEachIndexed
            for (faixa in 0 until grupo.length) {
                if (!grupo.isTrackSupported(faixa)) continue
                val formato = grupo.getTrackFormat(faixa)
                val rotulo = formato.label
                    ?: formato.language?.let { Locale(it).getDisplayLanguage(Locale("pt", "BR")).replaceFirstChar(Char::uppercase) }
                    ?: "Faixa ${resultado.size + 1}"
                resultado += Opcao(rotulo, indiceGrupo, faixa)
            }
        }
        return resultado
    }

    fun atual(player: Player, tipo: Int): Opcao? {
        val opcoes = opcoes(player, tipo)
        val grupos = player.currentTracks.groups
        return opcoes.firstOrNull { grupos[it.grupo].isTrackSelected(it.faixa) }
    }

    /** Audio: proxima faixa. Legenda: proxima faixa e, depois da ultima, desligada. */
    fun avancar(player: Player, tipo: Int) {
        val opcoes = opcoes(player, tipo)
        registrarEstado(player, tipo, "antes")
        if (opcoes.isEmpty()) return
        val atual = atual(player, tipo)
        val proxima = if (atual == null) 0 else opcoes.indexOf(atual) + 1
        val parametros = player.trackSelectionParameters.buildUpon()
        if (proxima >= opcoes.size && tipo == C.TRACK_TYPE_TEXT) {
            parametros.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val escolhida = opcoes[proxima % opcoes.size]
            val grupo = player.currentTracks.groups[escolhida.grupo].mediaTrackGroup
            parametros.setTrackTypeDisabled(tipo, false).setOverrideForType(TrackSelectionOverride(grupo, escolhida.faixa))
        }
        player.trackSelectionParameters = parametros.build()
        registrarEstado(player, tipo, "pedido")
    }

    /**
     * Estado das trilhas no log, para investigar a legenda que so aparece no segundo toque:
     * faixa selecionada, marcas de forcada/padrao e se o tipo esta desligado nos parametros.
     */
    fun registrarEstado(player: Player, tipo: Int, momento: String) {
        val nome = if (tipo == C.TRACK_TYPE_TEXT) "legenda" else "audio"
        val faixas = player.currentTracks.groups.filter { it.type == tipo }.flatMap { grupo ->
            (0 until grupo.length).map { faixa ->
                val formato = grupo.getTrackFormat(faixa)
                val marcas = listOfNotNull(
                    "selecionada".takeIf { grupo.isTrackSelected(faixa) },
                    "forcada".takeIf { formato.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
                    "padrao".takeIf { formato.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 },
                    "sem suporte".takeIf { !grupo.isTrackSupported(faixa) },
                )
                "${formato.language ?: "?"}/${formato.sampleMimeType ?: "?"}" + marcas.joinToString(",", "[", "]")
            }
        }
        if (faixas.isEmpty()) return
        val desligado = tipo in player.trackSelectionParameters.disabledTrackTypes
        Registro.info("Trilhas $nome ($momento): desligado=$desligado faixas=$faixas")
    }
}
