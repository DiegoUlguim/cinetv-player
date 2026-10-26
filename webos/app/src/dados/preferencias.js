// Preferencias e login no localStorage da TV (so neste aparelho), mais o teste de velocidade.
import { normalizarServidor } from './regras.js';

function ler(chave, padrao) {
  try {
    var v = localStorage.getItem('cinetv.' + chave);
    return v === null ? padrao : JSON.parse(v);
  } catch (e) {
    return padrao;
  }
}

function gravar(chave, valor) {
  try { localStorage.setItem('cinetv.' + chave, JSON.stringify(valor)); } catch (e) { /* sem espaco: segue sem guardar */ }
}

export var preferencias = {
  login: function () { return ler('login', null); },
  salvarLogin: function (servidor, usuario, senha) {
    var login = { servidor: normalizarServidor(servidor), usuario: String(usuario).trim(), senha: String(senha).trim() };
    gravar('login', login);
    return login;
  },
  qualidade: function () { return ler('qualidade', 'AUTOMATICA'); },
  salvarQualidade: function (q) { gravar('qualidade', q); },
  previaAoVivo: function () { return ler('previaAoVivo', true); },
  salvarPreviaAoVivo: function (ligada) { gravar('previaAoVivo', ligada); },
  destaque: function () { return ler('destaque', true); },
  salvarDestaque: function (ligado) { gravar('destaque', ligado); },
  animacoes: function () { return ler('animacoes', true); },
  salvarAnimacoes: function (ligadas) { gravar('animacoes', ligadas); },
  ultimaAba: function () { return ler('ultimaAba', 0); },
  salvarUltimaAba: function (a) { gravar('ultimaAba', a); },
  velocidade: function () { return ler('velocidade', null); },
  salvarVelocidade: function (bps) { gravar('velocidade', { bps: bps, em: Date.now() }); },
};

var INTERVALO_TESTE_MS = 6 * 60 * 60 * 1000;
var TAMANHO_TESTE = 4 * 1024 * 1024;

/**
 * Baixa os primeiros 4 MB de um filme (como o TesteVelocidade do Android) no maximo a cada 6 h.
 * O <video> nao informa a banda; sem esta medida a Automatica comeca em FHD.
 */
export function medirVelocidadeSePreciso(cliente, filmes, forcar) {
  var anterior = preferencias.velocidade();
  if (!forcar && anterior && Date.now() - anterior.em < INTERVALO_TESTE_MS) return Promise.resolve(anterior.bps);
  var filme = null;
  for (var i = 0; i < filmes.length && !filme; i++) if (/mp4/i.test(filmes[i].extensao)) filme = filmes[i];
  if (!filme) return Promise.resolve(anterior ? anterior.bps : null);
  var inicio = Date.now();
  return fetch(cliente.urlFilme(filme.id, filme.extensao), { headers: { Range: 'bytes=0-' + (TAMANHO_TESTE - 1) } })
    .then(function (r) { return r.ok ? r.arrayBuffer() : null; })
    .then(function (dados) {
      if (!dados) return anterior ? anterior.bps : null;
      var bps = Math.round((dados.byteLength * 8 * 1000) / Math.max(1, Date.now() - inicio));
      preferencias.salvarVelocidade(bps);
      return bps;
    }, function () { return anterior ? anterior.bps : null; });
}
