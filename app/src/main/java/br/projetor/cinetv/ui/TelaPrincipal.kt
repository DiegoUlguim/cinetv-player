package br.projetor.cinetv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import br.projetor.cinetv.dados.Credenciais
import br.projetor.cinetv.dados.PreferenciaQualidade
import br.projetor.cinetv.dados.RepositorioGuia
import br.projetor.cinetv.dados.Sincronizador
import br.projetor.cinetv.dados.banco.CatalogoDao
import br.projetor.cinetv.dados.usuario.BancoUsuario

/** Estado da sincronizacao mostrado discretamente no topo. */
sealed interface EstadoSincronizacao {
    data object Pronto : EstadoSincronizacao
    data object Sincronizando : EstadoSincronizacao
    data class Falhou(val mensagem: String) : EstadoSincronizacao
}

private val ABAS = listOf("Ao vivo", "Filmes", "Séries", "Buscar", "Ajustes")
private const val ABA_AJUSTES = 4
private const val AVISAR_VENCIMENTO_DIAS = 7
private const val CHAVE_ULTIMA_ABA = "ultima_aba"

@Composable
fun TelaPrincipal(
    dao: CatalogoDao,
    usuario: BancoUsuario,
    guia: RepositorioGuia,
    estado: EstadoSincronizacao,
    credenciais: Credenciais?,
    aoAbrirCanal: (canalId: Int, deFavoritos: Boolean) -> Unit,
    aoAbrirFilme: (Int) -> Unit,
    aoAbrirSerie: (Int) -> Unit,
    preferenciaQualidade: PreferenciaQualidade,
    estimativaBanda: Long?,
    aoTrocarQualidade: (PreferenciaQualidade) -> Unit,
    aoSalvarLogin: (Credenciais) -> Unit,
    aoAtualizar: () -> Unit,
) {
    // Abre na ultima aba usada (Busca e Ajustes nao contam); sem login, direto nos Ajustes
    val preferencias = LocalContext.current.getSharedPreferences("interface", Context.MODE_PRIVATE)
    var aba by rememberSaveable {
        mutableIntStateOf(if (credenciais == null) ABA_AJUSTES else preferencias.getInt(CHAVE_ULTIMA_ABA, 0))
    }
    LaunchedEffect(aba) {
        if (aba <= 2) preferencias.edit().putInt(CHAVE_ULTIMA_ABA, aba).apply()
    }
    // Ao abrir, o Android foca o primeiro elemento (a aba "Ao vivo") e isso trocaria a aba gravada.
    // O foco inicial vai para a aba certa e so depois o foco passa a trocar de aba.
    val focoAbas = remember { List(ABAS.size) { FocusRequester() } }
    var focoInicialFeito by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { focoAbas[aba].requestFocus() }
        focoInicialFeito = true
    }
    // Primeiro item de cada aba: onde o foco entra quando a aba ainda nao guardou um item focado
    val focoInicialConteudo = remember { FocoInicial() }
    var focoNoConteudo by remember { mutableStateOf(false) }
    // Voltar no conteudo sobe direto para a aba atual (sem subir a lista); na aba, sai do app
    BackHandler(enabled = focoNoConteudo) { runCatching { focoAbas[aba].requestFocus() } }
    // Trocar de aba nao perde a rolagem, o grupo escolhido nem o texto da busca
    val estadosAbas = rememberSaveableStateHolder()
    val expira by remember { dao.metadadoAoVivo(Sincronizador.CHAVE_CONTA_EXPIRA) }.collectAsState(initial = null)
    val diasRestantes = expira?.toLongOrNull()?.let(::diasAte)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Entrar nas abas (subindo do conteudo) foca a aba atual, nunca a mais proxima: a mais
            // proxima trocaria a aba sozinha
            TabRow(
                selectedTabIndex = aba,
                modifier = Modifier
                    .focusProperties { onEnter = { runCatching { focoAbas[aba].requestFocus() } } }
                    .focusGroup(),
            ) {
                ABAS.forEachIndexed { indice, titulo ->
                    Tab(
                        selected = aba == indice,
                        onFocus = { if (focoInicialFeito) aba = indice },
                        modifier = Modifier.focusRequester(focoAbas[indice]),
                    ) {
                        Text(titulo, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
            val aviso = when {
                credenciais == null -> "Configure o login em Ajustes"
                estado is EstadoSincronizacao.Sincronizando -> "Atualizando listas…"
                estado is EstadoSincronizacao.Falhou -> estado.mensagem
                diasRestantes != null && diasRestantes < 0 -> "A conta venceu"
                diasRestantes != null && diasRestantes <= AVISAR_VENCIMENTO_DIAS -> "A conta vence em $diasRestantes dias"
                else -> ""
            }
            Text(
                aviso,
                style = MaterialTheme.typography.labelLarge,
                color = if (diasRestantes != null && diasRestantes <= AVISAR_VENCIMENTO_DIAS) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focoNoConteudo = it.hasFocus }
                .focusRestorer(focoInicialConteudo.alvo),
        ) {
            estadosAbas.SaveableStateProvider(aba) {
                when (aba) {
                    0 -> TelaAoVivo(dao, usuario, guia, aoAbrirCanal, focoInicialConteudo)
                    1 -> TelaFilmes(dao, usuario, aoAbrirFilme, focoInicialConteudo)
                    2 -> TelaSeries(dao, usuario, aoAbrirSerie, focoInicialConteudo)
                    3 -> TelaBusca(dao, { aoAbrirCanal(it, false) }, aoAbrirFilme, aoAbrirSerie, focoInicialConteudo)
                    else -> TelaAjustes(
                        dao, credenciais, estado, preferenciaQualidade, estimativaBanda, aoTrocarQualidade, aoSalvarLogin, aoAtualizar,
                        focoInicialConteudo,
                    )
                }
            }
        }
    }
}
