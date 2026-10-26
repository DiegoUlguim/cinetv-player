// Ajustes: situacao da conta, atualizar listas, qualidade dos canais e login.
import { useEffect, useRef, useState } from 'preact/hooks';
import { Botao } from '../componentes.jsx';
import { entrar, estado, sincronizarSePreciso } from '../estado.js';
import { preferencias } from '../dados/preferencias.js';
import { diasAte, PREFERENCIAS, qualidadeAlvo, QUALIDADES, ROTULO_PREFERENCIA } from '../dados/regras.js';

function data(ms) {
  var d = new Date(ms);
  var dois = function (n) { return (n < 10 ? '0' : '') + n; };
  return dois(d.getDate()) + '/' + dois(d.getMonth() + 1) + '/' + d.getFullYear() + ' ' + dois(d.getHours()) + ':' + dois(d.getMinutes());
}

export function Ajustes(props) {
  var login = estado.login || { servidor: '', usuario: '', senha: '' };
  var [campos, setCampos] = useState({ servidor: login.servidor, usuario: login.usuario, senha: login.senha });
  var [qualidade, setQualidade] = useState(preferencias.qualidade());
  var [previa, setPrevia] = useState(preferencias.previaAoVivo());
  var [destaque, setDestaque] = useState(preferencias.destaque());
  var [animacoes, setAnimacoes] = useState(preferencias.animacoes());
  // Sem login, o foco comeca no servidor
  var itens = ['atualizar', 'qualidade', 'previa', 'destaque', 'animacoes', 'servidor', 'usuario', 'senha', 'salvar'];
  var [indice, setIndice] = useState(estado.login ? 0 : 5);
  var refs = { servidor: useRef(null), usuario: useRef(null), senha: useRef(null) };
  // Login que chegou depois (ares-launch -p ou outra tela): os campos acompanham
  var loginSalvo = estado.login ? estado.login.servidor + '|' + estado.login.usuario : '';
  useEffect(function () {
    if (estado.login) setCampos({ servidor: estado.login.servidor, usuario: estado.login.usuario, senha: estado.login.senha });
  }, [loginSalvo]);
  var item = itens[indice];

  useEffect(function () {
    var r = refs[item];
    var el = r && r.current;
    if (props.focado && el) {
      el.focus();
      try { el.setSelectionRange(el.value.length, el.value.length); } catch (e) { /* senha */ }
    } else {
      // So os campos daqui: a Busca tambem tem um, e as abas ficam todas montadas
      ['servidor', 'usuario', 'senha'].forEach(function (n) {
        if (refs[n].current && document.activeElement === refs[n].current) refs[n].current.blur();
      });
    }
    // O conteudo passa da altura da tela: rola ate o item em foco
    var focadoEl = document.querySelector('.ajustes .focado');
    if (props.focado && focadoEl && focadoEl.scrollIntoView) focadoEl.scrollIntoView(false);
  }, [props.focado, indice]);

  props.teclas.atual = function (acao) {
    if (acao === 'cima') { if (indice === 0) return 'subir'; setIndice(indice - 1); return true; }
    if (acao === 'baixo') { setIndice(Math.min(itens.length - 1, indice + 1)); return true; }
    if (acao !== 'ok') return false;
    if (item === 'atualizar' && estado.cliente) { sincronizarSePreciso(true); return true; }
    if (item === 'qualidade') {
      var nova = PREFERENCIAS[(PREFERENCIAS.indexOf(qualidade) + 1) % PREFERENCIAS.length];
      preferencias.salvarQualidade(nova);
      setQualidade(nova);
      return true;
    }
    if (item === 'previa') { preferencias.salvarPreviaAoVivo(!previa); setPrevia(!previa); return true; }
    if (item === 'destaque') { preferencias.salvarDestaque(!destaque); setDestaque(!destaque); return true; }
    if (item === 'animacoes') {
      preferencias.salvarAnimacoes(!animacoes);
      setAnimacoes(!animacoes);
      document.documentElement.className = !animacoes ? 'animado' : '';
      return true;
    }
    if (item === 'salvar' && campos.servidor.trim() && campos.usuario.trim() && campos.senha.trim()) {
      entrar(campos.servidor, campos.usuario, campos.senha);
      setIndice(0);
      return true;
    }
    return false;
  };

  var meta = estado.indice ? estado.indice.catalogo.meta : null;
  var situacao = [];
  if (meta && meta.status) situacao.push(meta.status === 'Active' ? 'Ativa' : meta.status);
  if (meta && meta.expiraEm) situacao.push('válida até ' + data(meta.expiraEm * 1000).split(' ')[0] + ' (' + diasAte(meta.expiraEm, Date.now()) + ' dias)');
  var medida = estado.velocidadeBps ? (estado.velocidadeBps / 1000000).toFixed(1).replace('.', ',') + ' Mbps' : 'ainda não medida';
  var alvo = QUALIDADES[qualidadeAlvo(qualidade, estado.velocidadeBps)].rotulo;

  function campo(nome, rotulo, tipo) {
    var focado = props.focado && item === nome;
    return (
      <div class="campo-ajuste">
        <div class="rotulo">{rotulo}</div>
        <input
          ref={refs[nome]} class={'campo' + (focado ? ' focado' : '')} type={tipo} value={campos[nome]}
          onInput={function (e) { var c = Object.assign({}, campos); c[nome] = e.target.value; setCampos(c); }}
        />
      </div>
    );
  }

  return (
    <div class="ajustes">
      <div class="secao">Conta</div>
      <div class="info"><div class="rotulo">Situação</div><div>{situacao.join(' · ') || 'sem informação ainda'}</div></div>
      <div class="info"><div class="rotulo">Última atualização das listas</div><div>{meta ? data(meta.ultima) : 'nunca'}</div></div>
      <Botao focado={props.focado && item === 'atualizar'}>{estado.sincronizando ? 'Atualizando…' : 'Atualizar listas agora'}</Botao>

      <div class="secao">Qualidade dos canais</div>
      <div class="info">
        <div class="rotulo">Velocidade da internet (medida ao abrir o app, a cada 6 h)</div>
        <div>{qualidade === 'AUTOMATICA' ? medida + ' → usa ' + alvo : medida}</div>
      </div>
      <Botao focado={props.focado && item === 'qualidade'}>{'Qualidade: ' + ROTULO_PREFERENCIA[qualidade]}</Botao>
      <div class="nota">Automática: FHD a partir de 10 Mbps, HD a partir de 6 Mbps, SD abaixo disso. Se travar, desce sozinha.</div>

      <div class="secao">Ao vivo</div>
      <Botao focado={props.focado && item === 'previa'}>{'Prévia do canal com som: ' + (previa ? 'Ligada' : 'Desligada')}</Botao>
      <div class="nota">Toca o canal em foco ao lado da lista, com a programação embaixo. Desligue se a TV ficar lenta.</div>

      <div class="secao">Filmes e séries</div>
      <Botao focado={props.focado && item === 'destaque'}>{'Prévia do título com som: ' + (destaque ? 'Ligada' : 'Desligada')}</Botao>
      <div class="nota">Parado num título por 2 s, toca o começo do filme (ou do primeiro episódio da série) no card. Desligue se a TV ficar lenta.</div>
      <Botao focado={props.focado && item === 'animacoes'}>{'Animações: ' + (animacoes ? 'Ligadas' : 'Desligadas')}</Botao>
      <div class="nota">A fileira desliza e o card em foco cresce. Desligadas, a troca é instantânea (mais leve para a TV).</div>

      <div class="secao">Login do servidor</div>
      {campo('servidor', 'Servidor (ex.: http://servidor.com:80)', 'text')}
      {campo('usuario', 'Usuário', 'text')}
      {campo('senha', 'Senha', 'password')}
      <Botao focado={props.focado && item === 'salvar'} secundario>Salvar login e atualizar</Botao>
    </div>
  );
}
