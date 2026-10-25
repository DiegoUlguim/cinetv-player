// Regras do CineTV portadas do app Android (app/src/main/java/br/projetor/cinetv/dados).
// Funcoes puras: testadas em webos/test sem TV nem servidor.

/** Qualidade declarada no nome do canal ("... FHD"). nivel ordena; rotulo vai para a tela. */
export const QUALIDADES = {
  SD: { nome: 'SD', rotulo: 'SD', nivel: 1 },
  HD: { nome: 'HD', rotulo: 'HD', nivel: 2 },
  FHD: { nome: 'FHD', rotulo: 'FHD', nivel: 3 },
  UHD: { nome: 'UHD', rotulo: '4K', nivel: 4 },
};

export const PREFERENCIAS = ['AUTOMATICA', 'FHD', 'HD', 'SD'];
export const ROTULO_PREFERENCIA = { AUTOMATICA: 'Automática', FHD: 'FHD', HD: 'HD', SD: 'SD' };

// Limites medidos no Android (24/10) pelo PCR de 5 trios, com margem de 1,5x
export const LIMITE_FHD_BPS = 10000000;
export const LIMITE_HD_BPS = 6000000;

const SUFIXO = /[\s\-|]+(FHD|FULL ?HD|UHD|4K|HD|SD)\s*$/i;

/** "01 - Globo SP Capital FHD" -> { base: "01 - Globo SP Capital", qualidade: "FHD" }; sem sufixo, qualidade null. */
export function separarQualidade(nome) {
  var texto = String(nome);
  var achado = SUFIXO.exec(texto);
  if (!achado) return { base: texto.trim(), qualidade: null };
  var bruto = achado[1].toUpperCase().replace(/ /g, '');
  var qualidade = bruto === 'FHD' || bruto === 'FULLHD' ? 'FHD' : bruto === 'UHD' || bruto === '4K' ? 'UHD' : bruto === 'HD' ? 'HD' : 'SD';
  var base = texto.substring(0, achado.index).trim();
  return base ? { base: base, qualidade: qualidade } : { base: texto.trim(), qualidade: null };
}

/** Qualidade que a regra quer usar. Sem medida de velocidade, FHD: o rebaixamento desce se travar (decisao de 24/10). */
export function qualidadeAlvo(preferencia, estimativaBps) {
  if (preferencia === 'FHD' || preferencia === 'HD' || preferencia === 'SD') return preferencia;
  if (estimativaBps == null) return 'FHD';
  if (estimativaBps >= LIMITE_FHD_BPS) return 'FHD';
  if (estimativaBps >= LIMITE_HD_BPS) return 'HD';
  return 'SD';
}

function nivel(qualidade) {
  return QUALIDADES[qualidade || 'HD'].nivel;
}

/** Indice da variante a abrir: a maior que nao passa do alvo; se todas passam, a menor. Sem qualidade conta como HD. */
export function escolherVariante(disponiveis, alvo) {
  if (!disponiveis.length) return -1;
  var limite = nivel(alvo);
  var melhor = -1;
  var menor = 0;
  for (var i = 0; i < disponiveis.length; i++) {
    var n = nivel(disponiveis[i]);
    if (n <= limite && (melhor < 0 || n > nivel(disponiveis[melhor]))) melhor = i;
    if (n < nivel(disponiveis[menor])) menor = i;
  }
  return melhor >= 0 ? melhor : menor;
}

/** Proxima variante abaixo da atual, para quando a internet nao da conta; null se ja e a menor. */
export function varianteAbaixo(disponiveis, atual) {
  var nivelAtual = nivel(disponiveis[atual]);
  var escolhida = null;
  for (var i = 0; i < disponiveis.length; i++) {
    var n = nivel(disponiveis[i]);
    if (n < nivelAtual && (escolhida === null || n > nivel(disponiveis[escolhida]))) escolhida = i;
  }
  return escolhida;
}

/**
 * Agrupa as variantes de qualidade do mesmo canal (mesma categoria e mesmo nome sem a qualidade).
 * O representante e a primeira variante na ordem do servidor e herda o primeiro epgId do grupo.
 */
export function agruparCanais(canais) {
  var guiaDoGrupo = {};
  var separados = canais.map(function (canal) {
    var s = separarQualidade(canal.nome);
    var grupo = canal.categoriaId + '|' + s.base.toLowerCase();
    if (canal.epgId && !guiaDoGrupo[grupo]) guiaDoGrupo[grupo] = canal.epgId;
    return Object.assign({}, canal, { nomeBase: s.base, qualidade: s.qualidade, grupo: grupo, representante: false });
  });
  var vistos = {};
  return separados.map(function (canal) {
    if (vistos[canal.grupo]) return canal;
    vistos[canal.grupo] = true;
    return Object.assign({}, canal, { representante: true, epgId: canal.epgId || guiaDoGrupo[canal.grupo] || null });
  });
}

/** Categorias adultas ficam fora do app, inclusive da busca. */
var ADULTA = /xxx|\+ ?18|18 ?\+|adult/i;
export function ehAdulta(nomeCategoria) {
  return ADULTA.test(String(nomeCategoria));
}

// ---- Leitura tolerante: os paineis variam o tipo de um item para outro ----

/** Texto do campo, ou null se ausente, null, objeto ou vazio. Numero vira texto. */
export function texto(item, chave) {
  var v = item ? item[chave] : null;
  if (v === null || v === undefined || typeof v === 'object') return null;
  var t = String(v).trim();
  return t ? t : null;
}

export function inteiro(item, chave) {
  var t = texto(item, chave);
  if (t === null) return null;
  var n = Number(t);
  return isFinite(n) ? Math.trunc(n) : null;
}

export function decimal(item, chave) {
  var t = texto(item, chave);
  if (t === null) return null;
  var n = Number(t.replace(',', '.'));
  return isFinite(n) ? n : null;
}

/** Primeiro texto de um campo que pode vir como lista ou como texto (ex.: backdrop_path). */
export function primeiroTexto(item, chave) {
  var v = item ? item[chave] : null;
  if (Array.isArray(v)) {
    for (var i = 0; i < v.length; i++) {
      if (v[i] !== null && typeof v[i] !== 'object' && String(v[i]).trim()) return String(v[i]).trim();
    }
    return null;
  }
  return texto(item, chave);
}

function ehObjeto(v) {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

export function mapearCategoria(item, ordem) {
  if (!ehObjeto(item)) return null;
  var id = texto(item, 'category_id');
  var nome = texto(item, 'category_name');
  return id && nome ? { id: id, nome: nome, ordem: ordem } : null;
}

export function mapearCanal(item, ordem) {
  if (!ehObjeto(item)) return null;
  var id = inteiro(item, 'stream_id');
  var nome = texto(item, 'name');
  if (id === null || !nome) return null;
  return { id: id, nome: nome, categoriaId: texto(item, 'category_id') || '', logo: texto(item, 'stream_icon'), epgId: texto(item, 'epg_channel_id'), ordem: ordem };
}

function nota(item) {
  var n = decimal(item, 'rating_5based');
  return n && n > 0 ? n : null;
}

function ano(item) {
  var a = texto(item, 'year');
  if (a) return a;
  var data = texto(item, 'release_date');
  return data ? data.substring(0, 4) : null;
}

export function mapearFilme(item, ordem) {
  if (!ehObjeto(item)) return null;
  var id = inteiro(item, 'stream_id');
  var nome = texto(item, 'name') || texto(item, 'title');
  if (id === null || !nome) return null;
  return {
    id: id, nome: nome, categoriaId: texto(item, 'category_id') || '',
    capa: texto(item, 'stream_icon') || texto(item, 'logo'), nota: nota(item), ano: ano(item),
    extensao: texto(item, 'container_extension') || 'mp4', adicionadoEm: inteiro(item, 'added'), ordem: ordem,
  };
}

export function mapearSerie(item, ordem) {
  if (!ehObjeto(item)) return null;
  var id = inteiro(item, 'series_id');
  var nome = texto(item, 'name') || texto(item, 'title');
  if (id === null || !nome) return null;
  return {
    id: id, nome: nome, categoriaId: texto(item, 'category_id') || '',
    capa: texto(item, 'cover') || texto(item, 'logo'), nota: nota(item), ano: ano(item), ordem: ordem,
  };
}

export function mapearDetalheFilme(info) {
  info = ehObjeto(info) ? info : {};
  return {
    sinopse: texto(info, 'plot') || texto(info, 'description'),
    duracaoSegundos: inteiro(info, 'duration_secs'),
    genero: texto(info, 'genre'),
    elenco: texto(info, 'cast') || texto(info, 'actors'),
    fundo: primeiroTexto(info, 'backdrop_path'),
    capaGrande: texto(info, 'cover_big') || texto(info, 'movie_image'),
    lancamento: texto(info, 'releasedate') || texto(info, 'release_date'),
  };
}

/** get_series_info: "info" e "episodes" agrupado por temporada ("1": [...]). Temporadas em ordem, so as com episodio. */
export function lerSerie(raiz) {
  var info = ehObjeto(raiz && raiz.info) ? raiz.info : null;
  var agrupados = ehObjeto(raiz && raiz.episodes) ? raiz.episodes : {};
  var episodios = {};
  Object.keys(agrupados).forEach(function (chave) {
    var numeroTemporada = Number(chave);
    if (!isFinite(numeroTemporada) || chave.trim() === '') return;
    var lista = Array.isArray(agrupados[chave]) ? agrupados[chave] : [];
    var daTemporada = [];
    lista.forEach(function (item) {
      if (!ehObjeto(item)) return;
      var id = inteiro(item, 'id');
      if (id === null) return;
      var detalhe = ehObjeto(item.info) ? item.info : null;
      var numero = inteiro(item, 'episode_num') || 0;
      daTemporada.push({
        id: id,
        temporada: inteiro(item, 'season') || numeroTemporada,
        numero: numero,
        titulo: texto(item, 'title') || ('Episódio ' + (numero || '')).trim(),
        extensao: texto(item, 'container_extension') || 'mp4',
        duracaoSegundos: detalhe ? inteiro(detalhe, 'duration_secs') : null,
        sinopse: detalhe ? texto(detalhe, 'plot') : null,
      });
    });
    daTemporada.sort(function (a, b) { return a.numero - b.numero; });
    if (daTemporada.length) episodios[numeroTemporada] = daTemporada;
  });
  var temporadas = Object.keys(episodios).map(Number).sort(function (a, b) { return a - b; });
  return {
    sinopse: info ? texto(info, 'plot') : null,
    genero: info ? texto(info, 'genre') : null,
    elenco: info ? texto(info, 'cast') : null,
    fundo: info ? (primeiroTexto(info, 'backdrop_path') || texto(info, 'cover')) : null,
    temporadas: temporadas,
    episodios: episodios,
  };
}

/** Proximo episodio na ordem da serie (mesma temporada ou primeiro da seguinte). */
export function proximoEpisodio(serie, episodioId) {
  var todos = [];
  serie.temporadas.forEach(function (t) { todos = todos.concat(serie.episodios[t]); });
  for (var i = 0; i < todos.length; i++) if (todos[i].id === episodioId) return todos[i + 1] || null;
  return null;
}

export function acharEpisodio(serie, id) {
  for (var i = 0; i < serie.temporadas.length; i++) {
    var lista = serie.episodios[serie.temporadas[i]];
    for (var j = 0; j < lista.length; j++) if (lista[j].id === id) return lista[j];
  }
  return null;
}

// ---- Busca: cada palavra e prefixo, todas obrigatorias, sem acento ----

export function semAcento(t) {
  return String(t).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
}

/** Palavras da consulta (sem acento, sem pontuacao); [] quando nao sobra nada. */
export function palavrasDaBusca(textoDigitado) {
  return semAcento(textoDigitado).split(/[^a-z0-9À-ɏ]+/).filter(function (p) { return p; });
}

/** O nome bate se cada palavra da consulta e prefixo de alguma palavra do nome. */
export function bateBusca(nomeNormalizado, palavras) {
  if (!palavras.length) return false;
  var doNome = nomeNormalizado.split(/[^a-z0-9À-ɏ]+/);
  return palavras.every(function (p) {
    return doNome.some(function (n) { return n.indexOf(p) === 0; });
  });
}

// ---- Servidor e log ----

/** Aceita "host", "host:porta" ou URL completa; devolve "esquema://host[:porta]". Sem esquema, https. */
export function normalizarServidor(bruto) {
  var t = String(bruto).trim().replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(t)) t = 'https://' + t;
  var inicioCaminho = t.indexOf('/', t.indexOf('://') + 3);
  return inicioCaminho > 0 ? t.substring(0, inicioCaminho) : t;
}

/** Tira usuario e senha de um texto (as URLs do Xtream levam os dois no caminho). */
export function mascarar(textoLivre, segredos) {
  var t = String(textoLivre);
  (segredos || []).forEach(function (s) {
    if (s && s.length >= 3) t = t.split(s).join('***');
    if (s && s.length >= 3) t = t.split(encodeURIComponent(s)).join('***');
  });
  return t;
}

/** Dias inteiros ate a data em epoch (s); negativo se ja venceu. */
export function diasAte(epochSegundos, agoraMs) {
  return Math.trunc((epochSegundos * 1000 - agoraMs) / 86400000);
}

/** Titulos do guia vem em base64 (UTF-8); se nao for base64 valido, usa o texto como veio. */
export function decodificarTitulo(bruto, atobFn) {
  try {
    var binario = (atobFn || atob)(bruto);
    var bytes = [];
    for (var i = 0; i < binario.length; i++) bytes.push('%' + ('0' + binario.charCodeAt(i).toString(16)).slice(-2));
    var t = decodeURIComponent(bytes.join('')).trim();
    return t || bruto;
  } catch (e) {
    return bruto;
  }
}

/** Programa atual e seguinte do get_short_epg; null quando o servidor diz que nao ha guia ([] ou sem epg_listings). */
export function lerGuia(raiz, agoraMs, atobFn) {
  if (!ehObjeto(raiz)) return null;
  var lista = Array.isArray(raiz.epg_listings) ? raiz.epg_listings : [];
  var programas = [];
  lista.forEach(function (item) {
    if (!ehObjeto(item)) return;
    var inicio = inteiro(item, 'start_timestamp');
    var fim = inteiro(item, 'stop_timestamp');
    var titulo = texto(item, 'title');
    if (inicio === null || fim === null || !titulo) return;
    if (fim * 1000 <= agoraMs) return;
    programas.push({ titulo: decodificarTitulo(titulo, atobFn), inicio: inicio * 1000, fim: fim * 1000 });
  });
  programas.sort(function (a, b) { return a.inicio - b.inicio; });
  var agora = null;
  for (var i = 0; i < programas.length; i++) if (programas[i].inicio <= agoraMs) { agora = programas[i]; break; }
  var seguinte = null;
  for (var j = 0; j < programas.length; j++) {
    if (programas[j] !== agora && programas[j].inicio >= (agora ? agora.fim : agoraMs)) { seguinte = programas[j]; break; }
  }
  // Grade curta para a previa do Ao vivo: o programa atual (ou o proximo) e os seguintes, ate 4
  var desde = agora ? programas.indexOf(agora) : 0;
  return { agora: agora, seguinte: seguinte, proximos: programas.slice(desde, desde + 4) };
}
