// Estado do app fora das telas: login, catalogo indexado, guia e dados do usuario (progresso e favoritos).
import { favoritos as lojaFavoritos, progresso as lojaProgresso, espacoUsado, gravarCatalogo, lerCatalogo } from './dados/banco.js';
import { RepositorioGuia } from './dados/guia.js';
import { medirVelocidadeSePreciso, preferencias } from './dados/preferencias.js';
import { mascarar, semAcento } from './dados/regras.js';
import { indexar, precisaSincronizar, sincronizar } from './dados/sincronizar.js';
import { ClienteXtream, ErroServidor } from './dados/xtream.js';

var ouvintes = [];

export var estado = {
  login: preferencias.login(),
  cliente: null,
  indice: null, // catalogo indexado (sincronizar.indexar)
  sincronizando: false,
  etapa: null,
  erro: null,
  velocidadeBps: (preferencias.velocidade() || {}).bps || null,
  progresso: {}, // chave -> registro
  favoritos: {}, // chave -> registro
  versao: 0, // muda a cada alteracao: as telas usam para redesenhar
};

estado.cliente = estado.login ? new ClienteXtream(estado.login) : null;

export var guia = new RepositorioGuia(function () { return estado.cliente; });
guia.registrar = function (m) { registrar(m); };

export function aoMudar(fn) {
  ouvintes.push(fn);
  return function () { ouvintes = ouvintes.filter(function (o) { return o !== fn; }); };
}

function avisar() {
  estado.versao++;
  ouvintes.forEach(function (fn) { fn(estado.versao); });
}

/** Log sem usuario e senha (as URLs do Xtream levam os dois). */
export function registrar(mensagem) {
  var segredos = estado.login ? [estado.login.usuario, estado.login.senha] : [];
  console.log('[CineTV] ' + mascarar(mensagem, segredos));
}

export function carregar() {
  return Promise.all([lerCatalogo(), lojaProgresso.todos(), lojaFavoritos.todos()]).then(function (r) {
    if (r[0]) estado.indice = indexar(r[0]);
    r[1].forEach(function (p) { estado.progresso[p.chave] = p; });
    r[2].forEach(function (f) { estado.favoritos[f.chave] = f; });
    avisar();
  }, function (e) {
    registrar('Falha ao ler o banco local: ' + e);
    avisar();
  });
}

/** Sincroniza se a lista tiver mais de 24 h (ou se forcar) e mede a velocidade no maximo a cada 6 h. */
export function sincronizarSePreciso(forcar) {
  if (!estado.cliente || estado.sincronizando) return Promise.resolve();
  var catalogo = estado.indice ? estado.indice.catalogo : null;
  var passo = Promise.resolve();
  if (forcar || precisaSincronizar(catalogo, Date.now())) {
    estado.sincronizando = true;
    estado.erro = null;
    avisar();
    passo = sincronizar(estado.cliente, Date.now(), function (etapa) { estado.etapa = etapa; avisar(); })
      .then(function (r) {
        registrar('Sincronizacao: ' + JSON.stringify(r.resumo));
        // O catalogo novo vale na hora, mesmo que a TV nao consiga guardar (vai baixar de novo na proxima abertura)
        estado.indice = indexar(r.catalogo);
        return gravarCatalogo(r.catalogo).then(function (gravadas) {
          registrar('Listas guardadas: ' + (gravadas.length ? gravadas.join(', ') : 'nada mudou'));
        }, function (e) {
          registrar('Falha ao guardar as listas na TV: ' + (e && (e.name + ' ' + e.message)));
          estado.erro = 'Listas atualizadas, mas a TV não conseguiu guardá-las';
        }).then(espacoUsado).then(function (espaco) {
          var mb = function (b) { return (b / 1048576).toFixed(1) + ' MB'; };
          if (espaco) registrar('Armazenamento: ' + mb(espaco.usado) + ' usados de ' + mb(espaco.cota));
        });
      })
      .catch(function (e) {
        registrar('Falha na sincronizacao: ' + (e && (e.name + ' ' + e.message)));
        estado.erro = e instanceof ErroServidor ? e.message : 'Não foi possível atualizar as listas (' + (e && (e.name || e.message)) + ')';
      })
      .then(function () { estado.sincronizando = false; estado.etapa = null; avisar(); });
  }
  return passo.then(function () {
    if (!estado.indice) return null;
    return medirVelocidadeSePreciso(estado.cliente, estado.indice.catalogo.filmes, forcar).then(function (bps) {
      if (bps) { estado.velocidadeBps = bps; registrar('Velocidade: ' + Math.round(bps / 1000) + ' kbps'); avisar(); }
    });
  });
}

export function entrar(servidor, usuario, senha) {
  estado.login = preferencias.salvarLogin(servidor, usuario, senha);
  estado.cliente = new ClienteXtream(estado.login);
  guia.cache = {};
  registrar('Login salvo para ' + estado.login.servidor);
  return sincronizarSePreciso(true);
}

// ---- Favoritos: canal pela linha (grupo FHD/HD/SD), filme e serie pelo id ----

export function chaveFavorito(tipo, item) {
  return tipo === 'canal' ? 'canal:' + item.grupo : tipo + ':' + item.id;
}

export function ehFavorito(tipo, item) {
  return !!estado.favoritos[chaveFavorito(tipo, item)];
}

export function alternarFavorito(tipo, item) {
  var chave = chaveFavorito(tipo, item);
  if (estado.favoritos[chave]) {
    delete estado.favoritos[chave];
    lojaFavoritos.apagar(chave);
  } else {
    var registro = { chave: chave, tipo: tipo, idConteudo: item.id, grupo: item.grupo || null, nome: item.nomeBase || item.nome, capa: item.capa || item.logo || null, em: Date.now() };
    estado.favoritos[chave] = registro;
    lojaFavoritos.gravar(registro);
  }
  avisar();
}

export function favoritosDoTipo(tipo) {
  return Object.keys(estado.favoritos).map(function (k) { return estado.favoritos[k]; })
    .filter(function (f) { return f.tipo === tipo; })
    .sort(function (a, b) { return b.em - a.em; });
}

// ---- Progresso: grava a partir de 30 s; passou de 95% conta como terminado (regra do Android) ----

export var MINIMO_PARA_GUARDAR_MS = 30000;
export var FRACAO_TERMINADO = 0.95;

export function progressoDe(chave) {
  return estado.progresso[chave] || null;
}

export function gravarProgresso(registro) {
  if (registro.duracaoMs > 0 && registro.posicaoMs >= registro.duracaoMs * FRACAO_TERMINADO) {
    apagarProgresso(registro.chave);
    return;
  }
  if (registro.posicaoMs < MINIMO_PARA_GUARDAR_MS) return;
  registro.atualizadoEm = Date.now();
  estado.progresso[registro.chave] = registro;
  lojaProgresso.gravar(registro);
  avisar();
}

export function apagarProgresso(chave) {
  if (!estado.progresso[chave]) return;
  delete estado.progresso[chave];
  lojaProgresso.apagar(chave);
  avisar();
}

export function recentes(tipo, limite) {
  return Object.keys(estado.progresso).map(function (k) { return estado.progresso[k]; })
    .filter(function (p) { return p.tipo === tipo; })
    .sort(function (a, b) { return b.atualizadoEm - a.atualizadoEm; })
    .slice(0, limite);
}

// ---- Busca em memoria sobre ~30 mil nomes ja normalizados na sincronizacao ----

export function nomeNormalizado(item) {
  return item.busca || semAcento(item.nome);
}
