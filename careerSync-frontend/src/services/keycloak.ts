import Keycloak from 'keycloak-js';

const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL;
const realm = import.meta.env.VITE_KEYCLOAK_REALM;
const clientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID;

export const keycloakConfigured = Boolean(keycloakUrl && realm && clientId);

const keycloak = keycloakConfigured
  ? new Keycloak({ url: keycloakUrl!, realm: realm!, clientId: clientId! })
  : null;

// keycloak.init() may only run once per instance. React.StrictMode mounts effects
// twice in development, which used to throw — so share one promise.
let initPromise: Promise<boolean> | null = null;
export function initKeycloak(): Promise<boolean> {
  if (!keycloak) return Promise.resolve(false);
  if (!initPromise) {
    initPromise = keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
  }
  return initPromise;
}

export default keycloak;
