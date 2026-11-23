import axios from 'axios';
import keycloak from './keycloak';
import type { Company, Opportunity, UserProfile } from '../types';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
});

// Refresh the token if it expires within 30s, then attach it.
api.interceptors.request.use(async (config) => {
  if (keycloak?.authenticated) {
    try { await keycloak.updateToken(30); } catch { keycloak.login(); }
    if (keycloak.token) config.headers.Authorization = `Bearer ${keycloak.token}`;
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error?.response?.status === 401 && keycloak?.authenticated) keycloak.login();
    return Promise.reject(error);
  },
);

export function errorMessage(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } }; message?: string };
  if (err?.response?.data?.message) return err.response.data.message;
  if (err?.message === 'Network Error') return 'Cannot reach the server. Check that the backend is running and CORS allows this origin.';
  return fallback;
}

export const getMyCompanies = async () => (await api.get<Company[]>('/companies')).data;
export const addCompanies = async (companyIds: number[]) =>
  (await api.post<Company[]>('/companies', { companyIds })).data;
export const deleteCompany = async (id: number) => api.delete(`/companies/${id}`);
export const getOpportunities = async () => (await api.get<Opportunity[]>('/opportunities')).data;
export const getMyProfile = async () => (await api.get<UserProfile>('/users/me')).data;

const availableCompaniesPath = import.meta.env.VITE_AVAILABLE_COMPANIES_PATH || '/companies/available';
export const getAvailableCompanies = async () => (await api.get<Company[]>(availableCompaniesPath)).data;

export default api;
