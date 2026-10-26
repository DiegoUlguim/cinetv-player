import { test } from 'node:test';
import assert from 'node:assert/strict';
import { partesMudadas } from '../app/src/dados/banco.js';

test('so as partes com assinatura nova sao regravadas', () => {
  var antes = { filmes: 'a', series: 'b', canais: 'c' };
  assert.deepEqual(partesMudadas(antes, { filmes: 'a2', series: 'b', canais: 'c' }), ['filmes']);
  assert.deepEqual(partesMudadas(antes, { filmes: 'a', series: 'b', canais: 'c' }), []);
});

test('sem gravacao anterior, grava tudo (filmes primeiro, a maior)', () => {
  assert.deepEqual(partesMudadas(null, { filmes: 'a', series: 'b', canais: 'c' }), ['filmes', 'series', 'canais']);
});
