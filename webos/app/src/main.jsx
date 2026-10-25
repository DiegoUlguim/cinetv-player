// CineTV para webOS: pilha de telas (so a do topo recebe as teclas), login por ares-launch -p e sincronizacao.
import { render } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import './estilo.css';
import { aoMudar, carregar, entrar, estado, registrar, sincronizarSePreciso } from './estado.js';
import { proximoEpisodio } from './dados/regras.js';
import { definirOuvinte } from './teclas.js';
import { DetalheFilme, DetalheSerie } from './telas/Detalhes.jsx';
import { PlayerVivo } from './telas/PlayerVivo.jsx';
import { PlayerVod } from './telas/PlayerVod.jsx';
import { Principal } from './telas/Principal.jsx';

// Tamanho da letra proporcional a tela: 16 px em 1920 de largura (a TV), e o mesmo desenho no PC
function ajustarEscala() {
  document.documentElement.style.fontSize = (window.innerWidth / 120) + 'px';
}
ajustarEscala();
window.addEventListener('resize', ajustarEscala);

/** Login pelo ares-launch -p "servidor=..." -p "usuario=..." -p "senha=..." (so com o PC pareado na TV). */
function loginDosParametros(bruto) {
  var dados = bruto;
  if (typeof dados === 'string') { try { dados = JSON.parse(dados); } catch (e) { dados = null; } }
  return dados && dados.servidor && dados.usuario && dados.senha ? dados : null;
}

function novaTela(tipo, dados) {
  return Object.assign({ tipo: tipo, teclas: { atual: null }, id: Math.random() }, dados || {});
}

function App() {
  var [, setVersao] = useState(0);
  var [pilha, setPilha] = useState([novaTela('principal')]);

  useEffect(function () { return aoMudar(setVersao); }, []);

  var topo = pilha[pilha.length - 1];
  definirOuvinte(function (acao) { return topo.teclas.atual ? topo.teclas.atual(acao) : false; });

  function empilhar(tela) { setPilha(pilha.concat([tela])); }
  function desempilhar() { setPilha(pilha.slice(0, -1)); }

  var abrir = {
    canal: function (canais, posicao) { empilhar(novaTela('vivo', { canais: canais, posicao: posicao })); },
    canalUnico: function (canal) {
      var doGrupo = estado.indice.canaisPorCategoria[canal.categoriaId] || [canal];
      var i = doGrupo.findIndex(function (c) { return c.id === canal.id; });
      empilhar(novaTela('vivo', { canais: i < 0 ? [canal] : doGrupo, posicao: Math.max(0, i) }));
    },
    filme: function (filme) { empilhar(novaTela('filme', { filme: filme })); },
    serie: function (serie) { empilhar(novaTela('serie', { serie: serie, proximo: null })); },
  };

  function assistirFilme(filme, retomarEm) {
    empilhar(novaTela('vod', {
      item: { tipo: 'filme', id: filme.id, titulo: filme.nome, capa: filme.capa, extensao: filme.extensao },
      retomarEm: retomarEm,
    }));
  }

  function assistirEpisodio(serie, episodio, retomarEm, detalhe) {
    var semAviso = pilha.map(function (t) { return t.tipo === 'serie' ? Object.assign({}, t, { proximo: null }) : t; });
    setPilha(semAviso.concat([novaTela('vod', {
      item: { tipo: 'episodio', id: episodio.id, titulo: serie.nome + '  ·  T' + episodio.temporada + ':E' + episodio.numero + '  ' + episodio.titulo, capa: serie.capa, extensao: episodio.extensao, serieId: serie.id },
      retomarEm: retomarEm, detalhe: detalhe,
    })]));
  }

  function fecharVod(tela, terminou) {
    var resto = pilha.slice(0, -1);
    var serie = resto[resto.length - 1];
    // Fim de episodio: a serie oferece o proximo em 10 s
    if (terminou && tela.item.tipo === 'episodio' && serie && serie.tipo === 'serie' && tela.detalhe) {
      var proximo = proximoEpisodio(tela.detalhe, tela.item.id);
      resto = resto.slice(0, -1).concat([Object.assign({}, serie, { proximo: proximo })]);
    }
    setPilha(resto);
  }

  return (
    <div class="app">
      {pilha.map(function (tela, i) {
        var ativa = i === pilha.length - 1;
        var conteudo;
        if (tela.tipo === 'principal') conteudo = <Principal teclas={tela.teclas} ativa={ativa} aoAbrir={abrir} />;
        else if (tela.tipo === 'filme') conteudo = <DetalheFilme teclas={tela.teclas} ativa={ativa} filme={tela.filme} aoAssistir={assistirFilme} aoFechar={desempilhar} />;
        else if (tela.tipo === 'serie') {
          conteudo = (
            <DetalheSerie teclas={tela.teclas} ativa={ativa} serie={tela.serie} proximo={tela.proximo}
              aoAssistir={assistirEpisodio} aoFechar={desempilhar}
              aoCancelarProximo={function () { setPilha(pilha.map(function (t) { return t === tela ? Object.assign({}, t, { proximo: null }) : t; })); }} />
          );
        } else if (tela.tipo === 'vivo') conteudo = <PlayerVivo teclas={tela.teclas} canais={tela.canais} posicao={tela.posicao} aoFechar={desempilhar} />;
        else conteudo = <PlayerVod teclas={tela.teclas} item={tela.item} retomarEm={tela.retomarEm} aoFechar={function (t) { fecharVod(tela, t); }} />;
        // Telas de baixo ficam montadas (guardam a posicao), mas escondidas
        return <div key={tela.id} class="camada" style={ativa ? '' : 'display:none'}>{conteudo}</div>;
      })}
    </div>
  );
}

function iniciar() {
  var raiz = document.getElementById('app');
  var lerParametros = function () {
    try { return window.PalmSystem ? window.PalmSystem.launchParams : null; } catch (e) { return null; }
  };
  var login = loginDosParametros(lerParametros()) || loginDosParametros(location.hash.length > 1 ? decodeURIComponent(location.hash.slice(1)) : null);
  // App ja aberto: os parametros do ares-launch -p chegam no webOSRelaunch
  document.addEventListener('webOSRelaunch', function (e) {
    var novo = loginDosParametros(e && e.detail);
    if (novo) entrar(novo.servidor, novo.usuario, novo.senha);
  });
  render(<App />, raiz);
  carregar().then(function () {
    if (login) return entrar(login.servidor, login.usuario, login.senha);
    return sincronizarSePreciso(false);
  }).catch(function (e) { registrar('Falha ao iniciar: ' + e); });
}

iniciar();
