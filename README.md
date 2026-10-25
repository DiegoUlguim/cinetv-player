# CineTV

Player IPTV simples para Android TV e projetores Android, feito para o controle remoto: poucos menus,
abertura rápida e visual limpo. Lê servidores no padrão **Xtream** (`player_api.php`): canais ao vivo,
filmes e séries.

O CineTV é só um player: **não traz canal, filme nem lista**. Ele abre o servidor que você informar, e o
conteúdo e a licença dele são responsabilidade de quem contrata o serviço.

Feito em Kotlin com Compose for TV, Media3 (ExoPlayer) e Room. Testado num projetor HY320
(Allwinner H713, Android 11, 32 bits, 1 GB de RAM), por isso o cuidado com memória e desempenho.
Funciona a partir do Android 9 (`minSdk` 28).

## O que tem

- **Ao vivo:** canais agrupados (FHD/HD/SD do mesmo canal viram uma linha, e a qualidade é escolhida pela
  velocidade da internet), programa atual na lista, grupo "★ Favoritos". No player: cima/baixo troca de
  canal, esquerda/direita abre a lista do grupo, OK mostra a faixa (agora/a seguir, favoritar, canal
  anterior, áudio e legenda).
- **Filmes e Séries:** fileiras por categoria, "Continuar assistindo" e "Minha lista" no topo, detalhe com
  sinopse, retomada do ponto e próximo episódio automático.
- **Buscar:** sem acento ("acao" acha "Ação") em canais, filmes e séries.
- **Ajustes:** login, validade da conta, atualizar listas e qualidade (Automática / FHD / HD / SD).
- Segurar OK em qualquer canal, filme ou série liga e desliga o favorito.
- Categorias adultas do servidor ficam escondidas, inclusive da busca.

## Navegação pelo controle

- **Voltar** em qualquer ponto de uma aba sobe direto para a aba atual; na aba, fecha o app.
- Subir do conteúdo volta à aba atual, e descer da aba volta ao item de onde saiu.
- Cada aba guarda a posição, e cada fileira de capas lembra a última capa.
- Ao vivo: a esquerda volta ao grupo selecionado; a direita entra no último canal usado no grupo.
- Série: abre na temporada de onde parou, e a direita vai ao episódio de retomar.
- Busca: o cursor volta ao fim do texto, e cada busca nova recomeça do primeiro resultado.

## Comportamento

- As listas se atualizam ao abrir o app quando têm mais de 24 h.
- A velocidade da internet é medida ao abrir, no máximo a cada 6 h, baixando o começo de um filme.
  Automática: FHD a partir de 10 Mbps, HD a partir de 6, SD abaixo. Se travar 2 vezes em 1 min, desce uma.
- Sair do player fecha a conexão na hora (contas de 1 conexão ficam livres para outro aparelho).
- Usuário e senha são mascarados no log do Android.
- Dados no aparelho: `cinetv.db` (catálogo, recriado quando o esquema muda) e `usuario.db` (progresso e
  favoritos, nunca recriado; mudança de esquema exige migração).

## Compilar

Precisa do Android Studio instalado (pelo JDK e pelo SDK). Os comandos abaixo são para Windows.

1. Crie a chave de release uma vez, na raiz do projeto:
   `"%ProgramFiles%\Android\Android Studio\jbr\bin\keytool" -genkeypair -keystore assinatura-iptv.jks -alias cinetv -keyalg RSA -keysize 2048 -validity 36500`
2. Copie `assinatura.properties.exemplo` para `assinatura.properties` e ponha a senha da chave
   (nome do arquivo e apelido também podem ser trocados lá).
3. Compile e instale:

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat testDebugUnitTest assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell cmd package compile -m speed-profile -f br.projetor.cinetv
```

Sem a chave (passos 1 e 2), o release sai assinado com a chave de debug como `br.projetor.cinetv.dev`, que
instala ao lado e não substitui um CineTV já instalado.

Guarde a chave e o `assinatura.properties` (os dois ficam fora do Git). Uma atualização assinada com outra
chave exige desinstalar o app, e isso apaga login, progresso e favoritos.

## Login

Em **Ajustes**, ou pelo ADB, sem digitar no controle:

```
adb shell am start -n br.projetor.cinetv/.AtividadeLoginAdb --es servidor <host> --es usuario <usuario> --es senha <senha>
```

Essa tela só abre pelo ADB: ela exige uma permissão que o shell do ADB tem e os apps comuns não, para outro
app instalado não trocar o seu login.

## Medir

Com o app instalado e o aparelho no ADB por rede:

- `bash medir_desempenho.sh <ip:porta>`: fluidez das listas (gfxinfo) num roteiro fixo de teclas.
- `bash medir_zapping.sh <id_do_canal> ts 10 <ip:porta>`: tempo de troca de canal.

## Com um agente de IA

Para instalar, configurar ou alterar o app com a ajuda de um agente (Claude Code, Codex, Copilot, Cursor),
as instruções para ele estão em `AGENTS.md`.

## Licença

MIT, em `LICENSE`.
