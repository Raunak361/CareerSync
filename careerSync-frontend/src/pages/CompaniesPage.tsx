import { useCallback, useEffect, useMemo, useState } from 'react';
import { Building2, Plus, Search, Trash2, RefreshCw, AlertCircle, CheckCircle2, ExternalLink } from 'lucide-react';
import { addCompanies, deleteCompany, errorMessage, getAvailableCompanies, getMyCompanies } from '../services/api';
import type { Company } from '../types';
import Avatar from '../components/Avatar';
import { hostOf } from '../utils';

export default function CompaniesPage() {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [available, setAvailable] = useState<Company[]>([]);
  const [catalogueError, setCatalogueError] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const followedIds = useMemo(() => new Set(companies.map((c) => c.id)), [companies]);
  const addable = useMemo(() => available.filter((c) => !followedIds.has(c.id)), [available, followedIds]);
  const filtered = companies.filter((c) => c.name.toLowerCase().includes(search.toLowerCase()));

  // Load the two lists independently so a missing catalogue endpoint never hides your companies.
  const load = useCallback(async () => {
    setLoading(true); setError(''); setCatalogueError('');
    const [mine, catalogue] = await Promise.allSettled([getMyCompanies(), getAvailableCompanies()]);
    if (mine.status === 'fulfilled') setCompanies(mine.value);
    else setError(errorMessage(mine.reason, 'Could not load your companies. Check that the backend is running and you are signed in.'));
    if (catalogue.status === 'fulfilled') setAvailable(catalogue.value);
    else {
      const status = (catalogue.reason as { response?: { status?: number } })?.response?.status;
      setCatalogueError(status === 404
        ? 'The company catalogue endpoint (GET /companies/available) was not found on the backend, so the picker is empty.'
        : errorMessage(catalogue.reason, 'Could not load the company catalogue.'));
    }
    setLoading(false);
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function handleAdd() {
    if (!selectedId) return;
    setSaving(true); setError(''); setMessage('');
    try {
      await addCompanies([Number(selectedId)]);
      setSelectedId(''); setMessage('Company added to your list.');
      await load();
    } catch (e) { setError(errorMessage(e, 'Could not add this company.')); }
    finally { setSaving(false); }
  }

  async function handleDelete(company: Company) {
    if (!window.confirm(`Remove ${company.name} from your companies?`)) return;
    setError(''); setMessage('');
    try {
      await deleteCompany(company.id);
      setCompanies((prev) => prev.filter((c) => c.id !== company.id));
      setMessage(`${company.name} was removed.`);
    } catch (e) { setError(errorMessage(e, 'Could not remove this company.')); }
  }

  return (
    <div className="stack-lg">
      <div className="page-intro">
        <div><h2>Companies you follow</h2><p>Pick the companies to monitor for new roles.</p></div>
        <div className="count-chip"><Building2 size={15} />{companies.length} followed</div>
      </div>

      <div className="panel">
        <div className="panel-heading"><div className="panel-icon"><Plus size={18} /></div><div><h3>Add a company</h3><p>Choose from the available catalogue.</p></div></div>
        <div className="add-row">
          <select className="select-control" value={selectedId} onChange={(e) => setSelectedId(e.target.value)} disabled={addable.length === 0} aria-label="Company">
            <option value="">{addable.length === 0 ? 'No companies available' : 'Choose a company…'}</option>
            {addable.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <button className="button button-primary" onClick={handleAdd} disabled={!selectedId || saving}><Plus size={17} />{saving ? 'Adding…' : 'Add company'}</button>
        </div>
        {catalogueError && <p className="helper-text">{catalogueError}</p>}
        {!catalogueError && !loading && available.length > 0 && addable.length === 0 && <p className="helper-text ok">You already follow every available company.</p>}
      </div>

      {message && <div className="notice notice-success" role="status"><CheckCircle2 size={17} /><span>{message}</span></div>}
      {error && <div className="notice notice-error" role="alert"><AlertCircle size={17} /><span>{error}</span></div>}

      <div className="list-toolbar">
        <h3>Your companies</h3>
        <div className="toolbar-actions">
          <label className="search-box"><Search size={16} /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search companies" /></label>
          <button className="icon-button bordered" onClick={() => void load()} aria-label="Refresh companies"><RefreshCw size={17} className={loading ? 'spin' : ''} /></button>
        </div>
      </div>

      {loading && companies.length === 0 ? (
        <div className="company-grid">{[0, 1, 2, 3].map((i) => <div key={i} className="skeleton skeleton-card" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="empty-state"><Building2 size={30} /><strong>{companies.length ? 'No matching companies' : 'No companies yet'}</strong><span>{companies.length ? 'Try a different search.' : 'Add a company above to start monitoring opportunities.'}</span></div>
      ) : (
        <div className="company-grid">
          {filtered.map((company) => (
            <article className="company-card" key={company.id}>
              <Avatar name={company.name} />
              <div className="company-info">
                <strong>{company.name}</strong>
                <a href={company.url} target="_blank" rel="noreferrer">{hostOf(company.url)} <ExternalLink size={12} /></a>
              </div>
              <button className="icon-button danger-button" onClick={() => void handleDelete(company)} aria-label={`Remove ${company.name}`} title="Remove company"><Trash2 size={17} /></button>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
