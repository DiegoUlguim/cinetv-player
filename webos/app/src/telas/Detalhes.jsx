// Detalhe de filme e de serie. Regras do Android (AtividadeDetalheSerie.kt): a serie abre na temporada de onde
// parou; a direita no ultimo botao vai ao episodio de retomar; a esquerda volta ao botao principal; subir do
// primeiro episodio vai a temporada atual; fim de episodio oferece o proximo em 10 s.
import { useEffect, useRef, useState } from 'preact/hooks';
import { Botao, Capa, FundoSuave, itensQueCabem, linhasQueCabem, ListaVertical, Fileira, Meta, tempo } from '../componentes.jsx';
import { alternarFavorito, ehFavorito, estado, progressoDe, recentes, registrar } from '../estado.js';
import { acharEpisodio, lerSerie, mapearDetalheFilme, proximoEpisodio } from '../dados/regras.js';

var detalhesFilme = {};
var detalhesSerie = {};

/** "1 h 58 min" ou "48 min"; null sem duracao. */
export function duracaoLegivel(segundos) {
  if (!(segundos > 0)) return null;
  var h = Math.floor(segundos / 3600);
  return (h > 0 ? h + ' h ' : '') + Math.floor((segundos % 3600) / 60) + ' min';
}

// Cache compartilhado com o destaque das fileiras (Capas): cada titulo e pedido ao servidor uma vez
export function carregarFilme(id) {
  if (detalhesFilme[id]) return Promise.resolve(detalhesFilme[id]);
  return estado.cliente.json('get_vod_info', { vod_id: id }).then(function (raiz) {
    detalhesFilme[id] = mapearDetalheFilme(raiz && raiz.info);
    return detalhesFilme[id];
  });
}

export function carregarSerie(id) {
  if (detalhesSerie[id]) return Promise.resolve(detalhesSerie[id]);
  return estado.cliente.json('get_series_info', { series_id: id }).then(function (raiz) {
    detalhesSerie[id] = lerSerie(raiz);
    return detalhesSerie[id];
  });
}

// Icones das acoes (traco de 24 px), como no video de referencia
var ICONES_ACAO = {
  play: 'M6 4l14 8-14 8z',
  inicio: 'M4 12a8 8 0 1 0 3-6.2M4 4v4h4',
  mais: 'M12 5v14M5 12h14',
  marcado: 'M5 12l5 5 9-10',
  episodios: 'M3 5h18M3 12h18M3 19h18',
  semelhantes: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
};

/** Acao da lista vertical do detalhe: icone e texto; em foco vira a pilula branca. */
function Acao(props) {
  var a = props.acao;
  var cheio = a.icone === 'play';
  return (
    <div class={'acao' + (props.focado ? ' focado' : '')}>
      <svg class="icone-acao" viewBox="0 0 24 24" aria-hidden="true" fill={cheio ? 'currentColor' : 'none'} stroke={cheio ? 'none' : 'currentColor'} stroke-width="2">
        <path d={ICONES_ACAO[a.icone]} />
      </svg>
      <span>{a.rotulo}</span>
    </div>
  );
}

function Fundo(props) {
  return (
    <div class="fundo-detalhe">
      {props.src ? <FundoSuave key={props.src} src={props.src} classe="" /> : null}
      <div class="escurecer" />
    </div>
  );
}

export function DetalheFilme(props) {
  var filme = props.filme;
  var [detalhe, setDetalhe] = useState(detalhesFilme[filme.id] || null);
  var [indice, setIndice] = useState(0);
  var [area, setArea] = useState('botoes');
  var [coluna, setColuna] = useState(0);
  var progresso = progressoDe('filme:' + filme.id);
  var mesma = estado.indice ? (estado.indice.filmesPorCategoria[filme.categoriaId] || []).filter(function (f) { return f.id !== filme.id; }).slice(0, 40) : [];

  useEffect(function () {
    carregarFilme(filme.id).then(setDetalhe, function (e) { registrar('Detalhe do filme indisponivel: ' + e.message); });
  }, [filme.id]);

  var botoes = progresso
    ? [{ rotulo: 'Continuar de ' + tempo(progresso.posicaoMs), icone: 'play', acao: function () { props.aoAssistir(filme, progresso.posicaoMs); } },
      { rotulo: 'Assistir desde o início', icone: 'inicio', acao: function () { props.aoAssistir(filme, 0); } }]
    : [{ rotulo: 'Assistir', icone: 'play', acao: function () { props.aoAssistir(filme, 0); } }];
  var naLista = ehFavorito('filme', filme);
  botoes.push({ rotulo: naLista ? 'Na minha lista' : 'Minha lista', icone: naLista ? 'marcado' : 'mais', acao: function () { alternarFavorito('filme', filme); } });
  if (mesma.length) botoes.push({ rotulo: 'Títulos semelhantes', icone: 'semelhantes', acao: function () { setArea('fileira'); } });
  var i = Math.min(indice, botoes.length - 1);
  var c = Math.min(coluna, Math.max(0, mesma.length - 1));

  props.teclas.atual = function (acao) {
    if (area === 'fileira') {
      if (acao === 'cima' || acao === 'voltar') { setArea('botoes'); return true; }
      if (acao === 'esq') { setColuna(Math.max(0, c - 1)); return true; }
      if (acao === 'dir') { setColuna(Math.min(mesma.length - 1, c + 1)); return true; }
      if (acao === 'ok' && mesma[c]) { props.aoAbrir(mesma[c]); return true; }
      return true;
    }
    if (acao === 'voltar') { props.aoFechar(); return true; }
    if (acao === 'cima') { setIndice(Math.max(0, i - 1)); return true; }
    if (acao === 'baixo') {
      if (i < botoes.length - 1) setIndice(i + 1);
      else if (mesma.length) setArea('fileira');
      return true;
    }
    if (acao === 'ok') { botoes[i].acao(); return true; }
    return true;
  };

  var d = detalhe || {};
  var ano = (d.lancamento || '').substring(0, 4) || filme.ano;
  var duracao = duracaoLegivel(d.duracaoSegundos);

  return (
    <div class="detalhe">
      <Fundo src={d.fundo || d.capaGrande || filme.capa} />
      <div class="coluna-detalhe">
        <div class="tipo-destaque">FILME</div>
        <div class="titulo-detalhe">{filme.nome}</div>
        <Meta nota={filme.nota} ano={ano} duracao={duracao} genero={d.genero} />
        {d.sinopse ? <div class="sinopse">{d.sinopse}</div> : null}
        {d.elenco ? <div class="elenco"><span class="rotulo-elenco">Elenco: </span>{d.elenco}</div> : null}
        {progresso && progresso.duracaoMs > 0
          ? <div class="barra-detalhe"><div class="preenchido" style={'width:' + Math.round(100 * progresso.posicaoMs / progresso.duracaoMs) + '%'} /></div>
          : null}
        <div class="acoes">{botoes.map(function (b, j) { return <Acao key={b.rotulo} acao={b} focado={props.ativa && area === 'botoes' && j === i} />; })}</div>
      </div>
      {mesma.length && area === 'fileira' ? (
        <div class="mesma-categoria">
          <div class="titulo-fileira">Da mesma categoria</div>
          <Fileira itens={mesma} indice={c} focada={props.ativa && area === 'fileira'} largura={11.8} altura={16.94} visiveis={itensQueCabem(11.8, 7.5)}
            chave={function (f) { return f.id; }}
            render={function (f, j, focado) { return <Capa src={f.capa} nome={f.nome} focado={focado} favorito={ehFavorito('filme', f)} />; }} />
        </div>
      ) : null}
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

  function abrirEpisodios() {
    if (!episodios.length) return;
    setIndiceTemporada(temporadas.indexOf(temporadaAtual));
    setArea('episodios');
  }
  var botoes = [];
  if (retomar && ultimoProgresso) {
    botoes.push({ rotulo: 'Continuar T' + retomar.temporada + ': Ep. ' + retomar.numero, icone: 'play', acao: function () { props.aoAssistir(serie, retomar, ultimoProgresso.posicaoMs, detalhe); } });
    if (primeiro) botoes.push({ rotulo: 'Assistir desde o início', icone: 'inicio', acao: function () { props.aoAssistir(serie, primeiro, 0, detalhe); } });
  } else if (primeiro) {
    botoes.push({ rotulo: 'Assistir T' + primeiro.temporada + ': Ep. ' + primeiro.numero, icone: 'play', acao: function () { props.aoAssistir(serie, primeiro, 0, detalhe); } });
  }
  if (primeiro) botoes.push({ rotulo: 'Mais episódios', icone: 'episodios', acao: abrirEpisodios });
  var naLista = ehFavorito('serie', serie);
  botoes.push({ rotulo: naLista ? 'Na minha lista' : 'Minha lista', icone: naLista ? 'marcado' : 'mais', acao: function () { alternarFavorito('serie', serie); } });
  var b = Math.min(botao, botoes.length - 1);

  function moverEpisodio(i) {
    ultimoEpisodio.current[temporadaAtual] = i;
    tique(function (n) { return n + 1; });
  }

  props.teclas.atual = function (acao) {
    // Com o aviso de proximo episodio na tela, as teclas sao dele
    if (props.proximo) return teclasAviso.atual ? teclasAviso.atual(acao) : true;
    // Lista de acoes em coluna, como no video; "Mais episodios" abre temporadas e episodios (Voltar retorna)
    if (area === 'botoes') {
      if (acao === 'voltar') { props.aoFechar(); return true; }
      if (acao === 'cima') { setBotao(Math.max(0, b - 1)); return true; }
      if (acao === 'baixo') { setBotao(Math.min(botoes.length - 1, b + 1)); return true; }
      if (acao === 'ok') { botoes[b].acao(); return true; }
      return true;
    }
    // Episodios como no print da Netflix: temporadas em coluna a esquerda, episodios a direita
    if (acao === 'voltar') { setArea('botoes'); return true; }
    if (area === 'temporadas') {
      if (acao === 'cima' || acao === 'baixo') {
        var n = Math.max(0, Math.min(temporadas.length - 1, indiceTemporada + (acao === 'baixo' ? 1 : -1)));
        setIndiceTemporada(n); setTemporada(temporadas[n]); return true;
      }
      if (acao === 'dir' || acao === 'ok') { setArea('episodios'); return true; }
      return true;
    }
    // episodios
    if (acao === 'esq') { setIndiceTemporada(temporadas.indexOf(temporadaAtual)); setArea('temporadas'); return true; }
    if (acao === 'cima') { moverEpisodio(Math.max(0, indiceEp - 1)); return true; }
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
  var naListaDeEpisodios = area !== 'botoes';
  var episodioDestaque = retomar || primeiro;

  var qtdTemporadas = temporadas.length ? temporadas.length + (temporadas.length > 1 ? ' temporadas' : ' temporada') : null;

  if (naListaDeEpisodios) {
    return (
      <div class="detalhe episodios-nf">
        <div class="coluna-temporadas">
          <div class="titulo-temporadas">{serie.nome}</div>
          <Meta ano={serie.ano} extra={qtdTemporadas} />
          <div class="lista-temporadas">
            {temporadas.map(function (t, j) {
              var focado = props.ativa && area === 'temporadas' && j === indiceTemporada;
              var n = detalhe.episodios[t].length;
              return (
                <div key={t} class={'item-temporada' + (focado ? ' focado' : t === temporadaAtual ? ' atual' : '')}>
                  <span>{'Temporada ' + t}</span><span class="qtd-episodios">{n + (n > 1 ? ' episódios' : ' episódio')}</span>
                </div>
              );
            })}
          </div>
        </div>
        <div class="coluna-episodios-nf">
          <div class="cabeca-episodios">{'Temporada ' + temporadaAtual}</div>
          <ListaVertical
            key={temporadaAtual} itens={episodios} indice={indiceEp} visiveis={linhasQueCabem(16.4, 11)} altura={16.4} focada={props.ativa && area === 'episodios'}
            chave={function (e) { return e.id; }}
            render={function (e, j, focado) {
              var p = progressoDe('episodio:' + e.id);
              var duracao = duracaoLegivel(e.duracaoSegundos);
              return (
                <div class={'episodio-nf' + (focado ? ' focado' : '')}>
                  <div class="miniatura-nf">
                    {e.imagem ? <img src={e.imagem} alt="" decoding="async" /> : null}
                    <span class="codigo-ep">{'T' + e.temporada + ':E' + e.numero}</span>
                    {p && p.duracaoMs > 0 ? <div class="barra-miniatura"><div class="preenchido" style={'width:' + Math.round(100 * p.posicaoMs / p.duracaoMs) + '%'} /></div> : null}
                  </div>
                  <div class="textos-ep">
                    <div class="titulo-ep">{e.titulo}</div>
                    {e.sinopse ? <div class="sinopse-ep">{e.sinopse}</div> : null}
                    {duracao ? <div class="duracao-ep">{'(' + duracao + ')'}</div> : null}
                  </div>
                </div>
              );
            }}
          />
        </div>
        {props.proximo ? <AvisoProximo episodio={props.proximo} teclas={teclasAviso} aoAssistir={function () { props.aoAssistir(serie, props.proximo, 0, detalhe); }} aoCancelar={props.aoCancelarProximo} /> : null}
      </div>
    );
  }

  return (
    <div class="detalhe detalhe-serie">
      <Fundo src={d.fundo || serie.capa} />
      <div class="cabecalho-serie">
        <div class="tipo-destaque">SÉRIE</div>
        <div class="titulo-detalhe">{serie.nome}</div>
        <Meta ano={serie.ano} nota={serie.nota} extra={qtdTemporadas} genero={d.genero} />
        {episodioDestaque ? <div class="episodio-destaque">{'T' + episodioDestaque.temporada + ': Ep. ' + episodioDestaque.numero + '  ' + episodioDestaque.titulo}</div> : null}
        {(episodioDestaque && episodioDestaque.sinopse) || d.sinopse ? <div class="sinopse curta">{(episodioDestaque && episodioDestaque.sinopse) || d.sinopse}</div> : null}
        {erro ? <div class="erro">{erro}</div> : null}
        <div class="acoes">{botoes.map(function (bt, j) { return <Acao key={bt.rotulo} acao={bt} focado={props.ativa && j === b} />; })}</div>
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
