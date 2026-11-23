import { Navigate } from 'react-router-dom';
import { BriefcaseBusiness, Building2, BellRing, ShieldCheck, LoaderCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { keycloakConfigured } from '../services/keycloak';

export default function LoginPage() {
  const { ready, authenticated, initError, login, register } = useAuth();
  if (ready && authenticated) return <Navigate to="/" replace />;

  return (
    <main className="auth-page">
      <section className="auth-hero">
        <div className="auth-orbit orbit-one" /><div className="auth-orbit orbit-two" />
        <div className="auth-brand"><span className="brand-mark"><BriefcaseBusiness size={18} /></span>Career<span className="brand-accent">Sync</span></div>
        <div className="auth-message">
          <span className="pill-label">YOUR CAREER, ORGANIZED</span>
          <h1>Never miss the<br /><span>right opening.</span></h1>
          <p>Follow the companies you care about and see new roles the moment they appear.</p>
          <ul className="auth-points">
            <li><Building2 size={16} /> Follow companies in one click</li>
            <li><BellRing size={16} /> Fresh roles, newest first</li>
            <li><BriefcaseBusiness size={16} /> Filter by experience level</li>
          </ul>
        </div>
        <div className="auth-footnote"><ShieldCheck size={15} /> Secure sign-in powered by Keycloak</div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <h2>Welcome back</h2>
          <p className="muted">Sign in to continue to your workspace.</p>
          {!keycloakConfigured ? (
            <div className="notice notice-warning"><span><strong>Keycloak is not configured.</strong> Copy <code>.env.example</code> to <code>.env</code> and set the server URL, realm and client ID, then restart <code>npm run dev</code>.</span></div>
          ) : !ready ? (
            <div className="inline-loader"><LoaderCircle className="spin" size={18} /> Connecting…</div>
          ) : (
            <>
              {initError && <div className="notice notice-warning"><span>Could not reach Keycloak. Check <code>VITE_KEYCLOAK_URL</code>, that the server is running, and that <code>http://localhost:5173</code> is an allowed web origin.</span></div>}
              <button className="button button-primary button-wide" onClick={login}>Sign in</button>
              <button className="button button-secondary button-wide" onClick={register}>Create an account</button>
            </>
          )}
        </div>
      </section>
    </main>
  );
}
