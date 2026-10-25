# Instruções para agentes de IA

Este arquivo orienta agentes (Claude Code, Codex, Copilot, Cursor e afins) que ajudam a **instalar**,
**configurar** ou **alterar** o CineTV. O README é para pessoas; aqui está o que um agente precisa
para não errar.

Responda ao usuário em português do Brasil.

## Limites do projeto

O CineTV é só um player para servidores **Xtream** (`player_api.php`) que o usuário já contratou.

- Não ajude a encontrar listas, servidores ou fontes de IPTV, nem inclua conteúdo, listas ou URLs de servidores no código.
- Não faça engenharia reversa de apps ou APIs privadas de provedores (decompilar, interceptar tráfego) para descobrir endereços.
  A URL vem do provedor (link M3U ou "URL Xtream").
- Nunca grave usuário, senha ou URL de servidor real em código, testes, commits, issues ou logs. Os testes usam
  servidor falso (`MockWebServer`) e dados inventados.

## Ajudar a instalar e configurar

O que o usuário precisa: um aparelho Android TV ou projetor Android (Android 9+), um PC com o Android Studio
instalado (pelo JDK e pelo SDK) e o login do servidor Xtream.

1. **Preparar o aparelho para o ADB:**
   - Configurações → Sobre → tocar 7 vezes em "Build" ou "Versão" para liberar as opções do desenvolvedor.
   - Ligar a depuração USB e, se houver, a depuração por rede.
   - No PC: `adb connect <ip-do-aparelho>:5555` e aceitar a autorização na tela da TV.
2. **Chave de release:**
   - O passo a passo está no README (seção "Compilar") e em `assinatura.properties.exemplo`.
   - Sem a chave, o build gera `br.projetor.cinetv.dev`, que instala ao lado de uma versão assinada.
3. **Compilar e instalar:**
   - Windows: `set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, depois `gradlew.bat assembleRelease`
     (em Linux e Mac, `./gradlew`).
   - Em seguida, `adb install -r app/build/outputs/apk/release/app-release.apk`.
4. **Login:**
   - Pela aba Ajustes do app.
   - Ou pelo ADB: `adb shell am start -n br.projetor.cinetv/.AtividadeLoginAdb --es servidor <url> --es usuario <u> --es senha <s>`.
   - O servidor é só a base (`http://host:porta`). Se o usuário tiver um link M3U
     (`http://host:porta/get.php?username=...&password=...`), o servidor é o trecho até a porta, e usuário e
     senha vêm dos parâmetros.
5. **Diagnosticar:**
   - Log do app: `adb logcat -s CineTV` (usuário e senha saem mascarados).
   - Para testar o servidor pelo PC: `curl "<servidor>/player_api.php?username=<u>&password=<s>"` deve
     responder JSON com `user_info.auth = 1`.
   - Mostre ao usuário só o resultado, nunca a senha.
   - Servidor atrás do Cloudflare pode bloquear o User-Agent do `curl`; use `-A okhttp/4.12.0`.

## Ferramentas e versões

Kotlin 2.1.21, Compose for TV (`tv-material` 1.0.0, BOM 2025.05.00), Media3 1.6.1, Room 2.7.1 com KSP,
OkHttp 4.12, kotlinx.serialization 1.8.1 e Coil 3.

Gradle 8.14.3, AGP 8.9.1, JDK 17 (o `jbr` do Android Studio). `compileSdk` 35, `minSdk` 28,
`targetSdk` 30. ABIs `armeabi-v7a` e `x86` (aparelho de 32 bits e emulador).

| Comando | Para quê |
|---|---|
| `gradlew testDebugUnitTest` | testes JVM das regras (rápidos, sem aparelho) |
| `gradlew connectedDebugAndroidTest` | testes instrumentados (sincronização e migração); o APK de debug não instala por cima de um release com outra chave: desinstale antes |
| `gradlew assembleRelease` | APK de release (R8 ligado) |
| `bash medir_desempenho.sh <ip:porta>` | fluidez das listas no aparelho (gfxinfo), roteiro fixo de teclas |
| `bash medir_zapping.sh <id_canal> ts 10 <ip:porta>` | tempo de troca de canal |

## Mapa do código

Raiz do código: `app/src/main/java/br/projetor/cinetv/`.

| Pasta | Conteúdo |
|---|---|
| raiz | `AplicacaoCineTV` (dependências), `AtividadePrincipal`, `AtividadeLoginAdb` |
| `dados/` | sincronização, guia, qualidade, agrupamento FHD/HD/SD, busca, teste de velocidade, log mascarado (`Registro`) |
| `dados/xtream/` | cliente da API e leitores JSON tolerantes (servidores variam muito) |
| `dados/banco/` | catálogo (Room) |
| `dados/usuario/` | progresso e favoritos (Room) |
| `ui/` | telas Compose e componentes de foco (`ItemFoco`, `CapaFoco`, `FocoInicial`, `CampoTexto`) |
| `player/` | players ao vivo e sob demanda, fábrica do ExoPlayer, trilhas |

## Regras ao alterar

- **Nomes em português** (classes, funções, variáveis, arquivos), como no resto do código.
- **Dois bancos com regras opostas:**
  - `cinetv.db` (catálogo) é cache: mudou o esquema, suba a `version` em `BancoCineTV`, e ele é recriado.
  - `usuario.db` (progresso e favoritos) **nunca** pode ser recriado: mudança de esquema exige uma `Migration`
    nova em `BancoUsuario` e um teste instrumentado dela.
- **Aparelho fraco é o alvo** (1 GB de RAM, 32 bits):
  - Não use componentes do `tv-material` que animam escala no foco (`ListItem`, `Card`); use `ItemFoco` e `CapaFoco`.
  - Listas longas são preguiçosas e paginadas.
  - Mudou lista ou tela de rolagem? Meça com `medir_desempenho.sh` antes e depois.
- **Foco pelo controle:**
  - Pedir foco a um `FocusRequester` cujo item saiu da composição derruba o app; use `FocoInicial`.
  - Em lista paginada, `focusRestorer` e `focusProperties { onEnter }` não se mostraram confiáveis (ver `TelaAoVivo`);
    as setas entre colunas são tratadas com `onPreviewKeyEvent`.
  - O Voltar sobe para a aba atual antes de sair.
- **Uma conexão por conta:**
  - O player fecha o fluxo anterior antes de abrir outro, nunca pré-carrega e libera a conexão ao sair.
  - Não crie chamadas paralelas de vídeo.
- **Servidores variam:**
  - Leia JSON de forma tolerante (campo ausente, número como texto, `[]` no lugar de objeto).
  - HTML no lugar de JSON é bloqueio.
  - Cubra o caso novo com teste em `app/src/test`.
- **Log:** só pelo `Registro`, que mascara usuário e senha. As URLs do Xtream levam os dois no caminho.
- **Categorias adultas** do servidor ficam fora do catálogo e da busca; não remova esse filtro.

## Fluxo de mudança

- A `main` é protegida: toda mudança vai numa branch e entra por pull request.
- Antes do PR: `gradlew testDebugUnitTest assembleRelease` sem erro; se mexeu em navegação ou player, teste no
  emulador de Android TV ou no aparelho.
- Mensagem de commit em português, dizendo o que o app passa a fazer e por quê.
