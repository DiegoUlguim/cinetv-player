// Player de filme e episodio. Regras do Android (AtividadePlayerSobDemanda.kt): retoma do ponto salvo, grava o
// progresso a cada 10 s e ao sair (a partir de 30 s; acima de 95% conta como terminado), esquerda/direita
// voltam e avancam 10 s, OK pausa; o fim do episodio avisa a serie para oferecer o proximo.
import { useEffect, useRef, useState } from 'preact/hooks';
import { tempo } from '../componentes.jsx';
import { apagarProgresso, estado, gravarProgresso, registrar } from '../estado.js';

var PASSO_S = 10;
var GRAVAR_A_CADA_MS = 10000;
var BARRA_VISIVEL_MS = 4000;

export function PlayerVod(props) {
  var item = props.item; // { tipo: 'filme'|'episodio', id, titulo, capa, extensao, serieId }
  var video = useRef(null);
  var [posicao, setPosicao] = useState(0);
  var [duracao, setDuracao] = useState(0);
  var [pausado, setPausado] = useState(false);
  var [barra, setBarra] = useState(true);
  var [mensagem, setMensagem] = useState(null);
  var terminou = useRef(false);

  function salvar() {
    var v = video.current;
    if (!v || terminou.current || !v.duration || !isFinite(v.duration)) return;
    gravarProgresso({
      chave: item.tipo + ':' + item.id, tipo: item.tipo, idConteudo: item.id, titulo: item.titulo, capa: item.capa,
      extensao: item.extensao, posicaoMs: Math.round(v.currentTime * 1000), duracaoMs: Math.round(v.duration * 1000),
      serieId: item.serieId || null,
    });
  }

  function fechar(acabou) {
    salvar();
    var v = video.current;
    if (v) { v.pause(); v.removeAttribute('src'); v.load(); }
    props.aoFechar(!!acabou);
  }

  useEffect(function () {
    var v = video.current;
    var url = item.tipo === 'filme' ? estado.cliente.urlFilme(item.id, item.extensao) : estado.cliente.urlEpisodio(item.id, item.extensao);
    registrar('Abrindo ' + item.tipo + ' ' + item.id + (props.retomarEm ? ' em ' + Math.round(props.retomarEm / 1000) + ' s' : ''));
    v.src = url;
    var p = v.play();
    if (p && p.catch) p.catch(function () { /* o evento error trata */ });
    var gravador = setInterval(salvar, GRAVAR_A_CADA_MS);
    var aoMudarVisibilidade = function () { if (document.hidden) { salvar(); v.pause(); setPausado(true); } };
    document.addEventListener('visibilitychange', aoMudarVisibilidade);
    return function () {
      clearInterval(gravador);
      document.removeEventListener('visibilitychange', aoMudarVisibilidade);
      v.pause();
      v.removeAttribute('src');
      v.load();
    };
  }, []);

  // Conta a partir do primeiro quadro (posicao > 0), e nao a cada timeupdate, que reiniciava o tempo sem parar
  useEffect(function () {
    if (!barra || pausado) return undefined;
    var t = setTimeout(function () { setBarra(false); }, BARRA_VISIVEL_MS);
    return function () { clearTimeout(t); };
  }, [barra, pausado, posicao > 0]);

  function pular(segundos) {
    var v = video.current;
    if (!v || !v.duration) return;
    v.currentTime = Math.max(0, Math.min(v.duration - 1, v.currentTime + segundos));
    setBarra(true);
  }

  function alternarPausa() {
    var v = video.current;
    if (!v) return;
    if (v.paused) { v.play(); setPausado(false); } else { v.pause(); setPausado(true); salvar(); }
    setBarra(true);
  }

  props.teclas.atual = function (acao) {
    if (acao === 'esq' || acao === 'recuar') { pular(-PASSO_S); return true; }
    if (acao === 'dir' || acao === 'avancar') { pular(PASSO_S); return true; }
    if (acao === 'ok' || acao === 'playPause') { alternarPausa(); return true; }
    if (acao === 'play') { if (video.current.paused) alternarPausa(); return true; }
    if (acao === 'pause') { if (!video.current.paused) alternarPausa(); return true; }
    if (acao === 'cima' || acao === 'baixo') { setBarra(true); return true; }
    if (acao === 'voltar' || acao === 'parar') { fechar(false); return true; }
    return true;
  };

  var fracao = duracao > 0 ? Math.min(1, posicao / duracao) : 0;
  return (
    <div class="player">
      <video
        ref={video} class="video"
        onLoadedMetadata={function (e) {
          setDuracao(e.target.duration * 1000);
          if (props.retomarEm > 0) e.target.currentTime = props.retomarEm / 1000;
        }}
        onTimeUpdate={function (e) { setPosicao(e.target.currentTime * 1000); }}
        onEnded={function () { terminou.current = true; apagarProgresso(item.tipo + ':' + item.id); fechar(true); }}
        onError={function () { setMensagem('Não foi possível abrir este título agora.'); }}
      />
      {mensagem ? <div class="mensagem-player">{mensagem}</div> : null}
      {barra || pausado ? (
        <div class="barra-vod">
          <div class="titulo-canal">{(pausado ? '❚❚  ' : '') + item.titulo}</div>
          <div class="tempo-vod">
            <span>{tempo(posicao)}</span>
            <div class="trilho"><div class="preenchido" style={'width:' + fracao * 100 + '%'} /></div>
            <span>{tempo(duracao)}</span>
          </div>
          <div class="dica">OK pausa  ·  ◀ ▶ volta e avança 10 s  ·  Voltar sai</div>
        </div>
      ) : null}
    </div>
  );
}
