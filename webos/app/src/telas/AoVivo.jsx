// Ao vivo: grupos a esquerda ("★ Favoritos" primeiro, se houver), canais do grupo a direita.
// Regras do Android (TelaAoVivo.kt): o grupo em foco so abre depois de 300 ms parado; a direita confirma na hora
// e entra no ultimo canal usado no grupo (ou no primeiro); a esquerda volta ao grupo selecionado.
// Previa (opcional em Ajustes): o canal em foco toca com som a direita, com a grade logo abaixo.
import { useEffect, useRef, useState } from 'preact/hooks';
import { hora, ItemLinha, linhasQueCabem, ListaVertical } from '../componentes.jsx';
import { alternarFavorito, estado, favoritosDoTipo, guia } from '../estado.js';
import { preferencias } from '../dados/preferencias.js';
import { escolherVariante, qualidadeAlvo } from '../dados/regras.js';

var ESPERA_TROCA_GRUPO_MS = 300;
var ESPERA_GUIA_MS = 400;
var FAVORITOS = '__favoritos__';

function grupos() {
  var lista = estado.indice ? estado.indice.categoriasAoVivo.slice() : [];
  if (favoritosDoTipo('canal').length) lista.unshift({ id: FAVORITOS, nome: '★ Favoritos' });
  return lista;
}

export function canaisDoGrupo(grupoId) {
  if (!estado.indice) return [];
  if (grupoId !== FAVORITOS) return estado.indice.canaisPorCategoria[grupoId] || [];
  // Favorito de canal e guardado pela linha (grupo FHD/HD/SD); volta o representante
  var porGrupo = {};
  estado.indice.representantes.forEach(function (c) { porGrupo[c.grupo] = c; });
  return favoritosDoTipo('canal').map(function (f) { return porGrupo[f.grupo]; }).filter(Boolean);
}

function ProgramaAtual(props) {
  var canal = props.canal;
  var [, redesenhar] = useState(0);
  var guardado = guia.emCache(canal);
  useEffect(function () {
    if (guardado !== undefined || !props.consultar || !canal.epgId) return undefined;
    var vivo = true;
    guia.obter(canal).then(function () { if (vivo) redesenhar(function (n) { return n + 1; }); });
    return function () { vivo = false; };
  }, [canal.id, props.consultar]);
  return guardado && guardado.agora ? <span class="subtitulo">{guardado.agora.titulo}</span> : null;
}

/** Fecha o fluxo do <video> (conexao unica por conta). */
function fecharVideo(v) {
  if (!v) return;
  v.pause();
  v.removeAttribute('src');
  v.load();
}

// Toca o canal recebido; sem canal (lista andando, foco fora ou app em segundo plano) a conexao fica fechada
function Previa(props) {
  var video = useRef(null);
  var [falhou, setFalhou] = useState(false);
  var [, redesenhar] = useState(0);
  var canal = props.canal;
  var [oculto, setOculto] = useState(document.hidden);
  useEffect(function () {
    var aoMudar = function () { setOculto(document.hidden); };
    document.addEventListener('visibilitychange', aoMudar);
    return function () { document.removeEventListener('visibilitychange', aoMudar); };
  }, []);
  var tocar = canal && !oculto ? canal : null;

  useEffect(function () {
    var v = video.current;
    setFalhou(false);
    if (!tocar) return undefined;
    // Mesma variante do player em tela cheia: ao apertar OK o canal abre igual
    var variantes = estado.indice.variantes[tocar.grupo] || [tocar];
    var i = escolherVariante(variantes.map(function (x) { return x.qualidade; }), qualidadeAlvo(preferencias.qualidade(), estado.velocidadeBps));
    v.src = estado.cliente.urlAoVivo((variantes[i] || tocar).id, 'ts');
    var p = v.play();
    if (p && p.catch) p.catch(function () { /* o evento error trata */ });
    return function () { fecharVideo(v); };
  }, [tocar && tocar.id]);

  useEffect(function () {
    if (!canal || !props.consultar) return undefined;
    var vivo = true;
    guia.obter(canal).then(function () { if (vivo) redesenhar(function (n) { return n + 1; }); });
    return function () { vivo = false; };
  }, [canal && canal.id, props.consultar]);

  var g = canal ? guia.emCache(canal) : null;
  var grade = g && g.proximos ? g.proximos : [];
  return (
    <div class="previa">
      <div class="quadro-previa">
        <video ref={video} class="video-previa" onError={function () { setFalhou(true); }} />
        {falhou ? <div class="aviso-previa">Prévia indisponível</div> : null}
      </div>
      <div class="guia-previa">
        {grade.length ? grade.map(function (p, i) {
          return (
            <div key={p.inicio} class={'programa-previa' + (p === g.agora ? ' agora' : '')}>
              <span class="hora-previa">{i === 0 && p === g.agora ? 'Agora' : hora(p.inicio)}</span>
              <span class="titulo-previa">{p.titulo}</span>
            </div>
          );
        }) : (canal && props.consultar && g !== undefined ? <div class="programa-previa">Sem guia para este canal</div> : null)}
      </div>
    </div>
  );
}

export function AoVivo(props) {
  var lista = grupos();
  var [indiceGrupo, setIndiceGrupo] = useState(0);
  var [selecionado, setSelecionado] = useState(null);
  var [area, setArea] = useState('grupos');
  var ultimoCanal = useRef({});
  var [indiceCanal, setIndiceCanal] = useState(0);
  var [listaParada, setListaParada] = useState(false);
  var grupoId = selecionado || (lista[0] && lista[0].id);
  var canais = canaisDoGrupo(grupoId);
  var comPrevia = preferencias.previaAoVivo();

  // So troca a lista de canais quando o foco para no grupo
  var emFoco = lista[indiceGrupo] && lista[indiceGrupo].id;
  useEffect(function () {
    if (!emFoco || emFoco === grupoId) return undefined;
    var t = setTimeout(function () { setSelecionado(emFoco); }, ESPERA_TROCA_GRUPO_MS);
    return function () { clearTimeout(t); };
  }, [emFoco]);

  // O guia so e consultado com a lista parada
  useEffect(function () {
    setListaParada(false);
    guia.cancelarPendentes();
    var t = setTimeout(function () { setListaParada(true); }, ESPERA_GUIA_MS);
    return function () { clearTimeout(t); };
  }, [indiceCanal, grupoId]);

  function entrarNosCanais() {
    var alvo = emFoco || grupoId;
    setSelecionado(alvo);
    var lista2 = canaisDoGrupo(alvo);
    if (!lista2.length) return;
    setIndiceCanal(Math.min(ultimoCanal.current[alvo] || 0, lista2.length - 1));
    setArea('canais');
  }

  props.teclas.atual = function (acao) {
    if (!lista.length) return acao === 'cima' ? 'subir' : false;
    if (area === 'grupos') {
      if (acao === 'cima') { if (indiceGrupo === 0) return 'subir'; setIndiceGrupo(indiceGrupo - 1); return true; }
      if (acao === 'baixo') { setIndiceGrupo(Math.min(lista.length - 1, indiceGrupo + 1)); return true; }
      if (acao === 'dir' || acao === 'ok') { entrarNosCanais(); return true; }
      return false;
    }
    if (acao === 'cima') { if (indiceCanal === 0) return 'subir'; mover(indiceCanal - 1); return true; }
    if (acao === 'baixo') { mover(Math.min(canais.length - 1, indiceCanal + 1)); return true; }
    if (acao === 'esq') {
      setArea('grupos');
      var i = lista.findIndex(function (g) { return g.id === grupoId; });
      setIndiceGrupo(i < 0 ? 0 : i);
      return true;
    }
    if (acao === 'ok' && canais[indiceCanal]) {
      // A previa fecha a conexao antes do player em tela cheia abrir a dele
      fecharVideo(document.querySelector('.video-previa'));
      props.aoAbrirCanal(canais, indiceCanal);
      return true;
    }
    if (acao === 'segurar' && canais[indiceCanal]) { alternarFavorito('canal', canais[indiceCanal]); return true; }
    return false;
  };

  function mover(i) {
    ultimoCanal.current[grupoId] = i;
    setIndiceCanal(i);
  }

  if (!estado.indice) return <div class="vazio">{estado.sincronizando ? 'Carregando as listas…' : 'Sem listas ainda. Configure o login em Ajustes.'}</div>;

  return (
    <div class="ao-vivo">
      <ListaVertical
        classe="grupos" itens={lista} indice={indiceGrupo} visiveis={linhasQueCabem(3.6, 7)} altura={3.6}
        focada={props.focado && area === 'grupos'} chave={function (g) { return g.id; }}
        render={function (g, i, focado) {
          return <ItemLinha focado={focado} selecionado={g.id === grupoId}><span class="texto">{g.nome}</span></ItemLinha>;
        }}
      />
      <ListaVertical
        key={grupoId} classe="canais" itens={canais} indice={area === 'canais' ? indiceCanal : 0} visiveis={linhasQueCabem(4.2, 7)} altura={4.2}
        focada={props.focado && area === 'canais'} chave={function (c) { return c.id; }}
        render={function (c, i, focado) {
          return (
            <ItemLinha focado={focado}>
              <span class="logo">{c.logo ? <img src={c.logo} alt="" decoding="async" /> : null}</span>
              <span class="textos">
                <span class="texto">{c.nomeBase}</span>
                {comPrevia ? null : <ProgramaAtual canal={c} consultar={listaParada} />}
              </span>
            </ItemLinha>
          );
        }}
      />
      {comPrevia ? (
        <Previa
          canal={props.focado && area === 'canais' && listaParada ? canais[indiceCanal] : null}
          consultar={listaParada}
        />
      ) : null}
    </div>
  );
}
