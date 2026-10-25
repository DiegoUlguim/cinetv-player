# CineTV para TVs LG (webOS)

O CineTV para TVs LG: as mesmas abas (Ao vivo, Filmes, Séries, Buscar, Ajustes) e a mesma navegação pelo controle
do app Android, rodando direto na TV. Funciona em TVs LG de **2019 ou mais novas** (webOS 4.5+, testado na
SM8600PSA, cujo navegador é o Chrome 53).

O login fica só na TV. Usuário e senha não aparecem no log do app.

## O que precisa

- TV LG de 2019 ou mais nova, na mesma rede do PC.
- Conta LG de desenvolvedor, gratuita: https://webostv.developer.lge.com (Sign in → Create account).
- PC com [Node.js](https://nodejs.org) 18 ou mais novo e Git. Os comandos abaixo funcionam no PowerShell, no
  Prompt de Comando e no Bash.
- O login de um servidor Xtream seu: servidor (`http://host:porta`), usuário e senha. Se você tem só um link
  M3U, o servidor é o trecho até a porta, e usuário e senha estão no próprio link.

## 1. Na TV: modo desenvolvedor

1. Na LG Content Store, instale o app **Developer Mode** e entre com a conta de desenvolvedor.
2. No app, ligue **Dev Mode Status**. A TV reinicia.
3. Abra o app de novo e ligue **Key Server**.
4. Anote o **IP** e a **Passphrase** que aparecem na tela.

O modo desenvolvedor dura até 1000 h. Para renovar, use **Extend Session Time** no mesmo app; se vencer, o webOS
remove os apps instalados por ele.

## 2. No PC: preparar

```
git clone https://github.com/DiegoUlguim/cinetv-player
cd cinetv-player
git switch webos-app
cd webos
npm install
```

O `npm` pode avisar que bloqueou scripts de instalação (`ssh2`, `esbuild`). Pode ignorar.

Cadastre a TV (troque `IP_DA_TV`) e pegue a chave dela (vai pedir a Passphrase do passo 1):

```
npx ares-setup-device -a tv -i "host=IP_DA_TV" -i "port=9922" -i "username=prisoner"
npx ares-novacom -d tv --getkey
```

## 3. Instalar o CineTV

```
npm run empacotar
npm run instalar
```

O ícone **CineTV** aparece na lista de apps da TV. Para entrar sem digitar no controle, abra pelo PC com o login
uma vez (depois ele fica salvo na TV):

```
npx ares-launch -d tv br.projetor.cinetv -p "servidor=http://host:porta" -p "usuario=SEU_USUARIO" -p "senha=SUA_SENHA"
```

Também dá para preencher o login na aba Ajustes, com o teclado da TV.

Na primeira abertura o app baixa as listas (alguns segundos); depois, só quando tiverem mais de 24 h.

## Usar

| Tela | Controle |
|---|---|
| Abas | ◀ ▶ troca de aba, ▼ entra; Voltar fecha o app |
| Conteúdo | Voltar sobe direto para a aba; ▲ no topo também |
| Ao vivo | ▲ ▼ grupos; ▶ entra nos canais do grupo (no último usado); ◀ volta ao grupo |
| Filmes e Séries | cada fileira lembra a última capa; OK abre o detalhe |
| Qualquer canal, filme ou série | **segurar OK** liga e desliga o favorito |
| Player ao vivo | ▲ ▼ troca de canal; ◀ ▶ lista de canais; OK mostra agora/a seguir, favoritar e canal anterior |
| Player de filme | ◀ ▶ volta e avança 10 s; OK pausa; Voltar sai (o ponto fica salvo) |

Fora desta versão: troca de faixa de áudio e de legenda (a TV não mostrou as faixas embutidas no teste).

## Atualizar e remover

Para atualizar, `git pull`, `npm run empacotar` e `npm run instalar` de novo: o login e as listas continuam.

```
npx ares-install -d tv --remove br.projetor.cinetv
```

## Problemas

- **Log do app:** `npx ares-inspect -d tv --app br.projetor.cinetv --open`, aba Console (linhas `[CineTV]`, já
  sem usuário e senha). A aba Network mostra as URLs de vídeo, que levam usuário e senha no caminho (é assim no
  Xtream): não mande print dela.
- **"O servidor não respondeu ou bloqueou o acesso desta TV":** o servidor recusou o app da TV (alguns filtram
  por aparelho) ou a internet caiu.
- **Canal "não foi possível abrir":** o app tenta 3 vezes. Pode ser o canal fora do ar ou a conta em uso em outro
  aparelho (muitas contas aceitam uma conexão só).

## Para desenvolver

- Código em `app/src` (Preact, compilado pelo `esbuild` para o Chrome 53); regras portadas do Android em
  `app/src/dados/regras.js`, com testes em `test/` (`npm test`).
- `npm run construir` gera `construido/`; abrindo `construido/index.html` no navegador do PC, as setas, Enter e
  Esc imitam o controle. O login vai no endereço: `index.html#{"servidor":"...","usuario":"...","senha":"..."}`.
- A pasta `prova/` tem a prova do T0 (o que o webOS aceita), mantida para testar TVs novas:
  `npm run prova:empacotar`, `npx ares-install -d tv saida/br.projetor.cinetv.prova_0.0.1_all.ipk`.

## Para agentes de IA

Se você é um agente ajudando a instalar ou alterar: siga os passos na ordem e não grave usuário, senha ou servidor
em arquivos, commits ou mensagens. Peça ao usuário para digitar o comando com a senha ele mesmo. Não procure
servidores, listas ou provedores de IPTV. Regra que mudar aqui e existir no app Android deve mudar nos dois.
