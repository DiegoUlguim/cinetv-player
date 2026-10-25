// Detalhe de filme e de serie. Regras do Android (AtividadeDetalheSerie.kt): a serie abre na temporada de onde
// parou; a direita no ultimo botao vai ao episodio de retomar; a esquerda volta ao botao principal; subir do
// primeiro episodio vai a temporada atual; fim de episodio oferece o proximo em 10 s.
import { useEffect, useRef, useState } from 'preact/hooks';
import { Botao, ItemLinha, ListaVertical, Fileira, tempo } from '../componentes.jsx';
import { alternarFavorito, ehFavorito, estado, progressoDe, recentes, registrar } from '../estado.js';
import { acharEpisodio, lerSerie, mapearDetalheFilme, proximoEpisodio } from '../dados/regras.js';

var detalhesFilme = {};
var detalhesSerie = {};

function carregarFilme(id) {
  if (detalhesFilme[id]) return Promise.resolve(detalhesFilme[id]);
  return estado.cliente.json('get_vod_info', { vod_id: id }).then(function (raiz) {
    detalhesFilme[id] = mapearDetalheFilme(raiz && raiz.info);
    return detalhesFilme[id];
  });
}

function carregarSerie(id) {
  if (detalhesSerie[id]) return Promise.resolve(detalhesSerie[id]);
  return estado.cliente.json('get_series_info', { series_id: id }).then(function (raiz) {
    detalhesSerie[id] = lerSerie(raiz);
    return detalhesSerie[id];
  });
}

function Fundo(props) {
  return (
    <div class="fundo-detalhe">
      {props.src ? <img src={props.src} alt="" /> : null}
      <div class="escurecer" />
    </div>
  );
}

export function DetalheFilme(props) {
  var filme = props.filme;
  var [detalhe, setDetalhe] = useState(detalhesFilme[filme.id] || null);
  var [indice, setIndice] = useState(0);
  var progresso = progressoDe('filme:' + filme.id);

  useEffect(function () {
    carregarFilme(filme.id).then(setDetalhe, function (e) { registrar('Detalhe do filme indisponivel: ' + e.message); });
  }, [filme.id]);

  var botoes = progresso
    ? [{ rotulo: 'Continuar de ' + tempo(progresso.posicaoMs), acao: function () { props.aoAssistir(filme, progresso.posicaoMs); } },
      { rotulo: 'Do início', acao: function () { props.aoAssistir(filme, 0); }, secundario: true }]
    : [{ rotulo: 'Assistir', acao: function () { props.aoAssistir(filme, 0); } }];
  botoes.push({ rotulo: ehFavorito('filme', filme) ? '✓ Na minha lista' : '+ Minha lista', acao: function () { alternarFavorito('filme', filme); }, secundario: true });
  var i = Math.min(indice, botoes.length - 1);

  props.teclas.atual = function (acao) {
    if (acao === 'esq') { setIndice(Math.max(0, i - 1)); return true; }
    if (acao === 'dir') { setIndice(Math.min(botoes.length - 1, i + 1)); return true; }
    if (acao === 'ok') { botoes[i].acao(); return true; }
    if (acao === 'voltar') { props.aoFechar(); return true; }
    return acao === 'cima' || acao === 'baixo';
  };

  var d = detalhe || {};
  var duracao = d.duracaoSegundos > 0 ? (Math.floor(d.duracaoSegundos / 3600) > 0 ? Math.floor(d.duracaoSegundos / 3600) + 'h ' : '') + Math.floor((d.duracaoSegundos % 3600) / 60) + 'min' : null;
  var linha = [(d.lancamento || '').substring(0, 4) || filme.ano, duracao, filme.nota ? '★ ' + filme.nota.toFixed(1).replace('.', ',') : null, d.genero]
    .filter(Boolean).join('  ·  ');

  return (
    <div class="detalhe">
      <Fundo src={d.fundo || d.capaGrande || filme.capa} />
      <div class="coluna-detalhe">
        <div class="titulo-detalhe">{filme.nome}</div>
        {linha ? <div class="linha-detalhe">{linha}</div> : null}
        {d.sinopse ? <div class="sinopse">{d.sinopse}</div> : null}
        {d.elenco ? <div class="elenco">{'Elenco: ' + d.elenco}</div> : null}
        <div class="botoes">
          {botoes.map(function (b, j) { return <Botao key={b.rotulo} focado={j === i} secundario={b.secundario}>{b.rotulo}</Botao>; })}
        </div>
      </div>
    </div>
  );
}

export function DetalheSerie(props) {
  var serie = props.serie;
  var [detalhe, setDetalhe] = useState(detalhesSerie[serie.id] || null);
  var [erro, setErro] = useState(null);
  var [temporada, setTemporada] = useState(-1);
  var [area, setArea] = useState('botoes');
  var [botao, setBotao] = useState(0);
  var [indiceTemporada, setIndiceTemporada] = useState(0);
  var ultimoEpisodio = useRef({});
  var [, tique] = useState(0);
  var teclasAviso = useRef({ atual: null }).current;

  useEffect(function () {
    carregarSerie(serie.id).then(setDetalhe, function (e) { setErro(e.message || 'O servidor não trouxe os episódios desta série'); });
  }, [serie.id]);

  // Episodio de onde parou (o mais recente desta serie) ou o primeiro
  var ultimoProgresso = recentes('episodio', 200).filter(function (p) { return p.serieId === serie.id; })[0] || null;
  var retomar = detalhe && ultimoProgresso ? acharEpisodio(detalhe, ultimoProgresso.idConteudo) : null;
  var temporadas = detalhe ? detalhe.temporadas : [];
  var primeiro = temporadas.length ? detalhe.episodios[temporadas[0]][0] : null;
  var temporadaAtual = temporadas.indexOf(temporada) >= 0 ? temporada : retomar && temporadas.indexOf(retomar.temporada) >= 0 ? retomar.temporada : temporadas[0];
  var episodios = detalhe && temporadaAtual != null ? detalhe.episodios[temporadaAtual] : [];
  var indiceEp = ultimoEpisodio.current[temporadaAtual];
  if (indiceEp == null) {
    var deRetomar = retomar ? episodios.findIndex(function (e) { return e.id === retomar.id; }) : -1;
    indiceEp = Math.max(0, deRetomar);
  }

  var botoes = [];
  if (retomar && ultimoProgresso) botoes.push({ rotulo: 'Continuar T' + retomar.temporada + ':E' + retomar.numero + ' de ' + tempo(ultimoProgresso.posicaoMs), acao: function () { props.aoAssistir(serie, retomar, ultimoProgresso.posicaoMs, detalhe); } });
  else if (primeiro) botoes.push({ rotulo: 'Assistir T' + primeiro.temporada + ':E' + primeiro.numero, acao: function () { props.aoAssistir(serie, primeiro, 0, detalhe); } });
  botoes.push({ rotulo: ehFavorito('serie', serie) ? '✓ Na minha lista' : '+ Minha lista', secundario: true, acao: function () { alternarFavorito('serie', serie); } });
  var b = Math.min(botao, botoes.length - 1);

  function moverEpisodio(i) {
    ultimoEpisodio.current[temporadaAtual] = i;
    tique(function (n) { return n + 1; });
  }

  props.teclas.atual = function (acao) {
    // Com o aviso de proximo episodio na tela, as teclas sao dele
    if (props.proximo) return teclasAviso.atual ? teclasAviso.atual(acao) : true;
    if (acao === 'voltar') { props.aoFechar(); return true; }
    if (area === 'botoes') {
      if (acao === 'esq') { setBotao(Math.max(0, b - 1)); return true; }
      if (acao === 'dir') {
        if (b < botoes.length - 1) { setBotao(b + 1); return true; }
        if (episodios.length) setArea('episodios');
        return true;
      }
      if (acao === 'ok') { botoes[b].acao(); return true; }
      return true;
    }
    if (area === 'temporadas') {
      if (acao === 'esq') {
        if (indiceTemporada === 0) { setArea('botoes'); setBotao(0); return true; }
        setIndiceTemporada(indiceTemporada - 1); setTemporada(temporadas[indiceTemporada - 1]); return true;
      }
      if (acao === 'dir') {
        var n = Math.min(temporadas.length - 1, indiceTemporada + 1);
        setIndiceTemporada(n); setTemporada(temporadas[n]); return true;
      }
      if (acao === 'baixo' || acao === 'ok') { setArea('episodios'); return true; }
      return true;
    }
    // episodios
    if (acao === 'esq') { setArea('botoes'); setBotao(0); return true; }
    if (acao === 'cima') {
      if (indiceEp === 0) {
        if (temporadas.length > 1) { setIndiceTemporada(temporadas.indexOf(temporadaAtual)); setArea('temporadas'); }
        return true;
      }
      moverEpisodio(indiceEp - 1); return true;
    }
    if (acao === 'baixo') { moverEpisodio(Math.min(episodios.length - 1, indiceEp + 1)); return true; }
    if (acao === 'ok' && episodios[indiceEp]) {
      var ep = episodios[indiceEp];
      var p = progressoDe('episodio:' + ep.id);
      props.aoAssistir(serie, ep, p ? p.posicaoMs : 0, detalhe);
      return true;
    }
    return true;
  };

  var d = detalhe || {};
  var linha = [serie.ano, serie.nota ? '★ ' + serie.nota.toFixed(1).replace('.', ',') : null, d.genero].filter(Boolean).join('  ·  ');

  return (
    <div class="detalhe">
      <Fundo src={d.fundo || serie.capa} />
      <div class="coluna-detalhe estreita">
        <div class="titulo-detalhe">{serie.nome}</div>
        {linha ? <div class="linha-detalhe">{linha}</div> : null}
        {d.sinopse ? <div class="sinopse">{d.sinopse}</div> : null}
        {erro ? <div class="erro">{erro}</div> : null}
        <div class="botoes">
          {botoes.map(function (bt, j) { return <Botao key={bt.rotulo} focado={props.ativa && area === 'botoes' && j === b} secundario={bt.secundario}>{bt.rotulo}</Botao>; })}
        </div>
      </div>
      <div class="coluna-episodios">
        {temporadas.length > 1
          ? <Fileira itens={temporadas} indice={area === 'temporadas' ? indiceTemporada : temporadas.indexOf(temporadaAtual)} focada={props.ativa && area === 'temporadas'} largura={11} altura={3.6} visiveis={5}
              render={function (t, j, focado) { return <ItemLinha focado={focado} selecionado={t === temporadaAtual}><span class="texto">{'Temporada ' + t}</span></ItemLinha>; }} />
          : null}
        <ListaVertical
          key={temporadaAtual} itens={episodios} indice={indiceEp} visiveis={10} altura={4.4} focada={props.ativa && area === 'episodios'}
          chave={function (e) { return e.id; }}
          render={function (e, j, focado) {
            var p = progressoDe('episodio:' + e.id);
            var extra = [e.duracaoSegundos > 0 ? Math.round(e.duracaoSegundos / 60) + ' min' : null, p ? 'parou em ' + tempo(p.posicaoMs) : null].filter(Boolean).join('  ·  ');
            return (
              <ItemLinha focado={focado}>
                <span class="numero-ep">{'E' + e.numero}</span>
                <span class="textos"><span class="texto">{e.titulo}</span>{extra ? <span class="subtitulo">{extra}</span> : null}</span>
              </ItemLinha>
            );
          }}
        />
      </div>
      {props.proximo ? <AvisoProximo episodio={props.proximo} teclas={teclasAviso} aoAssistir={function () { props.aoAssistir(serie, props.proximo, 0, detalhe); }} aoCancelar={props.aoCancelarProximo} /> : null}
    </div>
  );
}

/** "Proximo episodio em 10 s", com Assistir agora e Cancelar. */
function AvisoProximo(props) {
  var [restantes, setRestantes] = useState(10);
  var [i, setI] = useState(0);
  useEffect(function () {
    if (restantes <= 0) { props.aoAssistir(); return undefined; }
    var t = setTimeout(function () { setRestantes(restantes - 1); }, 1000);
    return function () { clearTimeout(t); };
  }, [restantes]);
  props.teclas.atual = function (acao) {
    if (acao === 'esq' || acao === 'dir') { setI(i === 0 ? 1 : 0); return true; }
    if (acao === 'ok') { if (i === 0) props.aoAssistir(); else props.aoCancelar(); return true; }
    if (acao === 'voltar') { props.aoCancelar(); return true; }
    return true;
  };
  var ep = props.episodio;
  return (
    <div class="aviso-proximo">
      <div class="rotulo-aviso">{'Próximo episódio em ' + restantes + ' s'}</div>
      <div class="titulo-aviso">{'T' + ep.temporada + ':E' + ep.numero + '  ' + ep.titulo}</div>
      <div class="botoes"><Botao focado={i === 0}>Assistir agora</Botao><Botao focado={i === 1} secundario>Cancelar</Botao></div>
    </div>
  );
}

export { proximoEpisodio };
