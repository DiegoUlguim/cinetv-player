package br.projetor.cinetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import br.projetor.cinetv.dados.Credenciais
import br.projetor.cinetv.dados.PreferenciaQualidade
import br.projetor.cinetv.dados.Qualidades
import br.projetor.cinetv.dados.Sincronizador
import br.projetor.cinetv.dados.banco.CatalogoDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** So o essencial: login, atualizar as listas e a situacao da conta. */
@Composable
fun TelaAjustes(
    dao: CatalogoDao,
    credenciais: Credenciais?,
    estado: EstadoSincronizacao,
    preferenciaQualidade: PreferenciaQualidade,
    estimativaBanda: Long?,
    aoTrocarQualidade: (PreferenciaQualidade) -> Unit,
    aoSalvarLogin: (Credenciais) -> Unit,
    aoAtualizar: () -> Unit,
    focoInicial: FocoInicial,
) {
    var servidor by remember { mutableStateOf(credenciais?.servidor.orEmpty()) }
    var usuario by remember { mutableStateOf(credenciais?.usuario.orEmpty()) }
    var senha by remember { mutableStateOf(credenciais?.senha.orEmpty()) }
    val ultima by remember { dao.metadadoAoVivo(Sincronizador.CHAVE_ULTIMA) }.collectAsState(initial = null)
    val status by remember { dao.metadadoAoVivo(Sincronizador.CHAVE_CONTA_STATUS) }.collectAsState(initial = null)
    val expira by remember { dao.metadadoAoVivo(Sincronizador.CHAVE_CONTA_EXPIRA) }.collectAsState(initial = null)
    val formato = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Conta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        val situacao = listOfNotNull(
            status?.takeIf { it.isNotEmpty() }?.let { if (it.equals("Active", true)) "Ativa" else it },
            expira?.toLongOrNull()?.let { "válida até ${formato.format(Date(it * 1000)).substringBefore(' ')} (${diasAte(it)} dias)" },
        ).joinToString(" · ").ifEmpty { "sem informação ainda" }
        Info("Situação", situacao)
        Info("Última atualização das listas", ultima?.toLongOrNull()?.let { formato.format(Date(it)) } ?: "nunca")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = aoAtualizar, modifier = Modifier.focoInicial(focoInicial), enabled = estado !is EstadoSincronizacao.Sincronizando) {
                Text(if (estado is EstadoSincronizacao.Sincronizando) "Atualizando…" else "Atualizar listas agora")
            }
        }

        Text("Qualidade dos canais", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        val medida = estimativaBanda?.let { "%.1f Mbps".format(it / 1_000_000.0) } ?: "ainda não medida"
        Info(
            "Velocidade da internet (medida ao abrir o app, a cada 6 h)",
            if (preferenciaQualidade == PreferenciaQualidade.AUTOMATICA) {
                "$medida → usa ${Qualidades.alvo(preferenciaQualidade, estimativaBanda).rotulo}"
            } else {
                medida
            },
        )
        Button(onClick = {
            val opcoes = PreferenciaQualidade.entries
            aoTrocarQualidade(opcoes[(opcoes.indexOf(preferenciaQualidade) + 1) % opcoes.size])
        }) { Text("Qualidade: ${preferenciaQualidade.rotulo}") }
        Text(
            "Automática: FHD a partir de 10 Mbps, HD a partir de 6 Mbps, SD abaixo disso. Se travar, desce sozinha.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Login do servidor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        Campo("Servidor (ex.: https://servidor.com)", servidor, { servidor = it }, KeyboardType.Uri)
        Campo("Usuário", usuario, { usuario = it }, KeyboardType.Text)
        Campo("Senha", senha, { senha = it }, KeyboardType.Password, senha = true)
        OutlinedButton(
            onClick = { aoSalvarLogin(Credenciais(servidor, usuario, senha)) },
            enabled = servidor.isNotBlank() && usuario.isNotBlank() && senha.isNotBlank(),
        ) { Text("Salvar login e atualizar") }
    }
}

/** Dias inteiros ate a data em epoch (s); negativo se ja venceu. */
fun diasAte(epochSegundos: Long): Long = (epochSegundos * 1000 - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)

@Composable
private fun Info(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, aoMudar: (String) -> Unit, tipo: KeyboardType, senha: Boolean = false) {
    var focado by remember { mutableStateOf(false) }
    val foco = LocalFocusManager.current
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CampoTexto(
            valor = valor,
            aoMudar = aoMudar,
            estilo = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            teclado = KeyboardOptions(keyboardType = tipo),
            transformacao = if (senha) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .padding(top = 4.dp)
                .width(520.dp)
                .sairComSetas(foco)
                .onFocusChanged { focado = it.isFocused }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(2.dp, if (focado) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
