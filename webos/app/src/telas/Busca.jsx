// Busca em canais, filmes e series: cada palavra e prefixo, sem acento. Campo nativo (teclado da TV).
// Regras do Android: cursor no fim ao voltar ao campo; cada busca nova recomeca do primeiro resultado.
import { useEffect, useRef, useState } from 'preact/hooks';
import { Capa, Fileira, itensQueCabem, ItemLinha } from '../componentes.jsx';
import { estado, nomeNormalizado } from '../estado.js';
import { bateBusca, palavrasDaBusca } from '../dados/regras.js';

var MAXIMO_POR_TIPO = 60;

function buscar(texto) {
  var palavras = palavrasDaBusca(texto);
  if (!estado.indice || texto.trim().length < 2 || !palavras.length) return null;
  var filtrar = function (lista) {
    var achados = [];
    for (var i = 0; i < lista.length && achados.length < MAXIMO_POR_TIPO; i++) {
      if (bateBusca(nomeNormalizado(lista[i]), palavras)) achados.push(lista[i]);
    }
    return achados;
  };
  var ix = estado.indice;
  var linhas = [
    { chave: 'canais', titulo: 'Canais', tipo: 'canal', itens: filtrar(ix.representantes) },
    { chave: 'filmes', titulo: 'Filmes', tipo: 'filme', itens: filtrar(ix.catalogo.filmes) },
    { chave: 'series', titulo: 'Séries', tipo: 'serie', itens: filtrar(ix.catalogo.series) },
  ].filter(function (l) { return l.itens.length; });
  return { texto: texto, linhas: linhas };
}

export function Busca(props) {
  var [texto, setTexto] = useState('');
  var [resultados, setResultados] = useState(null);
  var [area, setArea] = useState('campo');
  var [linha, setLinha] = useState(0);
  var [colunas, setColunas] = useState({});
  var campo = useRef(null);

  useEffect(function () {
    var t = setTimeout(function () {
      var r = buscar(texto);
      // Busca nova recomeca do inicio: a chave e a consulta que gerou os resultados
      setResultados(r);
      setLinha(0);
      setColunas({});
    }, 300);
    return function () { clearTimeout(t); };
  }, [texto]);

  // O campo nativo so tem foco com a busca na tela e a area do campo ativa; cursor sempre no fim
  useEffect(function () {
    var el = campo.current;
    if (!el) return;
    if (props.focado && area === 'campo') {
      el.focus();
      try { el.setSelectionRange(el.value.length, el.value.length); } catch (e) { /* sem selecao */ }
    } else if (document.activeElement === el) {
      el.blur();
    }
  }, [props.focado, area]);

  var linhas = resultados ? resultados.linhas : [];
  var atual = linhas[linha];
  var coluna = atual ? colunas[atual.chave] || 0 : 0;

  props.teclas.atual = function (acao) {
    if (area === 'campo') {
      if (acao === 'cima') return 'subir';
      if (acao === 'baixo' && linhas.length) { setArea('resultados'); setLinha(0); return true; }
      return acao === 'baixo';
    }
    if (acao === 'cima') { if (linha === 0) setArea('campo'); else setLinha(linha - 1); return true; }
    if (acao === 'baixo') { setLinha(Math.min(linhas.length - 1, linha + 1)); return true; }
    if (acao === 'esq' && coluna === 0) return false; // borda: vai ao menu
    if (acao === 'esq' || acao === 'dir') {
      var nova = Math.max(0, Math.min(atual.itens.length - 1, coluna + (acao === 'dir' ? 1 : -1)));
      var c = Object.assign({}, colunas);
      c[atual.chave] = nova;
      setColunas(c);
      return true;
    }
    if (acao === 'ok' && atual) { props.aoAbrir(atual.tipo, atual.itens[coluna]); return true; }
    return false;
  };

  return (
    <div class="busca">
      <input
        ref={campo} class={'campo' + (props.focado && area === 'campo' ? ' focado' : '')} type="text" value={texto}
        placeholder="Buscar canal, filme ou série (OK abre o teclado)"
        onInput={function (e) { setTexto(e.target.value); }}
      />
      {resultados && !linhas.length ? <div class="vazio-linha">Nada encontrado para "{resultados.texto}"</div> : null}
      {linhas.map(function (l, i) {
        var focada = props.focado && area === 'resultados' && i === linha;
        var col = colunas[l.chave] || 0;
        return (
          <div key={resultados.texto + ':' + l.chave} class="bloco-resultado">
            <div class="titulo-fileira">{l.titulo}</div>
            {l.tipo === 'canal'
              ? <Fileira itens={l.itens} indice={col} focada={focada} largura={20} altura={3.6} visiveis={itensQueCabem(20, 6)}
                  render={function (c, j, focado) { return <ItemLinha focado={focado}><span class="texto">{c.nomeBase}</span></ItemLinha>; }} />
              : <Fileira itens={l.itens} indice={col} focada={focada} largura={9} altura={12.5} visiveis={itensQueCabem(9, 6)}
                  render={function (item, j, focado) { return <Capa src={item.capa} nome={item.nome} focado={focado} />; }} />}
          </div>
        );
      })}
    </div>
  );
}
