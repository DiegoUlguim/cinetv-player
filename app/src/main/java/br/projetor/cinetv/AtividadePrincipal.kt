package br.projetor.cinetv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import br.projetor.cinetv.dados.Credenciais
import br.projetor.cinetv.dados.PreferenciaQualidade
import br.projetor.cinetv.dados.Registro
import br.projetor.cinetv.dados.Sincronizador
import br.projetor.cinetv.dados.xtream.ErroServidor
import br.projetor.cinetv.player.AtividadePlayerAoVivo
import br.projetor.cinetv.ui.AtividadeDetalheFilme
import br.projetor.cinetv.ui.AtividadeDetalheSerie
import br.projetor.cinetv.ui.EstadoSincronizacao
import br.projetor.cinetv.ui.TelaPrincipal
import br.projetor.cinetv.ui.TemaCineTV
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AtividadePrincipal : ComponentActivity() {

    private val aplicacao get() = application as AplicacaoCineTV
    private var estado by mutableStateOf<EstadoSincronizacao>(EstadoSincronizacao.Pronto)
    private var credenciais by mutableStateOf<Credenciais?>(null)
    private var preferenciaQualidade by mutableStateOf(PreferenciaQualidade.AUTOMATICA)
    private var estimativaBanda by mutableStateOf<Long?>(null)

    override fun onResume() {
        super.onResume()
        // A estimativa muda enquanto um canal ou filme toca; relida ao voltar para os Ajustes
        preferenciaQualidade = aplicacao.preferenciaQualidade
        estimativaBanda = aplicacao.estimativaBanda
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credenciais = aplicacao.credenciais.ler()

        setContent {
            TemaCineTV {
                TelaPrincipal(
                    dao = aplicacao.banco.catalogo(),
                    usuario = aplicacao.usuario,
                    guia = aplicacao.guia,
                    estado = estado,
                    credenciais = credenciais,
                    aoAbrirCanal = { id, deFavoritos -> AtividadePlayerAoVivo.abrir(this, id, deFavoritos) },
                    aoAbrirFilme = { id -> AtividadeDetalheFilme.abrir(this, id) },
                    aoAbrirSerie = { id -> AtividadeDetalheSerie.abrir(this, id) },
                    preferenciaQualidade = preferenciaQualidade,
                    estimativaBanda = estimativaBanda,
                    aoTrocarQualidade = { nova ->
                        aplicacao.preferenciaQualidade = nova
                        preferenciaQualidade = nova
                    },
                    aoSalvarLogin = { novas ->
                        aplicacao.credenciais.salvar(novas)
                        credenciais = aplicacao.credenciais.ler()
                        sincronizarSePreciso(forcar = true)
                    },
                    aoAtualizar = { sincronizarSePreciso(forcar = true) },
                )
            }
        }

        // Tempo ate a lista aparecer de fato (criterio de aceite), nao so o primeiro quadro
        lifecycleScope.launch {
            aplicacao.banco.catalogo().categorias("live").first { it.isNotEmpty() }
            reportFullyDrawn()
        }
        sincronizarSePreciso(forcar = intent.getBooleanExtra(EXTRA_LOGIN_NOVO, false))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Login novo gravado pela AtividadeLoginAdb
        if (intent.getBooleanExtra(EXTRA_LOGIN_NOVO, false)) {
            credenciais = aplicacao.credenciais.ler()
            sincronizarSePreciso(forcar = true)
        }
    }

    /** Atualiza ao abrir se a lista tiver mais de 24 h (decisao do dono, 24/10). */
    private fun sincronizarSePreciso(forcar: Boolean) {
        val cliente = aplicacao.cliente() ?: return
        val sincronizador = Sincronizador(aplicacao.banco, cliente)
        lifecycleScope.launch {
            if (forcar || sincronizador.precisaSincronizar()) {
                estado = EstadoSincronizacao.Sincronizando
                estado = try {
                    sincronizador.sincronizar()
                    EstadoSincronizacao.Pronto
                } catch (e: ErroServidor) {
                    EstadoSincronizacao.Falhou(e.mensagemUsuario)
                } catch (e: Exception) {
                    Registro.aviso("Falha na sincronizacao", e)
                    EstadoSincronizacao.Falhou("Não foi possível atualizar as listas")
                }
            }
            // Depois da lista (o teste baixa o comeco de um filme do catalogo): no maximo a cada 6 h
            aplicacao.medirVelocidadeSePreciso(forcar)
            estimativaBanda = aplicacao.estimativaBanda
        }
    }

    companion object {
        /** Extra que a AtividadeLoginAdb manda depois de gravar um login: forca a sincronizacao. */
        const val EXTRA_LOGIN_NOVO = "login_novo"
    }
}
