// Casos portados dos testes JVM do Android (app/src/test) para as regras do app webOS.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  agruparCanais, bateBusca, ehAdulta, escolherVariante, lerGuia, lerSerie, mapearCanal, mapearFilme,
  normalizarServidor, palavrasDaBusca, primeiroTexto, proximoEpisodio, acharEpisodio, qualidadeAlvo,
  separarQualidade, varianteAbaixo, semAcento, mascarar, decodificarTitulo,
} from '../app/src/dados/regras.js';

function canal(id, nome, categoria, epg) {
  return { id: id, nome: nome, categoriaId: categoria || '1', logo: null, epgId: epg || null, ordem: id };
}

test('agrupamento: trio vira um representante com o guia do grupo', function () {
  var r = agruparCanais([
    canal(1, '01 - Globo SP Capital FHD'), canal(2, '01 - Globo SP Capital HD', '1', 'globo.sp'),
    canal(3, '01 - Globo SP Capital SD'), canal(4, 'Pluto TV Anime Ação'),
  ]);
  var reps = r.filter(function (c) { return c.representante; });
  assert.deepEqual(reps.map(function (c) { return c.id; }), [1, 4]);
  assert.equal(reps[0].nomeBase, '01 - Globo SP Capital');
  assert.equal(reps[0].epgId, 'globo.sp');
  var qs = r.filter(function (c) { return c.grupo === reps[0].grupo; }).map(function (c) { return c.qualidade; }).sort();
  assert.deepEqual(qs, ['FHD', 'HD', 'SD']);
});

test('agrupamento: mesmo nome em categorias diferentes nao se junta', function () {
  var r = agruparCanais([canal(1, 'ESPN HD', 'esportes'), canal(2, 'ESPN SD', 'abertos')]);
  assert.equal(r.filter(function (c) { return c.representante; }).length, 2);
});

test('qualidade: separa o sufixo do nome', function () {
  assert.deepEqual(separarQualidade('01 - Globo SP Capital FHD'), { base: '01 - Globo SP Capital', qualidade: 'FHD' });
  assert.deepEqual(separarQualidade('+SBT: Kids SD'), { base: '+SBT: Kids', qualidade: 'SD' });
  assert.deepEqual(separarQualidade('ESPN 4K'), { base: 'ESPN', qualidade: 'UHD' });
  assert.deepEqual(separarQualidade('Canal | HD'), { base: 'Canal', qualidade: 'HD' });
});

test('qualidade: nome sem sufixo ou so com sufixo fica inteiro', function () {
  assert.deepEqual(separarQualidade('Pluto TV Anime Ação'), { base: 'Pluto TV Anime Ação', qualidade: null });
  assert.deepEqual(separarQualidade('HD'), { base: 'HD', qualidade: null });
  assert.deepEqual(separarQualidade('SHD Esportes'), { base: 'SHD Esportes', qualidade: null });
});

test('qualidade: regra automatica pela banda; sem medida comeca em FHD (decisao de 24/10)', function () {
  assert.equal(qualidadeAlvo('AUTOMATICA', 25000000), 'FHD');
  assert.equal(qualidadeAlvo('AUTOMATICA', 7000000), 'HD');
  assert.equal(qualidadeAlvo('AUTOMATICA', 3000000), 'SD');
  assert.equal(qualidadeAlvo('AUTOMATICA', null), 'FHD');
  assert.equal(qualidadeAlvo('SD', 50000000), 'SD');
});

test('qualidade: escolhe a maior que cabe no alvo ou a menor disponivel', function () {
  var trio = ['FHD', 'HD', 'SD'];
  assert.equal(escolherVariante(trio, 'FHD'), 0);
  assert.equal(escolherVariante(trio, 'HD'), 1);
  assert.equal(escolherVariante(trio, 'SD'), 2);
  assert.equal(escolherVariante(['FHD', 'UHD'], 'SD'), 0);
  assert.equal(escolherVariante([null], 'SD'), 0);
});

test('qualidade: desce uma quando trava', function () {
  var trio = ['FHD', 'HD', 'SD'];
  assert.equal(varianteAbaixo(trio, 0), 1);
  assert.equal(varianteAbaixo(trio, 1), 2);
  assert.equal(varianteAbaixo(trio, 2), null);
});

test('busca: cada palavra e prefixo, sem acento, sem pontuacao', function () {
  assert.deepEqual(palavrasDaBusca('Homem Aranha'), ['homem', 'aranha']);
  assert.deepEqual(palavrasDaBusca('"Ação" - OR TV!'), ['acao', 'or', 'tv']);
  assert.deepEqual(palavrasDaBusca('  -- "" '), []);
  assert.ok(bateBusca(semAcento('Ação Total'), palavrasDaBusca('acao')));
  assert.ok(bateBusca(semAcento('O Homem-Aranha 2'), palavrasDaBusca('homem ara')));
  assert.ok(!bateBusca(semAcento('Aranhas de Papel'), palavrasDaBusca('homem ara')));
  assert.ok(!bateBusca(semAcento('Ação'), []));
});

test('servidor: normaliza esquema, porta e caminho colado', function () {
  assert.equal(normalizarServidor('exemplo.cc'), 'https://exemplo.cc');
  assert.equal(normalizarServidor(' http://exemplo.cc:8080/ '), 'http://exemplo.cc:8080');
  assert.equal(normalizarServidor('https://exemplo.cc/player_api.php?username=a&password=b'), 'https://exemplo.cc');
});

test('leitura tolerante: canal com epg nulo ou vazio e icone vazio', function () {
  var a = mapearCanal({ stream_id: 10, name: 'Canal A', category_id: '5', stream_icon: '', epg_channel_id: null }, 0);
  var b = mapearCanal({ stream_id: '11', name: 'Canal B', category_id: '5', stream_icon: 'http://x/l.png', epg_channel_id: '' }, 1);
  assert.equal(a.logo, null);
  assert.equal(a.epgId, null);
  assert.equal(b.id, 11);
  assert.equal(b.epgId, null);
});

test('leitura tolerante: filme com nota inteira ou decimal e listas vazias', function () {
  var f1 = mapearFilme({ stream_id: 1, name: 'F1', category_id: '9', rating_5based: 4, backdrop_path: [], container_extension: 'mp4', added: '1700000000' }, 0);
  var f2 = mapearFilme({ stream_id: 2, name: 'F2', category_id: '9', rating_5based: 3.5, release_date: '', year: '2021' }, 1);
  var f3 = mapearFilme({ stream_id: 3, name: 'F3', category_id: '9', rating_5based: 0, container_extension: '' }, 2);
  assert.equal(f1.nota, 4);
  assert.equal(f1.adicionadoEm, 1700000000);
  assert.equal(primeiroTexto({ backdrop_path: [] }, 'backdrop_path'), null);
  assert.equal(primeiroTexto({ backdrop_path: ['http://x/b.jpg'] }, 'backdrop_path'), 'http://x/b.jpg');
  assert.equal(f2.nota, 3.5);
  assert.equal(f2.ano, '2021');
  assert.equal(f3.nota, null, 'nota 0 significa sem nota');
  assert.equal(f3.extensao, 'mp4', 'extensao vazia cai no padrao');
});

test('leitura tolerante: item sem id, sem nome ou que nao e objeto e ignorado', function () {
  assert.equal(mapearCanal({ stream_id: '', name: 'Sem id' }, 0), null);
  assert.equal(mapearCanal({ stream_id: 5, name: '' }, 1), null);
  assert.equal(mapearCanal('lixo', 2), null);
  assert.equal(mapearCanal({ stream_id: 6, name: 'Ok', category_id: '1' }, 3).id, 6);
});

test('categorias adultas do servidor', function () {
  assert.ok(ehAdulta('XXX: +18 | Adultos'));
  assert.ok(ehAdulta('XXX | +18 Adultos - Sexy Hot'));
  assert.ok(ehAdulta('XXX: +18 Reality'));
  assert.ok(!ehAdulta('Filmes | Ação'));
  assert.ok(!ehAdulta('Canais | Hotelaria e Turismo'));
  assert.ok(!ehAdulta('Séries | 2018'));
});

var amostraSerie = {
  seasons: [{ season_number: 1, name: 'Temporada 1' }],
  info: { plot: 'Sinopse', genre: 'Drama', backdrop_path: ['http://x/f.jpg'], cover: 'http://x/c.jpg' },
  episodes: {
    2: [{ id: '900', episode_num: '1', title: 'S2E1', container_extension: 'mkv', season: 2, info: { duration_secs: 2400, movie_image: 'http://x/e.jpg' } }],
    1: [
      { id: '377288', episode_num: '2', title: 'Episódio 2', container_extension: 'mp4', season: 1, info: { plot: '' } },
      { id: '377287', episode_num: '1', title: 'Episódio 1', container_extension: 'mp4', season: 1, info: [] },
      { id: '', episode_num: '3', title: 'Sem id' },
    ],
    x: [{ id: '1' }],
  },
};

test('serie: temporadas em ordem e episodios ordenados', function () {
  var s = lerSerie(amostraSerie);
  assert.deepEqual(s.temporadas, [1, 2]);
  assert.deepEqual(s.episodios[1].map(function (e) { return e.id; }), [377287, 377288]);
  assert.equal(s.episodios[2][0].extensao, 'mkv');
  assert.equal(s.episodios[2][0].duracaoSegundos, 2400);
  assert.equal(s.episodios[2][0].imagem, 'http://x/e.jpg');
  assert.equal(s.episodios[1][0].imagem, null);
  assert.equal(s.fundo, 'http://x/f.jpg');
});

test('serie: proximo episodio atravessa temporada; info como lista vazia nao quebra', function () {
  var s = lerSerie(amostraSerie);
  assert.equal(proximoEpisodio(s, 377287).id, 377288);
  assert.equal(proximoEpisodio(s, 377288).id, 900);
  assert.equal(proximoEpisodio(s, 900), null);
  assert.equal(acharEpisodio(s, 377287).duracaoSegundos, null);
});

test('guia: [] e objeto sem epg_listings sao "sem guia"; programa atual e seguinte', function () {
  var agora = 1000000 * 1000;
  assert.equal(lerGuia([], agora), null);
  var titulo = Buffer.from('Jornal Nacional', 'utf8').toString('base64');
  var g = lerGuia({ epg_listings: [
    { title: Buffer.from('Novela', 'utf8').toString('base64'), start_timestamp: 1000600, stop_timestamp: 1004200 },
    { title: titulo, start_timestamp: '999000', stop_timestamp: '1000600' },
    { title: 'antigo', start_timestamp: 990000, stop_timestamp: 995000 },
  ] }, agora, function (b) { return Buffer.from(b, 'base64').toString('latin1'); });
  assert.equal(g.agora.titulo, 'Jornal Nacional');
  assert.equal(g.seguinte.titulo, 'Novela');
  assert.deepEqual(g.proximos.map(function (p) { return p.titulo; }), ['Jornal Nacional', 'Novela']);
  assert.deepEqual(lerGuia({}, agora), { agora: null, seguinte: null, proximos: [] });
});

test('guia: titulo que nao e base64 fica como veio', function () {
  assert.equal(decodificarTitulo('###', function () { throw new Error('nao e base64'); }), '###');
});

test('log: usuario e senha mascarados, inclusive codificados na URL', function () {
  var t = mascarar('http://h/live/joao/s3nh@/1.ts e s3nh%40', ['joao', 's3nh@']);
  assert.equal(t, 'http://h/live/***/***/1.ts e ***');
});
