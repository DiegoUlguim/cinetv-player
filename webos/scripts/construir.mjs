// Gera webos/construido/: o que vai para a TV (HTML, appinfo, icones e o app compilado para o Chrome 53 do webOS 4.5).
import { build } from 'esbuild';
import { cpSync, mkdirSync, rmSync } from 'node:fs';

var producao = process.argv.indexOf('--dev') < 0;
rmSync('construido', { recursive: true, force: true });
mkdirSync('construido', { recursive: true });
cpSync('app/estatico', 'construido', { recursive: true });
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
