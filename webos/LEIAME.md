# CineTV para TVs LG (webOS): prova na TV

Esta pasta ainda **não tem o app para LG**. Por enquanto ela tem só uma **prova**: um app pequeno que roda na
TV, testa o que o webOS aceita (servidor, vídeo, faixas, memória) e mostra uma tabela de resultados. É essa
tabela que decide como o app de verdade vai ser feito.

A prova não grava nada fora da TV. O login fica só na TV, e usuário e senha aparecem mascarados na tela e no log.

## O que precisa

- TV LG de 2021 ou mais nova (webOS 6+), na mesma rede do PC.
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
remove o app instalado.

## 2. No PC: preparar

```
git clone https://github.com/DiegoUlguim/cinetv-player
cd cinetv-player
git switch webos-t0
cd webos
npm install
```

O `npm` pode avisar que bloqueou um script de instalação do `ssh2`. Pode ignorar: é um acelerador opcional.

Cadastre a TV (troque `IP_DA_TV`) e pegue a chave dela (vai pedir a Passphrase do passo 1):

```
npx ares-setup-device -a tv -i "host=IP_DA_TV" -i "port=9922" -i "username=prisoner"
npx ares-novacom -d tv --getkey
```

## 3. Instalar e rodar a prova

```
npm run prova:empacotar
npx ares-install -d tv saida/br.projetor.cinetv.prova_0.0.1_all.ipk
npx ares-launch -d tv br.projetor.cinetv.prova -p "servidor=http://host:porta" -p "usuario=SEU_USUARIO" -p "senha=SUA_SENHA"
```

A prova leva de 3 a 5 minutos: testa a API, a lista de filmes, o servidor de vídeo, 12 canais ao vivo, um
filme e as faixas de áudio e legenda. Feche o app do servidor em outros aparelhos antes, porque muitas contas
aceitam uma conexão só.

Enquanto ela roda, se quiser, acompanhe a memória:

```
npx ares-device -d tv -r -id br.projetor.cinetv.prova -t 5
```

## 4. Mandar o resultado

Quando aparecer "Fim." no alto da tela, **tire uma foto da tabela inteira** e mande para quem pediu o teste. Ela
já sai sem usuário e senha.

Para ver o log completo, use `npx ares-inspect -d tv --app br.projetor.cinetv.prova --open` e copie o console.
Atenção: a aba Network desse inspetor mostra as URLs de vídeo, que levam usuário e senha no caminho (é assim no
Xtream). Não mande print dessa aba.

## 5. Remover

```
npx ares-install -d tv --remove br.projetor.cinetv.prova
```

## Para agentes de IA

Se você é um agente ajudando a rodar a prova: siga os passos na ordem e não grave usuário, senha ou servidor em
arquivos, commits ou mensagens. Peça ao usuário para digitar o comando de abertura (passo 3) ele mesmo. Não
procure servidores, listas ou provedores de IPTV.

O que fazer em cada falha:

- `ares-novacom` falhando: confira se o Key Server está ligado e se o IP está certo.
- A linha "API: login" falhando: anote a mensagem. Ela distingue bloqueio do servidor (HTML ou `TypeError`) de
  senha recusada (`auth=0`).
