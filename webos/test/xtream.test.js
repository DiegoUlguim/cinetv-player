// Casos do ClienteXtreamTeste e da sincronizacao do Android, com um fetch falso.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ClienteXtream, classificarResposta, SENHA_RECUSADA } from '../app/src/dados/xtream.js';
import { indexar, precisaSincronizar, sincronizar } from '../app/src/dados/sincronizar.js';

function resposta(status, tipo, corpo) {
  return {
    status: status,
    headers: { get: function () { return tipo; } },
    json: function () { return Promise.resolve(typeof corpo === 'string' ? JSON.parse(corpo) : corpo); },
  };
}

var login = { servidor: 'http://srv', usuario: 'joao', senha: 'segredo' };

test('cliente: 401 e 403 em JSON sao senha recusada; HTML e bloqueio; 404 guarda o codigo', function () {
  assert.equal(classificarResposta(401, 'application/json').message, SENHA_RECUSADA);
  assert.equal(classificarResposta(403, 'application/json').message, SENHA_RECUSADA);
  assert.match(classificarResposta(403, 'text/html').message, /bloqueou/);
  assert.match(classificarResposta(200, 'text/html; charset=utf-8').message, /bloqueou/);
  assert.equal(classificarResposta(404, 'application/json').codigoHttp, 404);
  assert.equal(classificarResposta(200, 'application/json; charset=utf-8'), null);
});

test('cliente: auth=0 e senha recusada; falha de rede vira mensagem sem usuario nem senha', async function () {
  var c = new ClienteXtream(login, function () { return Promise.resolve(resposta(200, 'application/json', { user_info: { auth: 0 } })); });
  await assert.rejects(c.conta(), function (e) { return e.message === SENHA_RECUSADA; });
  var semRede = new ClienteXtream(login, function () { return Promise.reject(new TypeError('Failed to fetch')); });
  await assert.rejects(semRede.conta(), function (e) { return !/joao|segredo/.test(e.message); });
});

test('cliente: URLs de reproducao no padrao Xtream', function () {
  var c = new ClienteXtream(login);
  assert.equal(c.urlAoVivo(7), 'http://srv/live/joao/segredo/7.ts');
  assert.equal(c.urlFilme(8, 'mkv'), 'http://srv/movie/joao/segredo/8.mkv');
  assert.equal(c.urlEpisodio(9, 'mp4'), 'http://srv/series/joao/segredo/9.mp4');
  assert.match(c.urlApi('get_short_epg', { stream_id: 5 }), /action=get_short_epg&stream_id=5$/);
});

var respostas = {
  '': { user_info: { auth: 1, status: 'Active', exp_date: '1798761600' } },
  get_live_categories: [{ category_id: '1', category_name: 'Abertos' }, { category_id: '9', category_name: 'XXX: +18 | Adultos' }],
  get_live_streams: [
    { stream_id: 101, name: 'Canal X FHD', category_id: '1' },
    { stream_id: 102, name: 'Canal X HD', category_id: '1', epg_channel_id: 'canal.x' },
    { stream_id: 103, name: 'Canal X SD', category_id: '1' },
    { stream_id: 104, name: 'Canal Y', category_id: '1' },
    { stream_id: 105, name: 'Canal Adulto', category_id: '9' },
  ],
  get_vod_categories: [{ category_id: '2', category_name: 'Ação' }, { category_id: '8', category_name: 'Adultos +18' }],
  get_vod_streams: [
    { stream_id: 201, name: 'Filme A', category_id: '2', container_extension: 'mp4' },
    { stream_id: 202, name: 'Filme B', category_id: '2', container_extension: 'mkv' },
    { stream_id: 203, name: 'Filme Adulto', category_id: '8' },
  ],
  get_series_categories: [{ category_id: '3', category_name: 'Drama' }, { category_id: '7', category_name: 'XXX Adultos' }],
  get_series: [
    { series_id: 301, name: 'Serie A', category_id: '3' },
    { series_id: 302, name: 'Serie B', category_id: '3' },
    { series_id: 303, name: 'Serie Adulta', category_id: '7' },
  ],
};

function servidorFalso(falharSeries) {
  return function (url) {
    var acao = (/action=([a-z_]+)/.exec(url) || [])[1] || '';
    if (falharSeries && acao === 'get_series') return Promise.resolve(resposta(500, 'application/json', {}));
    return Promise.resolve(resposta(200, 'application/json', respostas[acao]));
  };
}

test('sincronizacao: agrupa o trio, esconde adultos e guarda a conta', async function () {
  var r = await sincronizar(new ClienteXtream(login, servidorFalso(false)), 5000);
  assert.deepEqual([r.resumo.canais, r.resumo.filmes, r.resumo.series], [2, 2, 2]);
  assert.equal(r.catalogo.canais.length, 4);
  assert.equal(r.catalogo.categorias.live.length + r.catalogo.categorias.vod.length + r.catalogo.categorias.series.length, 3);
  var rep = r.catalogo.canais.filter(function (c) { return c.id === 101; })[0];
  assert.ok(rep.representante);
  assert.equal(rep.epgId, 'canal.x', 'o representante herda o guia do trio');
  assert.equal(r.catalogo.meta.expiraEm, 1798761600);
  var ix = indexar(r.catalogo);
  assert.equal(ix.canaisPorCategoria['1'].length, 2);
  assert.equal(ix.variantes[rep.grupo].length, 3);
});

test('sincronizacao: senha recusada ou falha no meio nao devolve catalogo (o anterior fica)', async function () {
  await assert.rejects(sincronizar(new ClienteXtream(login, servidorFalso(true)), 0), function (e) { return e.codigoHttp === 500; });
  var recusa = function () { return Promise.resolve(resposta(200, 'application/json', { user_info: { auth: 0 } })); };
  await assert.rejects(sincronizar(new ClienteXtream(login, recusa), 0), function (e) { return e.message === SENHA_RECUSADA; });
});

test('sincronizacao: regra das 24 h', function () {
  assert.ok(precisaSincronizar(null, 0));
  var cat = { canais: [1], meta: { ultima: 0 } };
  assert.ok(!precisaSincronizar(cat, 23 * 3600000));
  assert.ok(precisaSincronizar(cat, 25 * 3600000));
});
