// Formato guardado no IndexedDB. A TV de teste tem cerca de 18 MB de cota e o catalogo em objetos (9 MB em JSON,
// mais no banco) nao cabia duas vezes: cada item vira uma lista de valores, sem nome de campo, sem o que se
// recalcula (busca, ordem, agrupamento dos canais) e com o comeco das URLs de capa guardado uma vez so.
import { agruparCanais, semAcento } from './regras.js';

export var FORMATO = 2;

function Prefixos() {
  this.lista = [];
  this.indice = {};
}

/** Capa em [indice do prefixo, resto]; o prefixo e tudo ate a ultima barra. */
Prefixos.prototype.separar = function (url) {
  if (!url) return [-1, null];
  var corte = url.lastIndexOf('/') + 1;
  var prefixo = url.substring(0, corte);
  var i = this.indice[prefixo];
  if (i === undefined) { i = this.lista.length; this.lista.push(prefixo); this.indice[prefixo] = i; }
  return [i, url.substring(corte)];
};

function juntar(prefixos, i, resto) {
  return i < 0 ? null : prefixos[i] + resto;
}

export function compactar(catalogo) {
  var p = new Prefixos();
  var canais = [];
  catalogo.canais.forEach(function (c) {
    var logo = p.separar(c.logo);
    canais.push([c.id, c.nome, c.categoriaId, logo[0], logo[1], c.epgId]);
  });
  var filmes = catalogo.filmes.map(function (f) {
    var capa = p.separar(f.capa);
    return [f.id, f.nome, f.categoriaId, capa[0], capa[1], f.nota, f.ano, f.extensao === 'mp4' ? null : f.extensao];
  });
  var series = catalogo.series.map(function (s) {
    var capa = p.separar(s.capa);
    return [s.id, s.nome, s.categoriaId, capa[0], capa[1], s.nota, s.ano];
  });
  return { formato: FORMATO, meta: catalogo.meta, categorias: catalogo.categorias, prefixos: p.lista, canais: canais, filmes: filmes, series: series };
}

/** Volta ao catalogo que as telas usam. Catalogo antigo (objetos, sem formato) passa direto. */
export function expandir(guardado) {
  if (!guardado || guardado.formato !== FORMATO) return guardado;
  var pre = guardado.prefixos;
  var canais = agruparCanais(guardado.canais.map(function (t, ordem) {
    return { id: t[0], nome: t[1], categoriaId: t[2], logo: juntar(pre, t[3], t[4]), epgId: t[5], ordem: ordem, busca: semAcento(t[1]) };
  }));
  var filmes = guardado.filmes.map(function (t, ordem) {
    return { id: t[0], nome: t[1], categoriaId: t[2], capa: juntar(pre, t[3], t[4]), nota: t[5], ano: t[6], extensao: t[7] || 'mp4', ordem: ordem, busca: semAcento(t[1]) };
  });
  var series = guardado.series.map(function (t, ordem) {
    return { id: t[0], nome: t[1], categoriaId: t[2], capa: juntar(pre, t[3], t[4]), nota: t[5], ano: t[6], ordem: ordem, busca: semAcento(t[1]) };
  });
  return { meta: guardado.meta, categorias: guardado.categorias, canais: canais, filmes: filmes, series: series };
}
