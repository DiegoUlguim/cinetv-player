/*
 * Prova do T0 (plano cinetv-webos): o que o webOS da TV aceita antes de escrever qualquer tela.
 * Cada teste vira uma linha na tela e no console, sem usuario nem senha (mascarados).
 *
 * Login: na TV, pelo PC
 *   ares-launch br.projetor.cinetv.prova -p "servidor=http://host:porta" -p "usuario=u" -p "senha=s"
 * No navegador do PC, para testar o codigo: index.html#{"servidor":...,"usuario":...,"senha":...}
 */
(function () {
  'use strict';

  var ESPERA_VIDEO_MS = 15000;
  var ESPERA_ZAPPING_MS = 400;
  var CANAIS_TESTADOS = 12;

  var tabela = document.getElementById('resultados');
  var estado = document.getElementById('estado');
  var video = document.getElementById('video');
  var login = null;

  function mascarar(texto) {
    var t = String(texto);
    if (login) {
      [login.usuario, login.senha].forEach(function (s) { if (s && s.length >= 3) t = t.split(s).join('***'); });
    }
    return t;
  }

  function registrar(nome, situacao, detalhe) {
    var linha = tabela.insertRow();
    linha.insertCell().textContent = nome;
    var c = linha.insertCell();
    c.textContent = situacao;
    c.className = situacao === 'ok' ? 'ok' : situacao === 'falha' ? 'falha' : 'aviso';
    linha.insertCell().textContent = mascarar(detalhe || '');
    console.log('[prova] ' + nome + ' | ' + situacao + ' | ' + mascarar(detalhe || ''));
  }

  function lerLogin() {
    var bruto = null;
    try {
      if (window.PalmSystem && window.PalmSystem.launchParams) bruto = window.PalmSystem.launchParams;
    } catch (e) { /* fora do webOS */ }
    if ((!bruto || bruto === '{}') && location.hash.length > 1) bruto = decodeURIComponent(location.hash.slice(1));
    var dados = null;
    try { dados = bruto ? JSON.parse(bruto) : null; } catch (e) { dados = null; }
    if (dados && dados.servidor && dados.usuario && dados.senha) {
      localStorage.setItem('prova_login', JSON.stringify(dados));
      return { dados: dados, origem: 'parametros de abertura' };
    }
    var salvo = localStorage.getItem('prova_login');
    return salvo ? { dados: JSON.parse(salvo), origem: 'salvo da abertura anterior' } : null;
  }

  function api(acao) {
    var u = login.servidor.replace(/\/+$/, '') + '/player_api.php?username=' + encodeURIComponent(login.usuario) +
      '&password=' + encodeURIComponent(login.senha);
    return acao ? u + '&action=' + acao : u;
  }

  function urlStream(tipo, id, extensao) {
    return login.servidor.replace(/\/+$/, '') + '/' + tipo + '/' + encodeURIComponent(login.usuario) + '/' +
      encodeURIComponent(login.senha) + '/' + id + '.' + extensao;
  }

  // fetch com tempo e tamanho; TypeError = bloqueio de CORS, Cloudflare sem cabecalho ou sem rede
  function buscar(url, opcoes) {
    var inicio = performance.now();
    return fetch(url, opcoes).then(function (r) {
      return r.text().then(function (corpo) {
        return { ok: r.ok, status: r.status, tipo: r.headers.get('content-type') || '', corpo: corpo, ms: performance.now() - inicio };
      });
    });
  }

  function falhaDeRede(e) {
    return (e && e.name === 'TypeError') ? 'TypeError (CORS, desafio do Cloudflare ou rede); online=' + navigator.onLine : String(e);
  }

  function limparVideo() {
    video.pause();
    video.removeAttribute('src');
    video.load();
  }

  // Abre a URL no <video> e espera "playing" (ok), "error" ou o tempo acabar
  function abrirVideo(url) {
    return new Promise(function (resolver) {
      var inicio = performance.now();
      var fim = function (resultado) {
        video.removeEventListener('playing', aoTocar);
        video.removeEventListener('error', aoErro);
        clearTimeout(relogio);
        resolver(resultado);
      };
      var aoTocar = function () { fim({ ok: true, ms: performance.now() - inicio }); };
      var aoErro = function () { fim({ ok: false, motivo: 'erro ' + (video.error ? video.error.code : '?'), ms: performance.now() - inicio }); };
      var relogio = setTimeout(function () { fim({ ok: false, motivo: 'sem imagem em ' + ESPERA_VIDEO_MS / 1000 + ' s' }); }, ESPERA_VIDEO_MS);
      video.addEventListener('playing', aoTocar);
      video.addEventListener('error', aoErro);
      video.src = url;
      video.play().catch(function () { /* o evento de erro informa */ });
    });
  }

  function esperar(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  function mediana(valores) {
    if (!valores.length) return null;
    var v = valores.slice().sort(function (a, b) { return a - b; });
    return v[Math.floor(v.length / 2)];
  }

  async function executar() {
    var lido = lerLogin();
    registrar('Navegador', 'aviso', navigator.userAgent);
    registrar('webOS', window.PalmSystem ? 'ok' : 'aviso', window.PalmSystem ? 'PalmSystem presente' : 'fora do webOS (teste no PC)');
    if (!lido) {
      registrar('Login por parametros de abertura', 'falha', 'nenhum login recebido; use ares-launch -p');
      estado.textContent = 'Sem login.';
      return;
    }
    login = lido.dados;
    registrar('Login por parametros de abertura', 'ok', 'origem: ' + lido.origem);

    // 1. API direto da TV
    estado.textContent = 'Testando a API…';
    var conta;
    try {
      var r = await buscar(api(null));
      conta = r.ok && r.tipo.indexOf('json') >= 0 ? JSON.parse(r.corpo) : null;
      var auth = conta && conta.user_info ? conta.user_info.auth : '?';
      registrar('API: login', r.ok && auth == 1 ? 'ok' : 'falha',
        'HTTP ' + r.status + ', ' + r.tipo + ', auth=' + auth + ', ' + Math.round(r.ms) + ' ms' +
        (r.tipo.indexOf('html') >= 0 ? ' (HTML: provavel bloqueio)' : ''));
      if (!conta || auth != 1) { estado.textContent = 'A API nao respondeu como esperado.'; return; }
    } catch (e) {
      registrar('API: login', 'falha', falhaDeRede(e));
      estado.textContent = 'A API falhou direto da TV: o proximo passo e o servico JS.';
      return;
    }

    // 2. Lista grande: tempo de baixar e de interpretar
    var filmes = [];
    try {
      var rf = await buscar(api('get_vod_streams'));
      var antes = performance.now();
      filmes = JSON.parse(rf.corpo);
      registrar('API: lista de filmes', 'ok', filmes.length + ' filmes, ' + Math.round(rf.corpo.length / 1024) + ' KB, baixar ' +
        Math.round(rf.ms) + ' ms, JSON.parse ' + Math.round(performance.now() - antes) + ' ms');
    } catch (e) {
      registrar('API: lista de filmes', 'falha', falhaDeRede(e));
    }

    // 3. CDN do video (302): CORS para o teste de velocidade e para o mpegts.js
    var filme = filmes.find(function (f) { return /mp4/i.test(f.container_extension || ''); }) || filmes[0];
    if (filme) {
      try {
        var urlFilme = urlStream('movie', filme.stream_id, filme.container_extension || 'mp4');
        var rc = await buscar(urlFilme, { headers: { Range: 'bytes=0-1048575' } });
        var kbps = Math.round((rc.corpo.length * 8) / rc.ms);
        registrar('CDN: CORS (teste de velocidade)', rc.ok ? 'ok' : 'falha', 'HTTP ' + rc.status + ', ' + Math.round(rc.corpo.length / 1024) +
          ' KB em ' + Math.round(rc.ms) + ' ms (~' + kbps + ' kbps)');
      } catch (e) {
        registrar('CDN: CORS (teste de velocidade)', 'falha', falhaDeRede(e) + ' (sem CORS na CDN: sem medida de velocidade nem mpegts.js)');
      }
    }

    // 4. Canais do grupo Globos Capitais (onde o m3u8 falhou no Android)
    var canais = [];
    try {
      var categorias = JSON.parse((await buscar(api('get_live_categories'))).corpo);
      var cat = categorias.find(function (c) { return /globos? capitais/i.test(c.category_name); }) || categorias[0];
      // Uma variante por canal (o grupo traz FHD, HD e SD do mesmo canal em linhas separadas)
      var vistos = {};
      canais = JSON.parse((await buscar(api('get_live_streams') + '&category_id=' + cat.category_id)).corpo).filter(function (c) {
        var base = String(c.name).replace(/\s+(FHD|UHD|4K|HD|SD|H265)\b.*$/i, '').trim().toLowerCase();
        if (vistos[base]) return false;
        vistos[base] = true;
        return true;
      }).slice(0, CANAIS_TESTADOS);
      registrar('API: canais de teste', 'ok', canais.length + ' canais de "' + cat.category_name + '"');
    } catch (e) {
      registrar('API: canais de teste', 'falha', falhaDeRede(e));
    }

    // 5. Ao vivo em m3u8 no <video> nativo, com a espera de zapping do Android
    var tempos = [];
    var abertos = 0;
    for (var i = 0; i < canais.length; i++) {
      estado.textContent = 'm3u8: canal ' + (i + 1) + ' de ' + canais.length + '…';
      var rv = await abrirVideo(urlStream('live', canais[i].stream_id, 'm3u8'));
      if (rv.ok) { abertos++; tempos.push(rv.ms); }
      registrar('m3u8: ' + canais[i].name, rv.ok ? 'ok' : 'falha', rv.ok ? Math.round(rv.ms) + ' ms' : rv.motivo);
      limparVideo();
      await esperar(ESPERA_ZAPPING_MS);
    }
    if (canais.length) {
      registrar('m3u8: resumo', abertos === canais.length ? 'ok' : 'aviso',
        abertos + '/' + canais.length + ' abriram, mediana ' + Math.round(mediana(tempos) || 0) + ' ms (Android: 11/12, 1460 ms)');
    }

    // 6. .ts cru no <video> (esperado falhar) e pelo mpegts.js (MSE)
    if (canais.length) {
      var urlTs = urlStream('live', canais[0].stream_id, 'ts');
      var rts = await abrirVideo(urlTs);
      registrar('ts no <video> nativo', rts.ok ? 'ok' : 'aviso', rts.ok ? Math.round(rts.ms) + ' ms' : rts.motivo + ' (esperado)');
      limparVideo();
      if (window.mpegts && mpegts.isSupported()) {
        var inicioTs = performance.now();
        var tocador = mpegts.createPlayer({ type: 'mpegts', isLive: true, url: urlTs });
        tocador.attachMediaElement(video);
        tocador.load();
        var rm = await new Promise(function (resolver) {
          var relogio = setTimeout(function () { resolver({ ok: false, motivo: 'sem imagem em ' + ESPERA_VIDEO_MS / 1000 + ' s' }); }, ESPERA_VIDEO_MS);
          video.addEventListener('playing', function () { clearTimeout(relogio); resolver({ ok: true, ms: performance.now() - inicioTs }); }, { once: true });
          tocador.on(mpegts.Events.ERROR, function (tipo, detalhe) { clearTimeout(relogio); resolver({ ok: false, motivo: tipo + ' ' + detalhe }); });
          tocador.play();
        });
        registrar('ts pelo mpegts.js', rm.ok ? 'ok' : 'falha', rm.ok ? Math.round(rm.ms) + ' ms' : rm.motivo);
        tocador.destroy();
        limparVideo();
      } else {
        registrar('ts pelo mpegts.js', 'falha', 'MSE indisponivel');
      }
    }

    // 7. Filme MP4: abre? quantas faixas de audio e legenda o navegador enxerga?
    if (filme) {
      estado.textContent = 'Filme MP4…';
      var rfm = await abrirVideo(urlStream('movie', filme.stream_id, filme.container_extension || 'mp4'));
      registrar('Filme no <video>', rfm.ok ? 'ok' : 'falha', rfm.ok ? Math.round(rfm.ms) + ' ms' : rfm.motivo);
      if (rfm.ok) {
        var audios = video.audioTracks ? video.audioTracks.length : 'API ausente';
        var legendas = video.textTracks ? video.textTracks.length : 'API ausente';
        registrar('Faixas do filme', 'aviso', 'audioTracks=' + audios + ', textTracks=' + legendas + ' (comparar com o Android no mesmo filme)');
      }
      limparVideo();
    }

    // 8. IndexedDB e memoria
    registrar('IndexedDB', window.indexedDB ? 'ok' : 'falha', window.indexedDB ? 'disponivel' : 'ausente');
    if (performance.memory) {
      registrar('Memoria do JS', 'aviso', Math.round(performance.memory.usedJSHeapSize / 1048576) + ' MB usados de ' +
        Math.round(performance.memory.jsHeapSizeLimit / 1048576) + ' MB');
    }
    estado.textContent = 'Fim. Tire uma foto desta tela ou copie o console do ares-inspect (usuario e senha ja saem mascarados).';
  }

  executar().catch(function (e) { registrar('Erro inesperado', 'falha', String(e)); });
})();
