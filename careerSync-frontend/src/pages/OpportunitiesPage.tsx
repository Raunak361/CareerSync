import { useCallback, useEffect, useMemo, useState } from 'react';
import { BriefcaseBusiness, ExternalLink, Search, RefreshCw, AlertCircle, Clock } from 'lucide-react';
import { errorMessage, getOpportunities } from '../services/api';
import type { Opportunity } from '../types';
import Avatar from '../components/Avatar';
import { timeAgo } from '../utils';

const EXPERIENCE_OPTIONS = [
  { value: 'all', label: 'All experience' },
  { value: '0', label: 'Fresher' },
  { value: '1', label: '1 year' },
  { value: '2', label: '2 years' },
  { value: '3', label: '3 years' },
  { value: '4', label: '4 years' },
  { value: '5', label: '5 years' },
  { value: 'unknown', label: 'Not specified' },
];

export default function OpportunitiesPage() {
  const [items, setItems] = useState<Opportunity[]>([]);
  const [experience, setExperience] = useState('all');
  const [company, setCompany] = useState('all');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setItems(await getOpportunities()); }
    catch (e) { setError(errorMessage(e, 'Could not load opportunities. Check your backend and login session.')); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const companyNames = useMemo(() => Array.from(new Set(items.map((i) => i.companyName).filter(Boolean))).sort(), [items]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items
      .filter((job) => {
        const expMatch = experience === 'all' || (experience === 'unknown' ? job.experience == null : job.experience === Number(experience));
        const companyMatch = company === 'all' || job.companyName === company;
        const text = `${job.title} ${job.companyName} ${job.type}`.toLowerCase();
        return expMatch && companyMatch && text.includes(q);
      })
      .sort((a, b) => new Date(b.detectedDate).getTime() - new Date(a.detectedDate).getTime());
  }, [items, experience, company, search]);

  const unread = items.filter((i) => !i.isRead).length;

  return (
    <div className="stack-lg">
      <div className="page-intro">
        <div><h2>Latest opportunities</h2><p>Roles discovered from the companies you follow.</p></div>
        <div className="chip-group">
          {unread > 0 && <div className="count-chip count-chip-accent">{unread} new</div>}
          <div className="count-chip"><BriefcaseBusiness size={15} />{filtered.length} shown</div>
        </div>
      </div>

      <div className="filter-panel">
        <label className="search-box search-grow"><Search size={16} /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search title, company or type" /></label>
        <select className="select-control" value={company} onChange={(e) => setCompany(e.target.value)} aria-label="Company">
          <option value="all">All companies</option>
          {companyNames.map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
        <select className="select-control" value={experience} onChange={(e) => setExperience(e.target.value)} aria-label="Experience">
          {EXPERIENCE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <button className="icon-button bordered" onClick={() => void load()} aria-label="Refresh opportunities"><RefreshCw size={17} className={loading ? 'spin' : ''} /></button>
      </div>

      {error && <div className="notice notice-error" role="alert"><AlertCircle size={17} /><span>{error}</span></div>}

      {loading && items.length === 0 ? (
        <div className="opportunity-list">{[0, 1, 2, 3].map((i) => <div key={i} className="skeleton skeleton-row" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="empty-state"><BriefcaseBusiness size={30} /><strong>No opportunities found</strong><span>{items.length ? 'Try changing your filters.' : 'Follow some companies and new roles will show up here.'}</span></div>
      ) : (
        <div className="opportunity-list">
          {filtered.map((job) => (
            <article className="opportunity-card" key={job.id}>
              <Avatar name={job.companyName} />
              <div className="job-main">
                <div className="job-title-row">
                  <h3>{job.title}</h3>
                  {!job.isRead && <span className="badge badge-new">New</span>}
                  {job.type && <span className="badge">{job.type}</span>}
                </div>
                <div className="job-company">{job.companyName}</div>
                <div className="job-meta">
                  <span>{job.experience == null ? 'Experience not specified' : job.experience === 0 ? 'Fresher' : `${job.experience}+ years`}</span>
                  <span className="meta-dot" />
                  <span className="meta-time"><Clock size={12} />{timeAgo(job.detectedDate)}</span>
                </div>
              </div>
              <a className="button button-outline apply-link" href={job.jobUrl} target="_blank" rel="noreferrer">View job <ExternalLink size={14} /></a>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
