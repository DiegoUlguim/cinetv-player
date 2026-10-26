import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assinatura, compactar, dividir, expandir, juntarPartes } from '../app/src/dados/compacto.js';
import { agruparCanais, semAcento } from '../app/src/dados/regras.js';

function catalogo() {
  var canal = (id, nome, epgId) => ({ id: id, nome: nome, categoriaId: '7', logo: 'http://logos.exemplo/c/' + id + '.png', epgId: epgId, ordem: id, busca: semAcento(nome) });
  return {
    meta: { ultima: 1, status: 'Active', expiraEm: 2 },
    categorias: { live: [{ id: '7', nome: 'Notícias', ordem: 0 }], vod: [], series: [] },
    canais: agruparCanais([canal(1, 'Canal Um FHD', 'um.br'), canal(2, 'Canal Um HD', null), { id: 3, nome: 'Sem Logo', categoriaId: '7', logo: null, epgId: null, ordem: 2, busca: 'sem logo' }]),
    filmes: [
      { id: 10, nome: 'Ação Total', categoriaId: '3', capa: 'https://image.tmdb.org/t/p/w600/abc.jpg', nota: 4.5, ano: '2020', extensao: 'mp4', adicionadoEm: 9, ordem: 0, busca: 'acao total' },
      { id: 11, nome: 'Outro', categoriaId: '3', capa: null, nota: null, ano: null, extensao: 'mkv', adicionadoEm: null, ordem: 1, busca: 'outro' },
    ],
    series: [{ id: 20, nome: 'Série', categoriaId: '5', capa: 'https://image.tmdb.org/t/p/w600/s.jpg', nota: 3, ano: '2019', ordem: 0, busca: 'serie' }],
  };
}

var campos = (lista, nomes) => lista.map((item) => Object.fromEntries(nomes.map((n) => [n, item[n]])));

test('compacto: ida e volta preserva o que as telas usam', () => {
  var original = catalogo();
  var volta = expandir(JSON.parse(JSON.stringify(compactar(original))));
  assert.deepEqual(volta.meta, original.meta);
  assert.deepEqual(volta.categorias, original.categorias);
  var canal = ['id', 'nome', 'categoriaId', 'logo', 'epgId', 'busca', 'nomeBase', 'qualidade', 'grupo', 'representante'];
  assert.deepEqual(campos(volta.canais, canal), campos(original.canais, canal));
  var filme = ['id', 'nome', 'categoriaId', 'capa', 'nota', 'ano', 'extensao', 'busca'];
  assert.deepEqual(campos(volta.filmes, filme), campos(original.filmes, filme));
  var serie = ['id', 'nome', 'categoriaId', 'capa', 'nota', 'ano', 'busca'];
  assert.deepEqual(campos(volta.series, serie), campos(original.series, serie));
});

test('compacto: o comeco das capas e guardado uma vez so', () => {
  var c = compactar(catalogo());
  assert.deepEqual(c.prefixos, ['http://logos.exemplo/c/', 'https://image.tmdb.org/t/p/w600/']);
});

test('compacto: catalogo antigo, em objetos, passa direto', () => {
  var antigo = catalogo();
  assert.equal(expandir(antigo), antigo);
});

test('partes: dividir e juntar devolvem o mesmo catalogo que o formato inteiro', () => {
  var original = catalogo();
  var partes = JSON.parse(JSON.stringify(dividir(original)));
  var volta = juntarPartes(original.meta, partes);
  var inteiro = expandir(JSON.parse(JSON.stringify(compactar(original))));
  assert.deepEqual(volta, inteiro);
});

test('partes: mudar um filme muda so a assinatura dos filmes', () => {
  var a = dividir(catalogo());
  var outro = catalogo();
  outro.filmes[0].nome = 'Ação Total 2';
  var b = dividir(outro);
  var ass = (p) => assinatura(JSON.stringify(p));
  assert.notEqual(ass(a.filmes), ass(b.filmes));
  assert.equal(ass(a.series), ass(b.series));
  assert.equal(ass(a.canais), ass(b.canais));
});
