import { useEffect, useState } from 'react';
import { UserRound, Mail, Fingerprint, LogOut } from 'lucide-react';
import { errorMessage, getMyProfile } from '../services/api';
import { useAuth } from '../context/AuthContext';
import type { UserProfile } from '../types';
import Avatar from '../components/Avatar';

export default function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { logout } = useAuth();

  useEffect(() => {
    getMyProfile().then(setProfile).catch((e) => setError(errorMessage(e, 'Could not load your profile.'))).finally(() => setLoading(false));
  }, []);

  return (
    <div className="stack-lg">
      <div className="page-intro"><div><h2>Your profile</h2><p>Your CareerSync account information.</p></div></div>
      {loading ? <div className="skeleton skeleton-profile" />
        : error ? <div className="notice notice-error" role="alert">{error}</div>
        : profile && (
          <div className="profile-card">
            <div className="profile-banner">
              <Avatar name={profile.name || profile.email} size={64} />
              <div><h2>{profile.name || 'CareerSync user'}</h2><span>{profile.email}</span></div>
            </div>
            <div className="profile-details">
              <div className="profile-detail"><div className="detail-icon"><UserRound size={18} /></div><div><span>Full name</span><strong>{profile.name || 'Not provided'}</strong></div></div>
              <div className="profile-detail"><div className="detail-icon"><Mail size={18} /></div><div><span>Email address</span><strong>{profile.email}</strong></div></div>
              <div className="profile-detail"><div className="detail-icon"><Fingerprint size={18} /></div><div><span>Account ID</span><strong className="id-value">{profile.keycloakId}</strong></div></div>
            </div>
            <div className="profile-actions"><button className="button button-secondary" onClick={logout}><LogOut size={16} /> Sign out</button></div>
          </div>
        )}
    </div>
  );
}
