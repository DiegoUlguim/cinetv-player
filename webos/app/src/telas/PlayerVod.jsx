// Player de filme e episodio, como o da Netflix na TV (video de referencia). Regras do Android
// (AtividadePlayerSobDemanda.kt): retoma do ponto salvo, grava o progresso a cada 10 s e ao sair (a partir de 30 s;
// acima de 95% conta como terminado); o fim do episodio avisa a serie para oferecer o proximo.
// Duas zonas de foco: a barra (comeca nela, realcada) e os icones do topo (voltar, desde o inicio, proximo).
// Na barra, esquerda e direita so movem um marcador de destino; OK pula para ele e Voltar cancela.
import { useEffect, useRef, useState } from 'preact/hooks';
import { tempo } from '../componentes.jsx';
import { apagarProgresso, estado, gravarProgresso, registrar } from '../estado.js';

var PASSO_S = 10;
var GRAVAR_A_CADA_MS = 10000;
var CONTROLES_VISIVEIS_MS = 4000;
// Proximo episodio como na Netflix: perto do fim aparece "Proximo episodio"; o botao se preenche em CONTAGEM_MS
// e entao passa sozinho (o Xtream nao informa onde comecam os creditos: usa os ultimos AVISO_FIM_MS)
var AVISO_FIM_MS = 30000;
var CONTAGEM_MS = 10000;

// Icones de traco (viewBox 24)
var ICONES = {
  voltar: 'M15 5l-7 7 7 7',
  inicio: 'M4 12a8 8 0 1 0 3-6.2M4 4v4h4',
  proximo: 'M5 4l10 8-10 8zM17 4v16',
  episodios: 'M3 5h18M3 12h18M3 19h18',
};

export function PlayerVod(props) {
  var item = props.item; // { tipo: 'filme'|'episodio', id, titulo, subtitulo, capa, extensao, serieId }
  var video = useRef(null);
  var [posicao, setPosicao] = useState(0);
  var [duracao, setDuracao] = useState(0);
  var [pausado, setPausado] = useState(false);
  var [visivel, setVisivel] = useState(true);
  var [zona, setZona] = useState('barra'); // 'barra' | 'topo'
  var [icone, setIcone] = useState(0);
  var [alvo, setAlvo] = useState(null); // ms de destino enquanto escolhe com as setas; null = sem marcador
  var [mensagem, setMensagem] = useState(null);
  var [dispensado, setDispensado] = useState(false); // Voltar escondeu o aviso de proximo episodio
  var terminou = useRef(false);
  var avisoFim = !!props.aoProximo && !dispensado && duracao > 0 && duracao - posicao <= AVISO_FIM_MS;

  var icones = [{ id: 'voltar', rotulo: 'Voltar' }, { id: 'inicio', rotulo: 'Desde o início' }];
  if (item.tipo === 'episodio') icones.push({ id: 'episodios', rotulo: 'Episódios' });
  if (props.aoProximo) icones.push({ id: 'proximo', rotulo: 'Próximo episódio' });

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

  // Some 4 s depois do primeiro quadro ou da ultima tecla; fica com o video pausado ou com o marcador na barra
  useEffect(function () {
    if (!visivel || pausado || alvo !== null) return undefined;
    var t = setTimeout(function () { setVisivel(false); setZona('barra'); }, CONTROLES_VISIVEIS_MS);
    return function () { clearTimeout(t); };
  }, [visivel, pausado, alvo, zona, icone, posicao > 0]);

  // A contagem do "Proximo episodio" comeca quando o aviso aparece
  useEffect(function () {
    if (!avisoFim) return undefined;
    var t = setTimeout(function () { irParaProximo(); }, CONTAGEM_MS);
    return function () { clearTimeout(t); };
  }, [avisoFim]);

  function irParaProximo() { salvar(); props.aoProximo(); }

  function mostrar() { if (!visivel) setVisivel(true); }

  function moverAlvo(segundos) {
    var base = alvo !== null ? alvo : posicao;
    setAlvo(Math.max(0, Math.min(Math.max(0, duracao - 1000), base + segundos * 1000)));
    setVisivel(true);
  }

  function confirmarAlvo() {
    var v = video.current;
    if (v && alvo !== null) v.currentTime = alvo / 1000;
    setAlvo(null);
  }

  function alternarPausa() {
    var v = video.current;
    if (!v) return;
    if (v.paused) { v.play(); setPausado(false); } else { v.pause(); setPausado(true); salvar(); }
    setVisivel(true);
  }

  function acionar(id) {
    var v = video.current;
    if (id === 'voltar') fechar(false);
    else if (id === 'inicio' && v) { v.currentTime = 0; if (v.paused) alternarPausa(); }
    else if (id === 'episodios') fechar(false); // volta a serie, com a lista de episodios
    else if (id === 'proximo') { salvar(); props.aoProximo(); }
  }

  props.teclas.atual = function (acao) {
    // Teclas de midia do controle valem em qualquer zona
    if (acao === 'playPause') { alternarPausa(); return true; }
    if (acao === 'play') { if (video.current.paused) alternarPausa(); return true; }
    if (acao === 'pause') { if (!video.current.paused) alternarPausa(); return true; }
    if (acao === 'recuar') { moverAlvo(-PASSO_S); return true; }
    if (acao === 'avancar') { moverAlvo(PASSO_S); return true; }
    if (acao === 'parar') { fechar(false); return true; }

    if (avisoFim && !(visivel && zona === 'topo')) {
      if (acao === 'ok') { irParaProximo(); return true; }
      if (acao === 'voltar') { setDispensado(true); return true; }
    }

    if (!visivel) {
      // Controles escondidos: a primeira tecla so os mostra (as setas laterais ja comecam a escolher o destino)
      if (acao === 'esq' || acao === 'dir') { setZona('barra'); moverAlvo(acao === 'dir' ? PASSO_S : -PASSO_S); return true; }
      if (acao === 'voltar') { fechar(false); return true; }
      setVisivel(true);
      return true;
    }
    if (zona === 'topo') {
      if (acao === 'esq' || acao === 'dir') { setIcone(Math.max(0, Math.min(icones.length - 1, icone + (acao === 'dir' ? 1 : -1)))); return true; }
      if (acao === 'ok') { acionar(icones[icone].id); return true; }
      if (acao === 'baixo') { setZona('barra'); return true; }
      if (acao === 'voltar') { setZona('barra'); return true; }
      return true;
    }
    // barra
    if (acao === 'esq' || acao === 'dir') { moverAlvo(acao === 'dir' ? PASSO_S : -PASSO_S); return true; }
    if (acao === 'ok') { if (alvo !== null) confirmarAlvo(); else alternarPausa(); mostrar(); return true; }
    if (acao === 'voltar') { if (alvo !== null) { setAlvo(null); return true; } fechar(false); return true; }
    if (acao === 'cima') { setAlvo(null); setZona('topo'); setIcone(0); return true; }
    return true;
  };

  var fracao = duracao > 0 ? Math.min(1, posicao / duracao) : 0;
  var fracaoAlvo = alvo !== null && duracao > 0 ? Math.min(1, alvo / duracao) : null;
  var emTopo = visivel && zona === 'topo';
  return (
    <div class="player">
      <video
        ref={video} class="video"
        onLoadedMetadata={function (e) {
          setDuracao(e.target.duration * 1000);
          if (props.retomarEm > 0) e.target.currentTime = props.retomarEm / 1000;
        }}
        onTimeUpdate={function (e) { setPosicao(e.target.currentTime * 1000); }}
        onEnded={function () {
          terminou.current = true;
          apagarProgresso(item.tipo + ':' + item.id);
          if (props.aoProximo && !dispensado) props.aoProximo(); else fechar(true);
        }}
        onError={function () { setMensagem('Não foi possível abrir este título agora.'); }}
      />
      {mensagem ? <div class="mensagem-player">{mensagem}</div> : null}
      {avisoFim ? (
        <div class="aviso-fim">
          <div class="botao-fim proximo">
            <div class="enchimento" style={'animation-duration:' + CONTAGEM_MS + 'ms'} />
            <span class="conteudo-fim"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4l13 8-13 8z" fill="currentColor" /></svg>Próximo episódio</span>
          </div>
        </div>
      ) : null}
      {visivel || pausado ? (
        <div>
          <div class="topo-vod">
            <div class="icones-vod">
              {icones.map(function (c, i) {
                var focado = emTopo && i === icone;
                return (
                  <div key={c.id} class={'icone-vod' + (focado ? ' focado' : '')}>
                    <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2"><path d={ICONES[c.id]} /></svg>
                    {focado ? <span class="rotulo-icone">{c.rotulo}</span> : null}
                  </div>
                );
              })}
            </div>
            <div class="titulos-vod">
              <div class="titulo-vod">{item.titulo}</div>
              {item.subtitulo ? <div class="subtitulo-vod">{item.subtitulo}</div> : null}
            </div>
          </div>
          <div class={'barra-vod' + (visivel && zona === 'barra' ? ' focada' : '')}>
            <div class="linha-vod">
              <div class="botao-pausa">
                {pausado
                  ? <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4l13 8-13 8z" fill="currentColor" /></svg>
                  : <svg viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><rect x="6" y="4" width="4" height="16" /><rect x="14" y="4" width="4" height="16" /></svg>}
              </div>
              <span class="tempo-atual">{tempo(posicao)}</span>
              <div class="trilho">
                <div class="preenchido" style={'width:' + fracao * 100 + '%'} />
                <div class="ponto" style={'left:' + fracao * 100 + '%'} />
                {fracaoAlvo !== null ? (
                  <div class="alvo" style={'left:' + fracaoAlvo * 100 + '%'}>
                    <span class="hora-alvo">{tempo(alvo)}</span>
                  </div>
                ) : null}
              </div>
              <span class="tempo-total">{tempo(duracao)}</span>
            </div>
            {fracaoAlvo !== null ? <div class="dica-vod">OK para ir até aqui  ·  Voltar para continuar de onde estava</div> : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}
