// Inicio, Filmes, Series e Minha lista, como a Netflix na TV (video de referencia): a fileira em foco fica logo
// abaixo do menu; o item em foco cresce para um card deitado, fica sempre na primeira posicao e mostra embaixo
// as informacoes; parado nele, toca o comeco do filme (ou do primeiro episodio da serie) com som.
// A fileira desliza e o card cresce com transicao de CSS (desligavel em Ajustes, para a TV de 2019).
import { useEffect, useRef, useState } from 'preact/hooks';
import { FundoSuave, Meta } from '../componentes.jsx';
import { alternarFavorito, ehFavorito, estado, favoritosDoTipo, progressoDe, recentes, registrar } from '../estado.js';
import { preferencias } from '../dados/preferencias.js';
import { carregarFilme, carregarSerie, duracaoLegivel } from './Detalhes.jsx';

// Medidas em rem (1 rem = 16 px numa tela de 1920), tiradas do video da Netflix: o card em foco ocupa perto de
// metade da largura e da altura da tela; as capas ao lado tem a mesma altura (as classes em estilo.css seguem estas)
var ALTURA = 30; // altura dos cards (capa em pe 2:3; o card em foco, deitado 16:9, tem a mesma altura)
var CAPA = ALTURA * 2 / 3;
var LARGO = ALTURA * 16 / 9;
var ESPACO = 1;
var MARGEM = 3.75; // da borda esquerda da tela
var TITULO = 3.2; // titulo da fileira + espaco ate os cards
var INFO = 9; // informacoes embaixo do card em foco
var TOPO = 1.2; // fileira em foco, logo abaixo do menu
var ENTRE_FILEIRAS = 1.6;
var DETALHE_ESPERA_MS = 700; // sinopse e imagem de fundo do item em foco
var PREVIA_ESPERA_MS = 2000; // video do item em foco

function entrada(tipo, item) { return item ? { tipo: tipo, item: item } : null; }

function continuar(tipo) {
  var ix = estado.indice;
  if (tipo === 'filme') {
    return recentes('filme', 20).map(function (p) { return { em: p.atualizadoEm, e: entrada('filme', ix.filmePorId[p.idConteudo]) }; });
  }
  // Progresso e por episodio; a fileira mostra cada serie uma vez
  var vistos = {};
  return recentes('episodio', 60).filter(function (p) { if (!p.serieId || vistos[p.serieId]) return false; vistos[p.serieId] = true; return true; })
    .slice(0, 20).map(function (p) { return { em: p.atualizadoEm, e: entrada('serie', ix.seriePorId[p.serieId]) }; });
}

function minhaLista(tipo) {
  var porId = tipo === 'filme' ? estado.indice.filmePorId : estado.indice.seriePorId;
  return favoritosDoTipo(tipo).map(function (f) { return entrada(tipo, porId[f.idConteudo]); }).filter(Boolean);
}

function categorias(tipo) {
  var ix = estado.indice;
  var lista = tipo === 'filme' ? ix.categoriasFilmes : ix.categoriasSeries;
  var por = tipo === 'filme' ? ix.filmesPorCategoria : ix.seriesPorCategoria;
  return lista.map(function (c) {
    return { chave: tipo + c.id, titulo: c.nome, itens: (por[c.id] || []).map(function (i) { return entrada(tipo, i); }) };
  });
}

function fileiras(tipo) {
  if (!estado.indice) return [];
  var lista = [];
  var tipos = tipo === 'filme' || tipo === 'serie' ? [tipo] : ['filme', 'serie'];
  if (tipo !== 'lista') {
    var c = [].concat.apply([], tipos.map(continuar)).sort(function (a, b) { return b.em - a.em; })
      .map(function (x) { return x.e; }).filter(Boolean);
    if (c.length) lista.push({ chave: 'continuar', titulo: 'Continuar assistindo', itens: c, progresso: true });
  }
  if (tipo === 'lista') {
    tipos.forEach(function (t) {
      var m = minhaLista(t);
      if (m.length) lista.push({ chave: 'minha' + t, titulo: t === 'filme' ? 'Filmes na minha lista' : 'Séries na minha lista', itens: m });
    });
    return lista;
  }
  var minha = [].concat.apply([], tipos.map(minhaLista));
  if (minha.length) lista.push({ chave: 'minha', titulo: 'Minha lista', itens: minha });
  if (tipos.length === 1) return lista.concat(categorias(tipo));
  // Inicio: categorias de filmes e de series intercaladas
  var f = categorias('filme');
  var s = categorias('serie');
  for (var i = 0; i < Math.max(f.length, s.length); i++) {
    if (f[i]) lista.push(f[i]);
    if (s[i]) lista.push(s[i]);
  }
  return lista;
}

function progressoDaEntrada(e) {
  return e.tipo === 'filme' ? progressoDe('filme:' + e.item.id)
    : recentes('episodio', 200).filter(function (r) { return r.serieId === e.item.id; })[0] || null;
}

function fracao(p) { return p && p.duracaoMs > 0 ? Math.min(1, p.posicaoMs / p.duracaoMs) : 0; }

/** Sinopse, imagem de fundo e duracao do item em foco, pedidas so com o foco parado (cache em Detalhes). */
function useDetalhe(e, ativo) {
  var [detalhe, setDetalhe] = useState(null);
  var chave = e ? e.tipo + e.item.id : '';
  useEffect(function () {
    setDetalhe(null);
    if (!e || !ativo || !estado.cliente) return undefined;
    var vivo = true;
    var t = setTimeout(function () {
      (e.tipo === 'filme' ? carregarFilme : carregarSerie)(e.item.id).then(function (d) { if (vivo) setDetalhe(d); }, function () { /* fica a capa */ });
    }, DETALHE_ESPERA_MS);
    return function () { vivo = false; clearTimeout(t); };
  }, [chave, ativo]);
  return detalhe;
}

/**
 * Video do item em foco (comeco do filme ou do primeiro episodio), fora das camadas que deslizam: no webOS o video
 * vai numa camada de hardware e fica preto dentro de elemento com transform, recorte ou cantos arredondados.
 * Conexao unica: fecha a cada movimento, ao sair da tela e com o app em segundo plano.
 */
function PreviaCard(props) {
  var video = useRef(null);
  var [tocando, setTocando] = useState(false);
  var e = props.entrada;
  var chave = e ? e.tipo + e.item.id : '';
  useEffect(function () {
    setTocando(false);
    var v = video.current;
    if (!e || !props.ativa || !estado.cliente) return undefined;
    var vivo = true;
    var t = setTimeout(function () {
      var url = e.tipo === 'filme'
        ? Promise.resolve(estado.cliente.urlFilme(e.item.id, e.item.extensao))
        : carregarSerie(e.item.id).then(function (d) {
          var ep = d && d.temporadas.length ? d.episodios[d.temporadas[0]][0] : null;
          return ep ? estado.cliente.urlEpisodio(ep.id, ep.extensao) : null;
        });
      url.then(function (u) {
        if (!vivo || !u) return;
        v.src = u;
        var p = v.play();
        if (p && p.catch) p.catch(function () { /* sem previa: fica a imagem */ });
      }, function () { /* sem previa */ });
    }, PREVIA_ESPERA_MS);
    var aoMudar = function () { if (document.hidden) fechar(); };
    function fechar() { v.pause(); v.removeAttribute('src'); v.load(); setTocando(false); }
    document.addEventListener('visibilitychange', aoMudar);
    return function () {
      vivo = false;
      clearTimeout(t);
      document.removeEventListener('visibilitychange', aoMudar);
      fechar();
    };
  }, [chave, props.ativa]);
  return (
    <video ref={video} class={'video-card' + (tocando ? ' visivel' : '')} style={props.estilo}
      onPlaying={function () { setTocando(true); }} onError={function () { setTocando(false); registrar('Previa do card indisponivel'); }} />
  );
}

export function Capas(props) {
  var lista = fileiras(props.tipo);
  var [linha, setLinha] = useState(0);
  var [, tique] = useState(0);
  var colunas = useRef({});
  var comPrevia = preferencias.destaque();
  var l = Math.min(linha, Math.max(0, lista.length - 1));
  var atual = lista[l];
  var coluna = atual ? Math.min(colunas.current[atual.chave] || 0, atual.itens.length - 1) : 0;
  var emFoco = atual ? atual.itens[coluna] : null;
  // Movimento recente: o detalhe e a previa so comecam com o foco parado
  var detalhe = useDetalhe(emFoco, props.focado);

  props.teclas.atual = function (acao) {
    if (!atual) return acao === 'cima' ? 'subir' : false;
    if (acao === 'cima') { if (l === 0) return 'subir'; setLinha(l - 1); return true; }
    if (acao === 'baixo') { setLinha(Math.min(lista.length - 1, l + 1)); return true; }
    if (acao === 'esq' || acao === 'dir') {
      colunas.current[atual.chave] = Math.max(0, Math.min(atual.itens.length - 1, coluna + (acao === 'dir' ? 1 : -1)));
      tique(function (n) { return n + 1; });
      return true;
    }
    if (acao === 'ok' && emFoco) { props.aoAbrir(emFoco.tipo, emFoco.item); return true; }
    if (acao === 'segurar' && emFoco) { alternarFavorito(emFoco.tipo, emFoco.item); return true; }
    return false;
  };

  if (!estado.indice) return <div class="vazio">{estado.sincronizando ? 'Carregando as listas…' : 'Sem listas ainda. Configure o login em Ajustes.'}</div>;
  if (!lista.length) return <div class="vazio">{props.tipo === 'lista' ? 'Sua lista está vazia. Segure OK numa capa para adicionar.' : 'Nada aqui ainda.'}</div>;

  var rem = parseFloat(window.getComputedStyle(document.documentElement).fontSize) || 16;
  var larguraTela = window.innerWidth / rem;
  var alturaFocada = TITULO + ALTURA + INFO;
  var alturaOutra = TITULO + ALTURA;

  // Fileiras: a anterior fica acima (escondida sob o menu), a em foco no topo, as seguintes embaixo
  var desenhadas = [];
  for (var i = Math.max(0, l - 1); i < Math.min(lista.length, l + 3); i++) {
    var y = i < l ? TOPO - alturaOutra - ENTRE_FILEIRAS
      : i === l ? TOPO
        : TOPO + alturaFocada + ENTRE_FILEIRAS + (i - l - 1) * (alturaOutra + ENTRE_FILEIRAS);
    desenhadas.push(
      <Fileira key={lista[i].chave} fileira={lista[i]} focada={i === l} ativa={props.focado && i === l} y={y}
        coluna={i === l ? coluna : Math.min(colunas.current[lista[i].chave] || 0, lista[i].itens.length - 1)}
        larguraTela={larguraTela} detalhe={i === l ? detalhe : null} />,
    );
  }

  var topoCard = TOPO + TITULO;
  return (
    <div class="capas">
      {desenhadas}
      {comPrevia ? <PreviaCard entrada={emFoco} ativa={props.focado} estilo={'left:' + MARGEM + 'rem;top:' + topoCard + 'rem;width:' + LARGO + 'rem;height:' + ALTURA + 'rem'} /> : null}
    </div>
  );
}

function Fileira(props) {
  var f = props.fileira;
  var c = props.coluna;
  var itens = [];
  // So os cards a partir de um antes do foco (que sai pela esquerda) ate a borda direita da tela
  var cabem = Math.ceil(props.larguraTela / (CAPA + ESPACO)) + 1;
  for (var i = Math.max(0, c - 1); i < Math.min(f.itens.length, c + cabem); i++) {
    var focado = props.focada && i === c;
    var largura = focado ? LARGO : CAPA;
    // O anterior sai inteiro pela esquerda (passa da margem), como na Netflix
    var x = i < c ? -(c - i) * (CAPA + ESPACO) - MARGEM
      : i === c ? 0
        : (props.focada ? LARGO : CAPA) + ESPACO + (i - c - 1) * (CAPA + ESPACO);
    itens.push(<Card key={f.itens[i].tipo + f.itens[i].item.id} e={f.itens[i]} x={x} largura={largura} focado={focado}
      ativo={props.ativa && i === c} progresso={f.progresso} detalhe={focado ? props.detalhe : null} />);
  }
  var e = f.itens[c];
  return (
    <div class="fileira-nf" style={'transform:translateY(' + props.y + 'rem)'}>
      <div class="titulo-fileira">{f.titulo}</div>
      <div class="trilho-fileira">{itens}</div>
      {props.focada && e ? <InfoCard e={e} detalhe={props.detalhe} progresso={f.progresso} /> : null}
    </div>
  );
}

function Card(props) {
  var e = props.e;
  var d = props.detalhe || {};
  var fundo = props.focado ? (d.fundo || d.capaGrande || null) : null;
  var p = props.progresso ? progressoDaEntrada(e) : null;
  return (
    <div class={'card-nf' + (props.focado ? ' focado' : '') + (props.ativo ? ' ativo' : '')}
      style={'transform:translateX(' + props.x + 'rem);width:' + props.largura + 'rem'}>
      <div class="imagem-card">
        {e.item.capa ? <img src={e.item.capa} alt="" decoding="async" /> : null}
        {fundo ? <FundoSuave key={fundo} src={fundo} classe="fundo-card" /> : null}
        {!e.item.capa || props.focado ? <span class="nome-card">{e.item.nome}</span> : null}
        {ehFavorito(e.tipo, e.item) ? <span class="selo">★</span> : null}
      </div>
      {p ? <div class="barra-card"><div class="preenchido" style={'width:' + Math.round(fracao(p) * 100) + '%'} /></div> : null}
    </div>
  );
}

/** Embaixo do card em foco: o que e, ano, nota, duracao ou temporadas, e a sinopse. */
function InfoCard(props) {
  var e = props.e;
  var d = props.detalhe || {};
  var p = props.progresso ? progressoDaEntrada(e) : null;
  var restante = p && p.duracaoMs > 0 ? Math.max(1, Math.round((p.duracaoMs - p.posicaoMs) / 60000)) + ' min restante(s)' : null;
  var extra = e.tipo === 'filme' ? duracaoLegivel(d.duracaoSegundos)
    : d.temporadas && d.temporadas.length ? d.temporadas.length + (d.temporadas.length > 1 ? ' temporadas' : ' temporada') : null;
  return (
    <div class="info-card">
      <Meta nota={e.item.nota} ano={e.item.ano} duracao={extra} genero={d.genero} extra={e.tipo === 'filme' ? 'Filme' : 'Série'} />
      {restante ? <div class="restante-card">{restante}</div> : null}
      {d.sinopse ? <div class="sinopse-card">{d.sinopse}</div> : null}
    </div>
  );
}
