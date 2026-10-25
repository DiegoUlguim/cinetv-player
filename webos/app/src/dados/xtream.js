// Cliente da API player_api.php do Xtream, como o ClienteXtream.kt do Android.
import { inteiro, texto } from './regras.js';

/** Erro ja traduzido para a mensagem que o usuario le. codigoHttp quando o servidor respondeu. */
export function ErroServidor(mensagem, codigoHttp) {
  this.message = mensagem;
  this.codigoHttp = codigoHttp == null ? null : codigoHttp;
}
ErroServidor.prototype = Object.create(Error.prototype);
ErroServidor.prototype.name = 'ErroServidor';

export var SENHA_RECUSADA = 'Usuário ou senha recusados pelo servidor';

/**
 * Traduz a resposta da API. O navegador nao distingue bloqueio (CORS, desafio do Cloudflare, redirecionamento
 * para outro site) de falta de rede: os dois chegam como TypeError; navigator.onLine separa os casos.
 */
export function classificarResposta(status, tipo) {
  tipo = tipo || '';
  var html = tipo.indexOf('html') >= 0;
  if (status >= 200 && status < 300 && tipo.indexOf('json') >= 0) return null;
  if (status === 401 || (status === 403 && !html)) return new ErroServidor(SENHA_RECUSADA, status);
  if (html || status === 403) return new ErroServidor('O servidor bloqueou o acesso. Tente de novo em alguns minutos', status);
  return new ErroServidor('O servidor respondeu com erro ' + status, status);
}

export function erroDeRede(online) {
  return new ErroServidor(online ? 'O servidor não respondeu ou bloqueou o acesso desta TV' : 'Sem conexão com a internet');
}

/** login = { servidor, usuario, senha }; buscar = fetch (trocado nos testes). */
export function ClienteXtream(login, buscar) {
  this.login = login;
  this.buscar = buscar || function (url, opcoes) { return fetch(url, opcoes); };
}

ClienteXtream.prototype.urlApi = function (acao, parametros) {
  var u = this.login.servidor + '/player_api.php?username=' + encodeURIComponent(this.login.usuario) +
    '&password=' + encodeURIComponent(this.login.senha);
  if (acao) u += '&action=' + acao;
  Object.keys(parametros || {}).forEach(function (k) { u += '&' + k + '=' + encodeURIComponent(parametros[k]); });
  return u;
};

/** JSON de uma acao. Erro de rede, HTTP ou HTML vira ErroServidor. */
ClienteXtream.prototype.json = function (acao, parametros) {
  var url = this.urlApi(acao, parametros);
  return this.buscar(url).then(function (r) {
    var erro = classificarResposta(r.status, r.headers.get('content-type'));
    if (erro) throw erro;
    return r.json();
  }, function () {
    throw erroDeRede(typeof navigator === 'undefined' ? true : navigator.onLine);
  });
};

/** Login: valida usuario e senha e devolve a situacao da conta. auth=0 e senha recusada. */
ClienteXtream.prototype.conta = function () {
  return this.json(null).then(function (raiz) {
    var usuario = raiz && raiz.user_info;
    if (!usuario || typeof usuario !== 'object' || inteiro(usuario, 'auth') === 0) throw new ErroServidor(SENHA_RECUSADA);
    return { status: texto(usuario, 'status'), expiraEm: inteiro(usuario, 'exp_date'), conexoes: inteiro(usuario, 'max_connections') };
  });
};

ClienteXtream.prototype.urlFluxo = function (tipo, id, extensao) {
  return this.login.servidor + '/' + tipo + '/' + encodeURIComponent(this.login.usuario) + '/' +
    encodeURIComponent(this.login.senha) + '/' + id + '.' + extensao;
};

ClienteXtream.prototype.urlAoVivo = function (id, extensao) { return this.urlFluxo('live', id, extensao || 'ts'); };
ClienteXtream.prototype.urlFilme = function (id, extensao) { return this.urlFluxo('movie', id, extensao); };
ClienteXtream.prototype.urlEpisodio = function (id, extensao) { return this.urlFluxo('series', id, extensao); };
