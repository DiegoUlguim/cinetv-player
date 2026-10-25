// Baixa as listas do servidor e monta o catalogo, como o Sincronizador.kt do Android.
import { agruparCanais, ehAdulta, mapearCanal, mapearCategoria, mapearFilme, mapearSerie, semAcento } from './regras.js';

export var VALIDADE_MS = 24 * 60 * 60 * 1000;

export function precisaSincronizar(catalogo, agoraMs) {
  return !catalogo || !catalogo.meta || agoraMs - catalogo.meta.ultima > VALIDADE_MS || !catalogo.canais.length;
}

/** Categorias sem as adultas e os ids das adultas, para filtrar os itens. */
function separarCategorias(lista) {
  var categorias = [];
  var adultas = {};
  (Array.isArray(lista) ? lista : []).forEach(function (item, ordem) {
    var c = mapearCategoria(item, ordem);
    if (!c) return;
    if (ehAdulta(c.nome)) adultas[c.id] = true;
    else categorias.push(c);
  });
  return { categorias: categorias, adultas: adultas };
}

function mapearLista(lista, mapear, adultas, resumo) {
  var saida = [];
  (Array.isArray(lista) ? lista : []).forEach(function (item, ordem) {
    var m = mapear(item, ordem);
    if (!m) { resumo.ignorados++; return; }
    if (adultas[m.categoriaId]) return;
    m.busca = semAcento(m.nome);
    saida.push(m);
  });
  return saida;
}

/**
 * Valida o login antes de qualquer coisa (senha errada nao apaga o catalogo atual) e devolve o catalogo novo.
 * aoEtapa recebe o nome da etapa para a tela mostrar o andamento.
 */
export function sincronizar(cliente, agoraMs, aoEtapa) {
  var etapa = aoEtapa || function () {};
  var inicio = Date.now();
  var resumo = { canais: 0, filmes: 0, series: 0, ignorados: 0, milissegundos: 0 };
  var catalogo = { categorias: {}, canais: [], filmes: [], series: [], meta: null };
  var conta;
  etapa('conta');
  return cliente.conta().then(function (c) {
    conta = c;
    etapa('canais');
    return Promise.all([cliente.json('get_live_categories'), cliente.json('get_live_streams')]);
  }).then(function (r) {
    var cats = separarCategorias(r[0]);
    catalogo.categorias.live = cats.categorias;
    catalogo.canais = agruparCanais(mapearLista(r[1], mapearCanal, cats.adultas, resumo));
    resumo.canais = catalogo.canais.filter(function (c) { return c.representante; }).length;
    etapa('filmes');
    return Promise.all([cliente.json('get_vod_categories'), cliente.json('get_vod_streams')]);
  }).then(function (r) {
    var cats = separarCategorias(r[0]);
    catalogo.categorias.vod = cats.categorias;
    catalogo.filmes = mapearLista(r[1], mapearFilme, cats.adultas, resumo);
    resumo.filmes = catalogo.filmes.length;
    etapa('series');
    return Promise.all([cliente.json('get_series_categories'), cliente.json('get_series')]);
  }).then(function (r) {
    var cats = separarCategorias(r[0]);
    catalogo.categorias.series = cats.categorias;
    catalogo.series = mapearLista(r[1], mapearSerie, cats.adultas, resumo);
    resumo.series = catalogo.series.length;
    catalogo.meta = { ultima: agoraMs, status: conta.status, expiraEm: conta.expiraEm };
    resumo.milissegundos = Date.now() - inicio;
    return { catalogo: catalogo, resumo: resumo };
  });
}

/** Indices em memoria para as telas: por categoria, por id e variantes por grupo. */
export function indexar(catalogo) {
  var porCategoria = function (lista) {
    var mapa = {};
    lista.forEach(function (item) { (mapa[item.categoriaId] = mapa[item.categoriaId] || []).push(item); });
    return mapa;
  };
  var porId = function (lista) {
    var mapa = {};
    lista.forEach(function (item) { mapa[item.id] = item; });
    return mapa;
  };
  var representantes = catalogo.canais.filter(function (c) { return c.representante; });
  var variantes = {};
  catalogo.canais.forEach(function (c) { (variantes[c.grupo] = variantes[c.grupo] || []).push(c); });
  var canaisPorCategoria = porCategoria(representantes);
  var filmesPorCategoria = porCategoria(catalogo.filmes);
  var seriesPorCategoria = porCategoria(catalogo.series);
  return {
    catalogo: catalogo,
    representantes: representantes,
    canaisPorCategoria: canaisPorCategoria,
    variantes: variantes,
    canalPorId: porId(catalogo.canais),
    filmePorId: porId(catalogo.filmes),
    seriePorId: porId(catalogo.series),
    filmesPorCategoria: filmesPorCategoria,
    seriesPorCategoria: seriesPorCategoria,
    // So categorias com itens aparecem
    categoriasAoVivo: catalogo.categorias.live.filter(function (c) { return canaisPorCategoria[c.id]; }),
    categoriasFilmes: catalogo.categorias.vod.filter(function (c) { return filmesPorCategoria[c.id]; }),
    categoriasSeries: catalogo.categorias.series.filter(function (c) { return seriesPorCategoria[c.id]; }),
  };
}
