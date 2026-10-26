// Listas virtualizadas (so os itens visiveis existem no DOM) e itens com foco, sem animacao: a TV e de 2019.
// A unica suavidade e o esmaecimento das imagens de fundo (opacidade, que a TV desenha sem recalcular a tela).
import { useRef, useState } from 'preact/hooks';

/** Imagem de fundo que aparece aos poucos depois de carregar (use key={src} para cada imagem nova). */
export function FundoSuave(props) {
  var [carregou, setCarregou] = useState(false);
  return <img class={props.classe + ' suave' + (carregou ? ' visivel' : '')} src={props.src} alt="" onLoad={function () { setCarregou(true); }} />;
}

/** Nota (verde), ano, duracao e genero numa linha, como no layout. */
export function Meta(props) {
  return (
    <div class="meta-destaque">
      {props.nota ? <span class="nota">{'★ ' + props.nota.toFixed(1).replace('.', ',')}</span> : null}
      {props.ano ? <span>{props.ano}</span> : null}
      {props.duracao ? <span>{props.duracao}</span> : null}
      {props.extra ? <span>{props.extra}</span> : null}
      {props.genero ? <span>{props.genero}</span> : null}
    </div>
  );
}


function remEmPx() {
  return parseFloat(window.getComputedStyle(document.documentElement).fontSize) || 16;
}

/** Quantas linhas de alturaRem cabem na altura da tela, tirando reservaRem (topo, margens). Na TV sobrava espaco. */
export function linhasQueCabem(alturaRem, reservaRem) {
  return Math.max(3, Math.floor((window.innerHeight / remEmPx() - reservaRem) / alturaRem));
}

/** Quantos itens de larguraRem cabem na largura da tela, mais um cortado na borda. */
export function itensQueCabem(larguraRem, reservaRem) {
  return Math.max(3, Math.ceil((window.innerWidth / remEmPx() - reservaRem) / larguraRem) + 1);
}

/** Primeiro item visivel, mudando so quando o foco sai da janela (rolagem por pagina, sem pular a cada tecla). */
export function useJanela(indice, visiveis, total) {
  var topo = useRef(0);
  if (indice < topo.current) topo.current = indice;
  if (indice > topo.current + visiveis - 1) topo.current = indice - visiveis + 1;
  topo.current = Math.max(0, Math.min(topo.current, Math.max(0, total - visiveis)));
  return topo.current;
}

/** Lista vertical: render(item, i, focado) desenha cada linha de altura fixa (rem). */
export function ListaVertical(props) {
  var visiveis = props.visiveis;
  var topo = useJanela(props.indice, visiveis, props.itens.length);
  var fim = Math.min(props.itens.length, topo + visiveis + 1);
  var linhas = [];
  for (var i = topo; i < fim; i++) {
    linhas.push(
      <div key={props.chave ? props.chave(props.itens[i], i) : i} class="linha-virtual" style={'top:' + (i - topo) * props.altura + 'rem;height:' + props.altura + 'rem'}>
        {props.render(props.itens[i], i, props.focada && i === props.indice)}
      </div>,
    );
  }
  return <div class={'lista-vertical ' + (props.classe || '')} style={'height:' + visiveis * props.altura + 'rem'}>{linhas}</div>;
}

/** Fileira horizontal de itens de largura fixa (rem). */
export function Fileira(props) {
  var visiveis = props.visiveis || 9;
  var topo = useJanela(props.indice, visiveis - 1, props.itens.length);
  var fim = Math.min(props.itens.length, topo + visiveis);
  var itens = [];
  for (var i = topo; i < fim; i++) {
    itens.push(
      <div key={props.chave ? props.chave(props.itens[i], i) : i} class="item-fileira" style={'left:' + (i - topo) * props.largura + 'rem;width:' + props.largura + 'rem'}>
        {props.render(props.itens[i], i, props.focada && i === props.indice)}
      </div>,
    );
  }
  return <div class="fileira" style={'height:' + props.altura + 'rem'}>{itens}</div>;
}

export function ItemLinha(props) {
  return (
    <div class={'item-linha' + (props.focado ? ' focado' : '') + (props.selecionado ? ' selecionado' : '')}>
      {props.children}
    </div>
  );
}

export function Capa(props) {
  return (
    <div class={'capa' + (props.focado ? ' focado' : '')}>
      {props.src ? <img src={props.src} alt="" decoding="async" /> : <span class="capa-nome">{props.nome}</span>}
      {props.favorito ? <span class="selo">★</span> : null}
    </div>
  );
}

export function Botao(props) {
  return <div class={'botao' + (props.focado ? ' focado' : '') + (props.secundario ? ' secundario' : '')}>{props.children}</div>;
}

export function tempo(ms) {
  var total = Math.max(0, Math.floor(ms / 1000));
  var h = Math.floor(total / 3600);
  var m = Math.floor((total % 3600) / 60);
  var s = total % 60;
  var dois = function (n) { return (n < 10 ? '0' : '') + n; };
  return h > 0 ? h + ':' + dois(m) + ':' + dois(s) : m + ':' + dois(s);
}

export function hora(ms) {
  var d = new Date(ms);
  var dois = function (n) { return (n < 10 ? '0' : '') + n; };
  return dois(d.getHours()) + ':' + dois(d.getMinutes());
}
