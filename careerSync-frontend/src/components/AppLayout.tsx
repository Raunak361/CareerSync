import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { BriefcaseBusiness, Building2, UserRound, LogOut, Menu, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';

const links = [
  { to: '/opportunities', label: 'Opportunities', icon: BriefcaseBusiness },
  { to: '/companies', label: 'Companies', icon: Building2 },
  { to: '/profile', label: 'My profile', icon: UserRound },
];

export default function AppLayout() {
  const { logout, userName } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const current = links.find((l) => location.pathname.startsWith(l.to)) ?? links[0];

  useEffect(() => { setMenuOpen(false); }, [location.pathname]);

  return (
    <div className="app-shell">
      <aside className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <span className="brand-mark"><BriefcaseBusiness size={18} /></span>
          <span>Career<span className="brand-accent">Sync</span></span>
          <button className="icon-button mobile-close" onClick={() => setMenuOpen(false)} aria-label="Close menu"><X size={20} /></button>
        </div>
        <nav aria-label="Main">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <Icon size={18} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          {userName && <div className="sidebar-user"><span className="sidebar-user-avatar">{userName[0].toUpperCase()}</span><span className="truncate">{userName}</span></div>}
          <button className="nav-link" onClick={logout}><LogOut size={18} /><span>Sign out</span></button>
        </div>
      </aside>
      {menuOpen && <button className="mobile-scrim" onClick={() => setMenuOpen(false)} aria-label="Close navigation" />}
      <main className="main-area">
        <header className="topbar">
          <button className="icon-button mobile-menu" onClick={() => setMenuOpen(true)} aria-label="Open menu"><Menu size={20} /></button>
          <h1>{current.label}</h1>
        </header>
        <section className="page-content"><Outlet /></section>
      </main>
    </div>
  );
}
