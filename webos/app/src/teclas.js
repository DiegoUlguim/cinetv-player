// Controle da TV (e teclado do PC, para testar) traduzido em acoes.
// OK curto dispara ao soltar; OK segurado (a TV repete a tecla) dispara "segurar" uma vez, como no Android.

var MAPA = {
  37: 'esq', 38: 'cima', 39: 'dir', 40: 'baixo',
  13: 'ok',
  461: 'voltar', 8: 'voltar', 27: 'voltar', // 461 = Voltar do controle LG; Backspace/Esc no PC
  33: 'canalMais', 34: 'canalMenos', // canal +/- (PageUp/PageDown no PC)
  415: 'play', 19: 'pause', 413: 'parar', 417: 'avancar', 412: 'recuar', 10252: 'playPause',
};

var ouvinte = null;
var okPressionado = false;
var okSegurado = false;

/** Uma tela por vez recebe as acoes (a do topo da pilha). */
export function definirOuvinte(fn) {
  ouvinte = fn;
}

function emCampoDeTexto() {
  var el = document.activeElement;
  return el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA');
}

function despachar(acao, evento) {
  if (ouvinte && ouvinte(acao) !== false && evento) evento.preventDefault();
}

window.addEventListener('keydown', function (e) {
  var acao = MAPA[e.keyCode];
  if (!acao) return;
  // No campo de texto, so cima, baixo e voltar saem dele; o resto (letras, setas laterais, OK) e do campo
  if (emCampoDeTexto() && acao !== 'cima' && acao !== 'baixo' && acao !== 'voltar') return;
  if (emCampoDeTexto() && acao === 'voltar' && e.keyCode === 8) return; // Backspace apaga no PC
  if (acao === 'ok') {
    e.preventDefault();
    if (!okPressionado) { okPressionado = true; okSegurado = false; return; }
    if (e.repeat && !okSegurado) { okSegurado = true; despachar('segurar', null); }
    return;
  }
  despachar(acao, e);
});

window.addEventListener('keyup', function (e) {
  if (MAPA[e.keyCode] !== 'ok') return;
  if (emCampoDeTexto()) { okPressionado = false; return; }
  var segurou = okSegurado;
  okPressionado = false;
  okSegurado = false;
  if (!segurou) despachar('ok', e);
});

/** Sai do app: no webOS volta para a tela inicial da TV. */
export function sairDoApp() {
  try {
    if (window.PalmSystem && window.PalmSystem.platformBack) { window.PalmSystem.platformBack(); return; }
  } catch (e) { /* fora do webOS */ }
  window.close();
}
