// IndexedDB: catalogo inteiro por versao (troca atomica de ponteiro) e dados do usuario por registro.
//
// No Android a sincronizacao grava numa transacao unica lendo da rede. No IndexedDB a transacao fecha sozinha
// no primeiro fetch, entao a versao nova e gravada inteira (a TV de teste tem 1 GB de limite de memoria e leu
// os 9 MB de filmes em 1,2 s) e so depois o ponteiro "atual" troca, junto com a exclusao da versao antiga.

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

/** Catalogo atual, ou null se nunca sincronizou. Apaga versoes orfas (sincronizacao interrompida). */
export function lerCatalogo() {
  return abrirBanco().then(function (db) {
    var tx = db.transaction('catalogo', 'readwrite');
    var loja = tx.objectStore('catalogo');
    return pedido(loja.get('atual')).then(function (atual) {
      return pedido(loja.getAllKeys ? loja.getAllKeys() : loja.openCursor()).then(function (chaves) {
        if (Array.isArray(chaves)) {
          chaves.forEach(function (k) { if (k !== 'atual' && k !== 'v' + atual) loja.delete(k); });
        }
        return atual ? pedido(loja.get('v' + atual)) : null;
      });
    }).then(function (catalogo) {
      return fimDaTransacao(tx).then(function () { return catalogo || null; });
    });
  });
}

/**
 * Grava a versao nova e, numa segunda transacao, troca o ponteiro e apaga a anterior.
 * Se nao couber (as duas versoes juntas passam do espaco da TV), apaga a anterior e tenta de novo: perde a troca
 * atomica so nesse caso, e o app segue com o catalogo novo na memoria mesmo se a segunda tentativa falhar.
 */
export function gravarCatalogo(catalogo) {
  return gravarComTroca(catalogo).catch(function (erro) {
    return apagarCatalogo().then(function () { return gravarComTroca(catalogo); }).catch(function (erro2) {
      throw erro2 || erro;
    });
  });
}

function apagarCatalogo() {
  return abrirBanco().then(function (db) {
    var tx = db.transaction('catalogo', 'readwrite');
    tx.objectStore('catalogo').clear();
    return fimDaTransacao(tx);
  });
}

function gravarComTroca(catalogo) {
  return abrirBanco().then(function (db) {
    var ler = db.transaction('catalogo', 'readonly');
    return pedido(ler.objectStore('catalogo').get('atual')).then(function (atual) {
      var nova = (atual || 0) + 1;
      var gravar = db.transaction('catalogo', 'readwrite');
      gravar.objectStore('catalogo').put(catalogo, 'v' + nova);
      return fimDaTransacao(gravar).then(function () {
        var trocar = db.transaction('catalogo', 'readwrite');
        var loja = trocar.objectStore('catalogo');
        loja.put(nova, 'atual');
        if (atual) loja.delete('v' + atual);
        return fimDaTransacao(trocar);
      });
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
