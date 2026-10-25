import { test } from 'node:test';
import assert from 'node:assert/strict';
import { comReserva, faltaDeEspaco } from '../app/src/dados/banco.js';

function erro(nome, mensagem) {
  var e = new Error(mensagem || '');
  e.name = nome;
  return e;
}

test('falta de espaco: libera a versao antiga e grava de novo', async () => {
  var chamadas = [];
  var tentativas = 0;
  var gravar = () => { chamadas.push('gravar'); tentativas++; return tentativas === 1 ? Promise.reject(erro('QuotaExceededError')) : Promise.resolve('ok'); };
  var liberar = () => { chamadas.push('liberar'); return Promise.resolve(); };
  assert.equal(await comReserva(gravar, liberar), 'ok');
  assert.deepEqual(chamadas, ['gravar', 'liberar', 'gravar']);
});

test('outro erro sobe sem apagar as listas guardadas', async () => {
  var liberou = false;
  await assert.rejects(
    comReserva(() => Promise.reject(erro('AbortError')), () => { liberou = true; return Promise.resolve(); }),
    { name: 'AbortError' },
  );
  assert.equal(liberou, false);
});

test('falta de espaco de novo na segunda tentativa sobe o erro', async () => {
  await assert.rejects(
    comReserva(() => Promise.reject(erro('QuotaExceededError')), () => Promise.resolve()),
    { name: 'QuotaExceededError' },
  );
});

test('reconhece a cota pelo nome ou pela mensagem do Chrome 53', () => {
  assert.equal(faltaDeEspaco(erro('QuotaExceededError')), true);
  assert.equal(faltaDeEspaco(erro('UnknownError', 'The operation failed because it exceeded the quota.')), true);
  assert.equal(faltaDeEspaco(erro('AbortError')), false);
  assert.equal(faltaDeEspaco(null), false);
});
