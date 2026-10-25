// Filmes e Series: "Continuar assistindo" e "Minha lista" no topo, uma fileira por categoria.
// Cada fileira lembra a ultima capa (focusRestorer do Android); altura fixa, para a lista nao pular na subida.
import { useRef, useState } from 'preact/hooks';
import { Capa, Fileira, itensQueCabem, useJanela } from '../componentes.jsx';
import { alternarFavorito, ehFavorito, estado, favoritosDoTipo, recentes } from '../estado.js';

var ALTURA_FILEIRA = 17; // rem: titulo + capa
var LARGURA_CAPA = 9; // rem: capa 8 + espaco

function fileiras(tipo) {
  var ix = estado.indice;
  if (!ix) return [];
  var porId = tipo === 'filme' ? ix.filmePorId : ix.seriePorId;
  var lista = [];
  var continuar;
  if (tipo === 'filme') {
    continuar = recentes('filme', 20).map(function (p) { return porId[p.idConteudo]; }).filter(Boolean);
  } else {
    // Progresso e por episodio; a fileira mostra cada serie uma vez
    var vistos = {};
    continuar = recentes('episodio', 60).map(function (p) { return p.serieId; })
      .filter(function (id) { if (!id || vistos[id]) return false; vistos[id] = true; return true; })
      .slice(0, 20).map(function (id) { return porId[id]; }).filter(Boolean);
  }
  if (continuar.length) lista.push({ chave: 'continuar', titulo: 'Continuar assistindo', itens: continuar });
  var minha = favoritosDoTipo(tipo).map(function (f) { return porId[f.idConteudo]; }).filter(Boolean);
  if (minha.length) lista.push({ chave: 'minha', titulo: 'Minha lista', itens: minha });
  var categorias = tipo === 'filme' ? ix.categoriasFilmes : ix.categoriasSeries;
  var porCategoria = tipo === 'filme' ? ix.filmesPorCategoria : ix.seriesPorCategoria;
  categorias.forEach(function (c) { lista.push({ chave: c.id, titulo: c.nome, itens: porCategoria[c.id] }); });
  return lista;
}

export function Capas(props) {
  var lista = fileiras(props.tipo);
  var [linha, setLinha] = useState(0);
  var [, tique] = useState(0);
  var colunas = useRef({});
  var l = Math.min(linha, Math.max(0, lista.length - 1));
  var atual = lista[l];
  var coluna = atual ? Math.min(colunas.current[atual.chave] || 0, atual.itens.length - 1) : 0;
  // Fileiras inteiras que cabem abaixo das abas (6 rem) e acima do rodape (3 rem), mais uma cortada
  var FILEIRAS_VISIVEIS = Math.max(2, Math.ceil((window.innerHeight / (parseFloat(window.getComputedStyle(document.documentElement).fontSize) || 16) - 9) / ALTURA_FILEIRA));
  var topo = useJanela(l, FILEIRAS_VISIVEIS - 1, lista.length);

  props.teclas.atual = function (acao) {
    if (!atual) return acao === 'cima' ? 'subir' : false;
    if (acao === 'cima') { if (l === 0) return 'subir'; setLinha(l - 1); return true; }
    if (acao === 'baixo') { setLinha(Math.min(lista.length - 1, l + 1)); return true; }
    if (acao === 'esq' || acao === 'dir') {
      var nova = Math.max(0, Math.min(atual.itens.length - 1, coluna + (acao === 'dir' ? 1 : -1)));
      colunas.current[atual.chave] = nova;
      tique(function (n) { return n + 1; });
      return true;
    }
    if (acao === 'ok') { props.aoAbrir(atual.itens[coluna]); return true; }
    if (acao === 'segurar') { alternarFavorito(props.tipo, atual.itens[coluna]); return true; }
    return false;
  };

  if (!estado.indice) return <div class="vazio">{estado.sincronizando ? 'Carregando as listas…' : 'Sem listas ainda. Configure o login em Ajustes.'}</div>;
  if (!lista.length) return <div class="vazio">Nada aqui ainda.</div>;

  var desenhadas = [];
  for (var i = topo; i < Math.min(lista.length, topo + FILEIRAS_VISIVEIS + 1); i++) {
    var f = lista[i];
    var focadaAqui = props.focado && i === l;
    var colunaAqui = Math.min(colunas.current[f.chave] || 0, f.itens.length - 1);
    desenhadas.push(
      <div key={f.chave} class="bloco-fileira" style={'top:' + (i - topo) * ALTURA_FILEIRA + 'rem'}>
        <div class="titulo-fileira">{f.titulo}</div>
        <Fileira
          itens={f.itens} indice={colunaAqui} focada={focadaAqui} largura={LARGURA_CAPA} altura={12.5} visiveis={itensQueCabem(LARGURA_CAPA, 3)}
          chave={function (item) { return item.id; }}
          render={function (item, j, focado) {
            return <Capa src={item.capa} nome={item.nome} focado={focado} favorito={ehFavorito(props.tipo, item)} />;
          }}
        />
      </div>,
    );
  }
  return (
    <div class="capas">
      <div class="area-fileiras">{desenhadas}</div>
      {atual && atual.itens[coluna] ? <div class="rodape-capa">{atual.itens[coluna].nome}{atual.itens[coluna].ano ? '  ·  ' + atual.itens[coluna].ano : ''}</div> : null}
    </div>
  );
}
