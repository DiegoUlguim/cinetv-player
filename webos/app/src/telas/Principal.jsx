// Menu no topo (como o da Netflix na TV) e o conteudo da aba. Regras: subir do topo do conteudo e o Voltar levam
// ao menu; no menu, esquerda e direita trocam de aba, baixo ou OK entram; Voltar no menu fecha o app;
// cada aba fica montada (escondida) e guarda a sua posicao.
import { useState } from 'preact/hooks';
import { estado } from '../estado.js';
import { preferencias } from '../dados/preferencias.js';
import { diasAte } from '../dados/regras.js';
import { sairDoApp } from '../teclas.js';
import { Ajustes } from './Ajustes.jsx';
import { AoVivo } from './AoVivo.jsx';
import { Busca } from './Busca.jsx';
import { Capas } from './Capas.jsx';

// Icones de traco (viewBox 24), desenhados no menu
var MENU = [
  { nome: 'Buscar', icone: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4' },
  { nome: 'Início', comNome: true },
  { nome: 'Ao vivo', comNome: true },
  { nome: 'Filmes', comNome: true },
  { nome: 'Séries', comNome: true },
  { nome: 'Minha lista', comNome: true },
  // Engrenagem ("settings" do Feather Icons, MIT)
  { nome: 'Ajustes', icone: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z' },
];
var ABA_INICIO = 1;
var ABA_AJUSTES = 6;
var LEMBRADAS = ['Início', 'Ao vivo', 'Filmes', 'Séries', 'Minha lista']; // Busca e Ajustes nao contam
var AVISAR_VENCIMENTO_DIAS = 7;

var ETAPAS = { conta: 'Conferindo a conta…', canais: 'Atualizando canais…', filmes: 'Atualizando filmes…', series: 'Atualizando séries…' };

function abaInicial() {
  if (!estado.login) return ABA_AJUSTES;
  // A ultima aba e guardada pelo nome; um numero e da versao com abas no topo (outra ordem): abre no Inicio
  var i = MENU.findIndex(function (m) { return m.nome === preferencias.ultimaAba(); });
  return i < 0 ? ABA_INICIO : i;
}

export function Principal(props) {
  var [aba, setAba] = useState(abaInicial);
  var [area, setArea] = useState('conteudo');
  var [portas] = useState(function () { return MENU.map(function () { return { atual: null }; }); });

  function trocarAba(nova) {
    setAba(nova);
    if (LEMBRADAS.indexOf(MENU[nova].nome) >= 0) preferencias.salvarUltimaAba(MENU[nova].nome);
  }

  props.teclas.atual = function (acao) {
    if (area === 'menu') {
      if (acao === 'esq') { if (aba > 0) trocarAba(aba - 1); return true; }
      if (acao === 'dir') { if (aba < MENU.length - 1) trocarAba(aba + 1); return true; }
      if (acao === 'baixo' || acao === 'ok') { setArea('conteudo'); return true; }
      if (acao === 'voltar') { sairDoApp(); return true; }
      return true;
    }
    if (acao === 'voltar') { setArea('menu'); return true; }
    var porta = portas[aba].atual;
    var r = porta ? porta(acao) : false;
    if (r === 'subir') { setArea('menu'); return true; }
    return r;
  };

  var meta = estado.indice ? estado.indice.catalogo.meta : null;
  var dias = meta && meta.expiraEm ? diasAte(meta.expiraEm, Date.now()) : null;
  var aviso = !estado.login ? 'Configure o login em Ajustes'
    : estado.sincronizando ? (ETAPAS[estado.etapa] || 'Atualizando listas…')
    : estado.erro ? estado.erro
    : dias !== null && dias < 0 ? 'A conta venceu'
    : dias !== null && dias <= AVISAR_VENCIMENTO_DIAS ? 'A conta vence em ' + dias + ' dias'
    : '';

  var focado = function (i) { return props.ativa && area === 'conteudo' && aba === i; };
  var abrir = props.aoAbrir;
  var abrirItem = function (tipo, item) { abrir[tipo](item); };
  var conteudos = [
    <Busca teclas={portas[0]} focado={focado(0)} aoAbrir={function (tipo, item) { abrir[tipo === 'canal' ? 'canalUnico' : tipo](item); }} />,
    <Capas teclas={portas[1]} focado={focado(1)} tipo="inicio" aoAbrir={abrirItem} />,
    <AoVivo teclas={portas[2]} focado={focado(2)} aoAbrirCanal={abrir.canal} />,
    <Capas teclas={portas[3]} focado={focado(3)} tipo="filme" aoAbrir={abrirItem} />,
    <Capas teclas={portas[4]} focado={focado(4)} tipo="serie" aoAbrir={abrirItem} />,
    <Capas teclas={portas[5]} focado={focado(5)} tipo="lista" aoAbrir={abrirItem} />,
    <Ajustes teclas={portas[6]} focado={focado(6)} />,
  ];
  var menuAberto = props.ativa && area === 'menu';

  return (
    <div class="principal">
      {conteudos.map(function (c, i) {
        return <div key={i} class="conteudo" style={i === aba ? '' : 'display:none'}>{c}</div>;
      })}
      <div class="menu-topo">
        {MENU.map(function (m, i) {
          var classe = 'item-menu' + (m.icone && !m.comNome ? ' so-icone' : '') + (i === aba ? ' atual' : '') + (menuAberto && i === aba ? ' focado' : '');
          return (
            <div key={m.nome} class={classe}>
              {m.comNome ? <span>{m.nome}</span> : <svg class="icone-menu" viewBox="0 0 24 24" aria-hidden="true"><path d={m.icone} /></svg>}
            </div>
          );
        })}
        <div class="espaco-menu" />
        {aviso ? <div class="aviso">{aviso}</div> : null}
        <div class="marca">CineTV</div>
      </div>
    </div>
  );
}
