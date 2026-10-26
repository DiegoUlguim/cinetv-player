// Gera webos/construido/: o que vai para a TV (HTML, appinfo, icones e o app compilado para o Chrome 53 do webOS 4.5).
import { build } from 'esbuild';
import { cpSync, mkdirSync, rmSync } from 'node:fs';

var producao = process.argv.indexOf('--dev') < 0;
rmSync('construido', { recursive: true, force: true });
mkdirSync('construido', { recursive: true });
cpSync('app/estatico', 'construido', { recursive: true });
// Letra Inter (OFL) dentro do pacote: a TV nao a tem e o app nao depende da internet para desenhar o texto
mkdirSync('construido/fontes');
[400, 500, 700, 800].forEach(function (peso) {
  cpSync('node_modules/@fontsource/inter/files/inter-latin-' + peso + '-normal.woff2', 'construido/fontes/inter-' + peso + '.woff2');
});
await build({
  entryPoints: ['app/src/main.jsx'],
  bundle: true,
  outfile: 'construido/app.js',
  target: 'chrome53',
  jsx: 'automatic',
  jsxImportSource: 'preact',
  minify: producao,
  sourcemap: !producao,
  logLevel: 'warning',
});
console.log('construido/ pronto' + (producao ? '' : ' (dev, sem minificar)'));
