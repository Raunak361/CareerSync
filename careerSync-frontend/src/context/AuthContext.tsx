import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import keycloak, { initKeycloak, keycloakConfigured } from '../services/keycloak';

interface AuthContextValue {
  ready: boolean;
  authenticated: boolean;
  initError: boolean;
  userName: string;
  login: () => void;
  register: () => void;
  logout: () => void;
}
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [initError, setInitError] = useState(false);

  useEffect(() => {
    let active = true;
    if (!keycloakConfigured) { setReady(true); return; }
    initKeycloak()
      .then((ok) => { if (active) { setAuthenticated(ok); setReady(true); } })
      .catch((error) => {
        console.error('Keycloak initialization failed', error);
        if (active) { setInitError(true); setReady(true); }
      });
    return () => { active = false; };
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    ready, authenticated, initError,
    userName: (keycloak?.tokenParsed?.name as string) || (keycloak?.tokenParsed?.preferred_username as string) || '',
    login: () => void keycloak?.login({ redirectUri: window.location.origin }),
    register: () => void keycloak?.register({ redirectUri: window.location.origin }),
    logout: () => void keycloak?.logout({ redirectUri: window.location.origin }),
  }), [ready, authenticated, initError]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
