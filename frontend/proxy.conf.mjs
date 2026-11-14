// Serveur de développement Angular : relaie /api vers le backend Spring Boot.
// Le navigateur ne parle qu'à http://localhost:61748 (pas de CORS, un seul port à connaître).
// Le port du backend est lu dans le fichier .env à la racine (SERVER_PORT, 8095 par défaut).
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

function lirePortBackend() {
  if (process.env.SERVER_PORT) return process.env.SERVER_PORT.trim();
  try {
    const racine = join(dirname(fileURLToPath(import.meta.url)), '..');
    const env = readFileSync(join(racine, '.env'), 'utf8');
    const m = env.match(/^\s*SERVER_PORT\s*=\s*(\d+)\s*$/m);
    if (m) return m[1];
  } catch {
    // pas de .env : port par défaut
  }
  return '8095';
}

const cible = `http://127.0.0.1:${lirePortBackend()}`;

export default {
  '/api': {
    target: cible,
    secure: false,
    changeOrigin: false,
    logLevel: 'warn'
  }
};
