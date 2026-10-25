// Guia curto (get_short_epg), como o Guia.kt do Android: so canais com epgId, no maximo 2 consultas ao mesmo
// tempo, cache ate o programa acabar e "sem guia" (404 ou []) guardado por mais tempo, sem registrar falha.
import { lerGuia } from './regras.js';

var SEM_PROGRAMA_MS = 15 * 60 * 1000;
var SEM_GUIA_MS = 30 * 60 * 1000;
var APOS_FALHA_MS = 5 * 60 * 1000;
var SIMULTANEAS = 2;

export function RepositorioGuia(obterCliente) {
  this.obterCliente = obterCliente;
  this.cache = {};
  this.emAndamento = {};
  this.fila = [];
  this.ativas = 0;
}

RepositorioGuia.prototype.emCache = function (canal) {
  var e = this.cache[canal.id];
  return e && Date.now() < e.validoAte ? e.guia : undefined;
};

/** Guia do canal ({ agora, seguinte }) ou null. Promessa compartilhada se ja ha consulta do mesmo canal. */
RepositorioGuia.prototype.obter = function (canal) {
  var self = this;
  if (!canal || !canal.epgId) return Promise.resolve(null);
  var guardado = this.emCache(canal);
  if (guardado !== undefined) return Promise.resolve(guardado);
  if (this.emAndamento[canal.id]) return this.emAndamento[canal.id];
  var promessa = new Promise(function (resolver) {
    self.fila.push({ canal: canal, resolver: resolver });
    self.proxima();
  });
  this.emAndamento[canal.id] = promessa;
  return promessa;
};

RepositorioGuia.prototype.proxima = function () {
  var self = this;
  if (this.ativas >= SIMULTANEAS || !this.fila.length) return;
  var tarefa = this.fila.shift();
  var cliente = this.obterCliente();
  if (!cliente) { tarefa.resolver(null); return; }
  this.ativas++;
  var agora = Date.now();
  var guardar = function (guia, validoAte) {
    self.cache[tarefa.canal.id] = { guia: guia, validoAte: validoAte };
    delete self.emAndamento[tarefa.canal.id];
    self.ativas--;
    tarefa.resolver(guia);
    self.proxima();
  };
  cliente.json('get_short_epg', { stream_id: tarefa.canal.id, limit: 4 }).then(function (raiz) {
    var guia = lerGuia(raiz, Date.now());
    if (!guia) guardar(null, agora + SEM_GUIA_MS);
    else guardar(guia, guia.agora ? guia.agora.fim : agora + SEM_PROGRAMA_MS);
  }, function (erro) {
    var semGuia = erro && erro.codigoHttp === 404;
    // Falha que nao e "sem guia" vai para o log (o Android faz o mesmo): o canal fica sem guia por 5 min
    if (!semGuia && self.registrar) self.registrar('Guia do canal ' + tarefa.canal.id + ' indisponivel: ' + (erro && erro.message));
    guardar(null, agora + (semGuia ? SEM_GUIA_MS : APOS_FALHA_MS));
  });
};

/** Pedidos que ainda nao sairam (a lista andou e os canais sumiram da tela) sao descartados. */
RepositorioGuia.prototype.cancelarPendentes = function () {
  var self = this;
  this.fila.forEach(function (t) { delete self.emAndamento[t.canal.id]; t.resolver(undefined); });
  this.fila = [];
};
