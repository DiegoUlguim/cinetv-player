#!/usr/bin/env bash
# Portao de desempenho do CineTV no projetor: roteiro fixo de D-pad e gfxinfo zerado antes de cada tela.
# Uso: bash medir_desempenho.sh [endereco]   (padrao 192.168.0.7:5555)
# ADB do PATH, ou o do SDK do usuario no Windows, ou o que vier na variavel ADB
adb="${ADB:-$(command -v adb || echo "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")}"
aparelho="${1:-192.168.0.7:5555}"
pacote=br.projetor.cinetv
a() { "$adb" -s "$aparelho" "$@"; }
resumo() {
    a shell dumpsys gfxinfo $pacote | tr -d '\r' \
        | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" | grep -v legacy
}

echo "acessibilidade ativa: $(a shell settings get secure enabled_accessibility_services | tr -d '\r')"
a shell cmd package compile -m speed-profile -f $pacote >/dev/null
a shell am force-stop $pacote; sleep 2
a shell am start -W -n $pacote/.AtividadePrincipal >/dev/null; sleep 5
# O app abre na ultima aba usada, com o foco nela; quatro vezes a esquerda leva ao Ao vivo
a shell "input keyevent 21 21 21 21; sleep 2"

# Ao vivo: o foco abre nas abas; desce ao 2o grupo (o maior do servidor de teste, 234 canais),
# entra nos canais e rola 80 para baixo e 40 para cima
a shell "input keyevent 20; sleep 0.5; input keyevent 20; sleep 1; input keyevent 22; sleep 1.5"
a shell dumpsys gfxinfo $pacote reset >/dev/null
a shell 'for i in $(seq 1 80); do input keyevent 20; sleep 0.12; done; for i in $(seq 1 40); do input keyevent 19; sleep 0.12; done'
echo "== Ao vivo: rolagem de canais"; resumo

# Ao vivo: troca de grupo a cada passo (recarrega a lista de canais)
a shell "input keyevent 21; sleep 0.5"
a shell dumpsys gfxinfo $pacote reset >/dev/null
a shell 'for i in $(seq 1 30); do input keyevent 20; sleep 0.25; done'
echo "== Ao vivo: troca de grupos"; resumo

# Filmes: volta ao topo, vai para a aba Filmes e percorre 4 fileiras de capas
a shell 'for i in $(seq 1 40); do input keyevent 19; sleep 0.05; done; input keyevent 22; sleep 2; input keyevent 20; sleep 2'
a shell dumpsys gfxinfo $pacote reset >/dev/null
a shell 'for f in 1 2 3 4; do for i in $(seq 1 25); do input keyevent 22; sleep 0.15; done; input keyevent 20; sleep 0.4; done'
echo "== Filmes: 4 fileiras de capas"; resumo
# Filmes: desce e sobe 20 fileiras (os solavancos relatados eram na subida, com as fileiras recarregando)
a shell 'for i in $(seq 1 30); do input keyevent 21; sleep 0.05; done'
a shell dumpsys gfxinfo $pacote reset >/dev/null
a shell 'for i in $(seq 1 20); do input keyevent 20; sleep 0.3; done'
echo "== Filmes: descer 20 fileiras"; resumo
a shell dumpsys gfxinfo $pacote reset >/dev/null
a shell 'for i in $(seq 1 20); do input keyevent 19; sleep 0.3; done'
echo "== Filmes: subir 20 fileiras"; resumo
a shell dumpsys meminfo $pacote | tr -d '\r' | grep -E "TOTAL PSS|TOTAL:" | head -1
