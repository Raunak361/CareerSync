export interface Company {
  id: number;
  name: string;
  url: string;
  lastCheckedTimestamp?: string | null;
}

export interface Opportunity {
  id: number;
  companyId: number;
  companyName: string;
  title: string;
  type: string;
  jobUrl: string;
  experience: number | null;
  detectedDate: string;
  isRead: boolean;
}

export interface UserProfile {
  id: number;
  email: string;
  name: string;
  keycloakId: string;
}
