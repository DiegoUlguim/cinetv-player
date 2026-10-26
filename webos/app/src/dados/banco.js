// IndexedDB: catalogo em partes (canais, filmes, series e a meta) e dados do usuario por registro.
//
// A TV de teste tem ~18 MB de cota e nao devolve na hora o espaco apagado: duas copias do catalogo inteiro
// estouravam, e apagar a antiga para tentar de novo deixava a TV sem listas (baixava tudo a cada abertura).
// Agora cada parte e regravada sozinha, so se mudou, e a meta vai por ultimo: se uma parte falhar, a meta
// antiga fica (a proxima abertura sincroniza de novo) e nada do que estava guardado e apagado.

import { assinatura, dividir, expandir, juntarPartes, PARTES } from './compacto.js';

var NOME = 'cinetv';
var VERSAO_ESQUEMA = 1;
var abrindo = null;

function pedido(req) {
  return new Promise(function (resolver, rejeitar) {
    req.onsuccess = function () { resolver(req.result); };
    req.onerror = function () { rejeitar(req.error); };
  });
}

function fimDaTransacao(tx) {
  return new Promise(function (resolver, rejeitar) {
    tx.oncomplete = function () { resolver(); };
    tx.onerror = function () { rejeitar(tx.error); };
    tx.onabort = function () { rejeitar(tx.error || new Error('transacao abortada')); };
  });
}

export function abrirBanco() {
  if (abrindo) return abrindo;
  abrindo = new Promise(function (resolver, rejeitar) {
    var req = indexedDB.open(NOME, VERSAO_ESQUEMA);
    req.onupgradeneeded = function () {
      var db = req.result;
      if (!db.objectStoreNames.contains('catalogo')) db.createObjectStore('catalogo');
      if (!db.objectStoreNames.contains('progresso')) db.createObjectStore('progresso', { keyPath: 'chave' });
      if (!db.objectStoreNames.contains('favoritos')) db.createObjectStore('favoritos', { keyPath: 'chave' });
    };
    req.onsuccess = function () { resolver(req.result); };
    req.onerror = function () { rejeitar(req.error); };
  });
  return abrindo;
}

/** Catalogo guardado, ou null se nunca sincronizou. Le o formato em partes e, se nao houver, o antigo (v1, v2...). */
export function lerCatalogo() {
  return abrirBanco().then(function (db) {
    var tx = db.transaction('catalogo', 'readonly');
    var loja = tx.objectStore('catalogo');
    var lidos = {};
    var pedidos = PARTES.concat(['meta']).map(function (n) {
      return pedido(loja.get('p:' + n)).then(function (v) { lidos[n] = v; });
    });
    pedidos.push(pedido(loja.get('atual')).then(function (atual) {
      return atual ? pedido(loja.get('v' + atual)).then(function (v) { lidos.antigo = v; }) : null;
    }));
    return Promise.all(pedidos).then(function () {
      return fimDaTransacao(tx).then(function () {
        var completo = lidos.meta && PARTES.every(function (n) { return lidos[n]; });
        if (completo) return juntarPartes(lidos.meta.meta, lidos);
        return lidos.antigo ? expandir(lidos.antigo) : null;
      });
    });
  });
}

/**
 * Espaco usado e cota do armazenamento da TV, em bytes (API do Chrome antigo), ou null se a TV nao informar.
 * Vai para o log a cada sincronizacao: e o numero que diz se o catalogo cabe.
 */
export function espacoUsado() {
  var api = typeof navigator !== 'undefined' && navigator.webkitTemporaryStorage;
  if (!api) return Promise.resolve(null);
  return new Promise(function (resolver) {
    api.queryUsageAndQuota(function (usado, cota) { resolver({ usado: usado, cota: cota }); }, function () { resolver(null); });
  });
}

function gravarChave(db, chave, valor, apagar) {
  var tx = db.transaction('catalogo', 'readwrite');
  var loja = tx.objectStore('catalogo');
  (apagar || []).forEach(function (k) { loja.delete(k); });
  loja.put(valor, chave);
  return fimDaTransacao(tx);
}

/** Partes cuja assinatura mudou desde a ultima gravacao (todas, se nunca gravou). */
export function partesMudadas(antes, novas) {
  return PARTES.filter(function (n) { return !antes || antes[n] !== novas[n]; });
}

/** Grava as partes que mudaram, uma por transacao, e a meta por ultimo. Devolve os nomes das partes gravadas. */
export function gravarCatalogo(catalogo) {
  var partes = dividir(catalogo);
  var novas = {};
  PARTES.forEach(function (n) { novas[n] = assinatura(JSON.stringify(partes[n])); });
  return abrirBanco().then(function (db) {
    var ler = db.transaction('catalogo', 'readonly').objectStore('catalogo');
    return Promise.all([pedido(ler.get('p:meta')), pedido(ler.getAllKeys())]).then(function (r) {
      var mudadas = partesMudadas(r[0] && r[0].assinaturas, novas);
      // O formato antigo (atual, v1, v2...) sai junto com a primeira gravacao
      var legado = r[1].filter(function (k) { return String(k).indexOf('p:') !== 0; });
      var passo = Promise.resolve();
      mudadas.forEach(function (n, i) {
        passo = passo.then(function () { return gravarChave(db, 'p:' + n, partes[n], i === 0 ? legado : null); });
      });
      return passo
        .then(function () { return gravarChave(db, 'p:meta', { meta: catalogo.meta, assinaturas: novas }, mudadas.length ? null : legado); })
        .then(function () { return mudadas; });
    });
  });
}

function todos(loja) {
  return abrirBanco().then(function (db) {
    var tx = db.transaction(loja, 'readonly');
    var s = tx.objectStore(loja);
    if (s.getAll) return pedido(s.getAll());
    return new Promise(function (resolver, rejeitar) {
      var lista = [];
      var c = s.openCursor();
      c.onsuccess = function () { var cur = c.result; if (cur) { lista.push(cur.value); cur.continue(); } else resolver(lista); };
      c.onerror = function () { rejeitar(c.error); };
    });
  });
}

function gravar(loja, registro) {
  return abrirBanco().then(function (db) {
    var tx = db.transaction(loja, 'readwrite');
    tx.objectStore(loja).put(registro);
    return fimDaTransacao(tx);
  });
}

function apagar(loja, chave) {
  return abrirBanco().then(function (db) {
    var tx = db.transaction(loja, 'readwrite');
    tx.objectStore(loja).delete(chave);
    return fimDaTransacao(tx);
  });
}

export var progresso = {
  todos: function () { return todos('progresso'); },
  gravar: function (registro) { return gravar('progresso', registro); },
  apagar: function (chave) { return apagar('progresso', chave); },
};

export var favoritos = {
  todos: function () { return todos('favoritos'); },
  gravar: function (registro) { return gravar('favoritos', registro); },
  apagar: function (chave) { return apagar('favoritos', chave); },
};
