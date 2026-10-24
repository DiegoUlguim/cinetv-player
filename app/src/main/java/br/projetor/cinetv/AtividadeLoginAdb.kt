package br.projetor.cinetv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import br.projetor.cinetv.dados.ArmazenamentoCredenciais
import br.projetor.cinetv.dados.Credenciais
import br.projetor.cinetv.dados.Registro

/**
 * Login sem digitar no controle, pelo ADB:
 * am start -n br.projetor.cinetv/.AtividadeLoginAdb --es servidor ... --es usuario ... --es senha ...
 *
 * Exige WRITE_SECURE_SETTINGS (manifesto): o shell do ADB tem essa permissao e um app comum nao
 * consegue obte-la, entao outro app instalado nao troca o servidor nem a senha do usuario.
 */
class AtividadeLoginAdb : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val servidor = intent.getStringExtra("servidor")
        val usuario = intent.getStringExtra("usuario")
        val senha = intent.getStringExtra("senha")
        if (servidor != null && usuario != null && senha != null) {
            (application as AplicacaoCineTV).credenciais.salvar(Credenciais(servidor, usuario, senha))
            Registro.info("Login recebido por ADB para ${ArmazenamentoCredenciais.normalizarServidor(servidor)} (usuario $usuario)")
            startActivity(
                Intent(this, AtividadePrincipal::class.java)
                    .putExtra(AtividadePrincipal.EXTRA_LOGIN_NOVO, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } else {
            Registro.aviso("Login por ADB sem servidor, usuario ou senha")
        }
        finish()
    }
}
