// Player ao vivo. Regras do Android (AtividadePlayerAoVivo.kt): uma conexao por vez (fecha a anterior antes de
// abrir), espera de 400 ms no zapping, qualidade pela variante (FHD/HD/SD) e rebaixamento com 2 travadas em 60 s,
// nova tentativa ate 2 vezes, e a conexao e liberada ao sair (Voltar ou Home). Canal em .ts no player da TV (T0).
import { useEffect, useRef, useState } from 'preact/hooks';
import { Botao, hora, ItemLinha, linhasQueCabem, ListaVertical } from '../componentes.jsx';
import { alternarFavorito, ehFavorito, estado, guia, registrar } from '../estado.js';
import { preferencias } from '../dados/preferencias.js';
import { escolherVariante, qualidadeAlvo, varianteAbaixo } from '../dados/regras.js';

var ESPERA_ZAPPING_MS = 400;
var JANELA_TRAVADAS_MS = 60000;
var TRAVADAS_PARA_DESCER = 2;
var TENTATIVAS = 2;
var NOME_VISIVEL_MS = 3500;
var FAIXA_VISIVEL_MS = 8000;

export function PlayerVivo(props) {
  var canais = props.canais;
  var video = useRef(null);
  var [indice, setIndice] = useState(props.posicao);
  var [anterior, setAnterior] = useState(null);
  var [mensagem, setMensagem] = useState(null);
  var [nomeVisivel, setNomeVisivel] = useState(true);
  var [faixa, setFaixa] = useState(false);
  var [botaoFaixa, setBotaoFaixa] = useState(0);
  var [lista, setLista] = useState(false);
  var [indiceLista, setIndiceLista] = useState(props.posicao);
  var [rotuloQualidade, setRotuloQualidade] = useState('');
  var [, tique] = useState(0);
  var controle = useRef({ timer: null, tentativas: 0, travadas: [], variante: null, tocando: false });
  var canal = canais[indice];

  function limpar() {
    var v = video.current;
    if (!v) return;
    v.pause();
    v.removeAttribute('src');
    v.load();
  }

  function abrir(c, imediato) {
    var ctl = controle.current;
    clearTimeout(ctl.timer);
    limpar(); // conexao unica: a anterior fecha antes de qualquer espera
    setMensagem(null);
    ctl.timer = setTimeout(function () {
      var variantes = (estado.indice.variantes[c.grupo] || [c]);
      var qualidades = variantes.map(function (v) { return v.qualidade; });
      if (ctl.variante === null) ctl.variante = escolherVariante(qualidades, qualidadeAlvo(preferencias.qualidade(), estado.velocidadeBps));
      var escolhida = variantes[ctl.variante] || c;
      setRotuloQualidade(escolhida.qualidade || '');
      ctl.tocando = false;
      ctl.inicio = Date.now();
      registrar('Abrindo canal ' + escolhida.id + ' (' + (escolhida.qualidade || '?') + ')');
      video.current.src = estado.cliente.urlAoVivo(escolhida.id, 'ts');
      var p = video.current.play();
      if (p && p.catch) p.catch(function () { /* o evento error trata */ });
    }, imediato ? 0 : ESPERA_ZAPPING_MS);
  }

  function irPara(i, imediato) {
    var n = (i + canais.length) % canais.length;
    if (n !== indice) setAnterior(indice);
    var ctl = controle.current;
    ctl.tentativas = 0;
    ctl.travadas = [];
    ctl.variante = null; // qualidade rebaixada vale ate trocar de canal
    setIndice(n);
    setNomeVisivel(true);
    abrir(canais[n], imediato);
  }

  // Abre ao entrar; fecha ao sair; libera a conexao com o app em segundo plano (Home)
  useEffect(function () {
    abrir(canal, true);
    var aoMudarVisibilidade = function () {
      if (document.hidden) limpar();
      else abrir(canais[indiceAtual.current], true);
    };
    document.addEventListener('visibilitychange', aoMudarVisibilidade);
    return function () {
      clearTimeout(controle.current.timer);
      document.removeEventListener('visibilitychange', aoMudarVisibilidade);
      limpar();
    };
  }, []);
  var indiceAtual = useRef(indice);
  indiceAtual.current = indice;

  useEffect(function () {
    if (!nomeVisivel) return undefined;
    var t = setTimeout(function () { setNomeVisivel(false); }, NOME_VISIVEL_MS);
    return function () { clearTimeout(t); };
  }, [nomeVisivel, indice]);

  useEffect(function () {
    if (!faixa) return undefined;
    var t = setTimeout(function () { setFaixa(false); }, FAIXA_VISIVEL_MS);
    return function () { clearTimeout(t); };
  }, [faixa, botaoFaixa]);

  // Guia do canal atual para o nome e a faixa
  useEffect(function () {
    guia.obter(canal).then(function () { tique(function (n) { return n + 1; }); });
  }, [canal && canal.id]);

  function aoTocar() {
    var ctl = controle.current;
    if (!ctl.tocando) registrar('Troca de canal: ' + (Date.now() - ctl.inicio) + ' ms ate o primeiro quadro');
    ctl.tocando = true;
    ctl.tentativas = 0;
  }

  function aoTravar() {
    var ctl = controle.current;
    if (!ctl.tocando) return;
    var agora = Date.now();
    ctl.travadas = ctl.travadas.filter(function (t) { return agora - t < JANELA_TRAVADAS_MS; }).concat([agora]);
    if (ctl.travadas.length < TRAVADAS_PARA_DESCER) return;
    var variantes = estado.indice.variantes[canal.grupo] || [canal];
    var abaixo = varianteAbaixo(variantes.map(function (v) { return v.qualidade; }), ctl.variante || 0);
    if (abaixo === null) return;
    registrar('Travou ' + ctl.travadas.length + 'x em 60 s: descendo a qualidade');
    ctl.travadas = [];
    ctl.variante = abaixo;
    abrir(canal, true);
  }

  function aoErro() {
    var ctl = controle.current;
    if (ctl.tentativas < TENTATIVAS) {
      ctl.tentativas++;
      registrar('Falha ao abrir o canal, nova tentativa ' + ctl.tentativas);
      abrir(canal, true);
      return;
    }
    setMensagem('Não foi possível abrir este canal agora. Ele pode estar fora do ar, ou a conta está em uso em outro aparelho.');
  }

  props.teclas.atual = function (acao) {
    if (lista) {
      if (acao === 'cima') { setIndiceLista(Math.max(0, indiceLista - 1)); return true; }
      if (acao === 'baixo') { setIndiceLista(Math.min(canais.length - 1, indiceLista + 1)); return true; }
      if (acao === 'ok') { setLista(false); if (indiceLista !== indice) irPara(indiceLista, true); return true; }
      if (acao === 'voltar' || acao === 'esq' || acao === 'dir') { setLista(false); return true; }
      return true;
    }
    if (faixa) {
      var botoes = anterior !== null ? 2 : 1;
      if (acao === 'esq' || acao === 'dir') { setBotaoFaixa((botaoFaixa + 1) % botoes); return true; }
      if (acao === 'ok') {
        if (botaoFaixa === 0) alternarFavorito('canal', canal);
        else { setFaixa(false); irPara(anterior, true); }
        return true;
      }
      if (acao === 'voltar' || acao === 'baixo') { setFaixa(false); return true; }
      return true;
    }
    if (acao === 'cima' || acao === 'canalMais') { irPara(indice - 1, false); return true; }
    if (acao === 'baixo' || acao === 'canalMenos') { irPara(indice + 1, false); return true; }
    if (acao === 'esq' || acao === 'dir') { setIndiceLista(indice); setLista(true); return true; }
    if (acao === 'ok') { setBotaoFaixa(0); setFaixa(true); return true; }
    if (acao === 'voltar') { props.aoFechar(); return true; }
    return true;
  };

  var g = canal ? guia.emCache(canal) : null;
  return (
    <div class="player">
      <video ref={video} class="video" onPlaying={aoTocar} onWaiting={aoTravar} onError={aoErro} />
      {mensagem ? <div class="mensagem-player">{mensagem}</div> : null}
      {nomeVisivel && !faixa && canal ? (
        <div class="nome-canal">
          <div class="titulo-canal">{canal.nomeBase}</div>
          {g && g.agora ? <div class="subtitulo">{g.agora.titulo}</div> : null}
        </div>
      ) : null}
      {faixa && canal ? (
        <div class="faixa">
          <div class="titulo-canal">{canal.nomeBase}{rotuloQualidade ? <span class="qualidade">{rotuloQualidade}</span> : null}</div>
          {g && g.agora ? <div class="programa">{'Agora  ' + hora(g.agora.inicio) + '–' + hora(g.agora.fim) + '  ' + g.agora.titulo}</div> : null}
          {g && g.seguinte ? <div class="subtitulo">{'A seguir  ' + hora(g.seguinte.inicio) + '–' + hora(g.seguinte.fim) + '  ' + g.seguinte.titulo}</div> : null}
          <div class="botoes">
            <Botao focado={botaoFaixa === 0}>{ehFavorito('canal', canal) ? '★ Nos favoritos' : '☆ Favoritar'}</Botao>
            {anterior !== null ? <Botao focado={botaoFaixa === 1}>Canal anterior</Botao> : null}
          </div>
        </div>
      ) : null}
      {lista ? (
        <div class="lista-lateral">
          <ListaVertical itens={canais} indice={indiceLista} visiveis={linhasQueCabem(3.6, 2.6)} altura={3.6} focada={true}
            chave={function (c) { return c.id; }}
            render={function (c, i, focado) { return <ItemLinha focado={focado} selecionado={i === indice}><span class="texto">{c.nomeBase}</span></ItemLinha>; }} />
        </div>
      ) : null}
    </div>
  );
}
