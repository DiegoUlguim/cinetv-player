// Abas no topo e o conteudo da aba. Regras do Android (TelaPrincipal.kt): subir do conteudo foca a aba atual;
// Voltar no conteudo sobe direto para a aba; na aba, Voltar fecha o app; cada aba guarda a sua posicao.
import { useState } from 'preact/hooks';
import { estado } from '../estado.js';
import { preferencias } from '../dados/preferencias.js';
import { diasAte } from '../dados/regras.js';
import { sairDoApp } from '../teclas.js';
import { Ajustes } from './Ajustes.jsx';
import { AoVivo } from './AoVivo.jsx';
import { Busca } from './Busca.jsx';
import { Capas } from './Capas.jsx';

var ABAS = ['Ao vivo', 'Filmes', 'Séries', 'Buscar', 'Ajustes'];
var ABA_AJUSTES = 4;
var AVISAR_VENCIMENTO_DIAS = 7;

var ETAPAS = { conta: 'Conferindo a conta…', canais: 'Atualizando canais…', filmes: 'Atualizando filmes…', series: 'Atualizando séries…' };

export function Principal(props) {
  // Abre na ultima aba usada (Busca e Ajustes nao contam); sem login, nos Ajustes
  var [aba, setAba] = useState(estado.login ? preferencias.ultimaAba() : ABA_AJUSTES);
  var [area, setArea] = useState(estado.login ? 'abas' : 'conteudo');
  // Uma "porta" de teclas por aba: as abas ficam montadas (escondidas) e guardam a posicao
  var [portas] = useState(function () { return ABAS.map(function () { return { atual: null }; }); });

  function trocarAba(nova) {
    setAba(nova);
    if (nova <= 2) preferencias.salvarUltimaAba(nova);
  }

  props.teclas.atual = function (acao) {
    if (area === 'abas') {
      if (acao === 'esq' && aba > 0) { trocarAba(aba - 1); return true; }
      if (acao === 'dir' && aba < ABAS.length - 1) { trocarAba(aba + 1); return true; }
      if (acao === 'baixo' || acao === 'ok') { setArea('conteudo'); return true; }
      if (acao === 'voltar') { sairDoApp(); return true; }
      return false;
    }
    if (acao === 'voltar') { setArea('abas'); return true; }
    var porta = portas[aba].atual;
    var r = porta ? porta(acao) : false;
    if (r === 'subir') { setArea('abas'); return true; }
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
  var conteudos = [
    <AoVivo teclas={portas[0]} focado={focado(0)} aoAbrirCanal={abrir.canal} />,
    <Capas teclas={portas[1]} focado={focado(1)} tipo="filme" aoAbrir={abrir.filme} />,
    <Capas teclas={portas[2]} focado={focado(2)} tipo="serie" aoAbrir={abrir.serie} />,
    <Busca teclas={portas[3]} focado={focado(3)} aoAbrir={function (tipo, item) { abrir[tipo === 'canal' ? 'canalUnico' : tipo](item); }} />,
    <Ajustes teclas={portas[4]} focado={focado(4)} />,
  ];

  return (
    <div class="principal">
      <div class="topo">
        <div class="abas">
          {ABAS.map(function (t, i) {
            var classe = 'aba' + (i === aba ? ' atual' : '') + (props.ativa && area === 'abas' && i === aba ? ' focada' : '');
            return <div key={t} class={classe}>{t}</div>;
          })}
        </div>
        <div class="aviso">{aviso}</div>
      </div>
      {conteudos.map(function (c, i) {
        return <div key={i} class="conteudo" style={i === aba ? '' : 'display:none'}>{c}</div>;
      })}
    </div>
  );
}
