// Développement local :
//   Angular (frontend)      : http://localhost:61748 - relaie /api vers le backend (proxy.conf.mjs)
//   Spring Boot (API)       : http://localhost:8095  (SERVER_PORT dans .env)
//   FastAPI (NLP, interne)  : http://127.0.0.1:8000 - appelé uniquement par le backend
// Les appels sont relatifs ("/api/...") : aucun port codé en dur dans le frontend.
export const environment = {
  apiBaseUrl: ''
};
