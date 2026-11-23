import { Navigate, Outlet } from 'react-router-dom';
import { LoaderCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { keycloakConfigured } from '../services/keycloak';

export default function ProtectedRoute() {
  const { ready, authenticated } = useAuth();
  if (!ready) return <div className="full-loader"><LoaderCircle className="spin" /> Checking session…</div>;
  if (!keycloakConfigured) return <Navigate to="/login" replace />;
  return authenticated ? <Outlet /> : <Navigate to="/login" replace />;
}
