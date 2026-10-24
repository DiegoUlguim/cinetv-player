// Copia o mpegts.js do node_modules para o app de prova (o IPK nao enxerga node_modules)
const fs = require('fs');
const path = require('path');
fs.copyFileSync(
  path.join(__dirname, '..', 'node_modules', 'mpegts.js', 'dist', 'mpegts.js'),
  path.join(__dirname, '..', 'prova', 'mpegts.js'),
);
