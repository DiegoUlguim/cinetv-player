#!/usr/bin/env bash
# Mede a troca de canal (da tecla ao 1o quadro) no projetor, por formato.
# Uso: bash medir_zapping.sh <canal_id_inicial> [ts|m3u8] [trocas] [endereco]
# ADB do PATH, ou o do SDK do usuario no Windows, ou o que vier na variavel ADB
adb="${ADB:-$(command -v adb || echo "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")}"
canal="$1"; formato="${2:-ts}"; trocas="${3:-10}"; aparelho="${4:-192.168.0.7:5555}"
pacote=br.projetor.cinetv
a() { "$adb" -s "$aparelho" "$@"; }

a shell am force-stop $pacote; sleep 1
a logcat -c
a shell am start -n $pacote/.player.AtividadePlayerAoVivo --ei canal "$canal" --es formato "$formato" >/dev/null
sleep 10
for i in $(seq 1 "$trocas"); do a shell input keyevent 20; sleep 9; done
a shell am force-stop $pacote

echo "== $formato"
a logcat -d -s CineTV:* | tr -d '\r' | grep -E "Troca de canal|Falha" | sed 's/.*CineTV *: //'
a logcat -d -s CineTV:* | tr -d '\r' | grep -oE "Troca de canal: [0-9]+" | grep -oE "[0-9]+" | tail -n "$trocas" | sort -n \
    | awk '{v[NR]=$1} END {if (NR) printf "trocas medidas: %d | mediana: %d ms | maximo: %d ms\n", NR, v[int((NR+1)/2)], v[NR]}'
