# Frontend Implementation Quick Start — Authority Alerts

**TL;DR for developers** — Copy-paste ready code snippets and API reference.

---

## API Service Module

Create a file: `src/services/authorityAlertService.ts`

```typescript
import { AuthorityAlertResponse, AuthorityAlertUpsertRequest, AlertCoverageDto } from '@/types/alerts';

const API_BASE = process.env.REACT_APP_API_URL || '/api';
const ALERTS_ENDPOINT = `${API_BASE}/authority-alerts`;

export class AuthorityAlertService {
  
  private static getHeaders() {
    const token = localStorage.getItem('authToken');
    return {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    };
  }
  
  private static async handleResponse(response: Response) {
    if (!response.ok) {
      const error = new Error('API Error');
      (error as any).status = response.status;
      
      if (response.headers.get('content-type')?.includes('application/json')) {
        (error as any).details = await response.json();
      }
      throw error;
    }
    return response.json();
  }
  
  /**
   * Create a new authority alert
   * Requires: SUPER_ADMIN or authorityAlertCurator=true
   */
  static async createAlert(request: AuthorityAlertUpsertRequest): Promise<AuthorityAlertResponse> {
    const response = await fetch(ALERTS_ENDPOINT, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify(request)
    });
    return this.handleResponse(response);
  }
  
  /**
   * Update an existing authority alert
   * Requires: SUPER_ADMIN or authorityAlertCurator=true
   */
  static async updateAlert(alertId: string, request: AuthorityAlertUpsertRequest): Promise<AuthorityAlertResponse> {
    const response = await fetch(`${ALERTS_ENDPOINT}/${alertId}`, {
      method: 'PUT',
      headers: this.getHeaders(),
      body: JSON.stringify(request)
    });
    return this.handleResponse(response);
  }
  
  /**
   * Get alerts for a specific region(s)
   * Requires: REGIONAL_ANALYST or SUPER_ADMIN
   * @param country - "United States" | "Canada" | "Mexico"
   * @param states - Array of state/province names (optional; if empty, show all states)
   */
  static async getRegionalAlerts(country: string, states: string[] = []): Promise<AuthorityAlertResponse[]> {
    const params = new URLSearchParams({ country });
    states.forEach(s => params.append('state', s));
    
    const response = await fetch(`${ALERTS_ENDPOINT}/regions?${params}`, {
      headers: this.getHeaders()
    });
    return this.handleResponse(response);
  }
  
  /**
   * Get EMERGENCY severity alerts across all regions
   * Requires: REGIONAL_ANALYST or SUPER_ADMIN
   */
  static async getEmergencyFeed(): Promise<AuthorityAlertResponse[]> {
    const response = await fetch(`${ALERTS_ENDPOINT}/emergency`, {
      headers: this.getHeaders()
    });
    return this.handleResponse(response);
  }
  
  /**
   * Get alerts relevant to a specific farm
   * Filters by farm's country/state
   * Highlights OUTBREAK and EMERGENCY types
   * Requires: farm view access
   */
  static async getFarmAlerts(farmId: string): Promise<AuthorityAlertResponse[]> {
    const response = await fetch(`${ALERTS_ENDPOINT}/farms/${farmId}`, {
      headers: this.getHeaders()
    });
    return this.handleResponse(response);
  }
  
  /**
   * Get alert coverage by country (for map rendering)
   * Requires: REGIONAL_ANALYST or SUPER_ADMIN
   */
  static async getCountryCoverage(): Promise<AlertCoverageDto[]> {
    const response = await fetch(`${ALERTS_ENDPOINT}/map/countries`, {
      headers: this.getHeaders()
    });
    return this.handleResponse(response);
  }
  
  /**
   * Get alert coverage by state within a country
   * Requires: REGIONAL_ANALYST or SUPER_ADMIN
   */
  static async getStateCoverage(country: string): Promise<AlertCoverageDto[]> {
    const response = await fetch(`${ALERTS_ENDPOINT}/map/countries/${encodeURIComponent(country)}/states`, {
      headers: this.getHeaders()
    });
    return this.handleResponse(response);
  }
}

export default AuthorityAlertService;
```

---

## Type Definitions

Create a file: `src/types/alerts.ts`

```typescript
export enum AuthorityAlertType {
  NEW_DETECTION = 'NEW_DETECTION',
  ADVISORY = 'ADVISORY',
  OUTBREAK = 'OUTBREAK',
  QUARANTINE = 'QUARANTINE',
  ERADICATION_COMPLETE = 'ERADICATION_COMPLETE',
  OTHER = 'OTHER'
}

export enum AuthorityAlertSeverity {
  ADVISORY = 'ADVISORY',
  WATCH = 'WATCH',
  WARNING = 'WARNING',
  EMERGENCY = 'EMERGENCY'
}

export enum SpeciesCode {
  // Pests
  THRIPS = 'THRIPS',
  RED_SPIDER_MITE = 'RED_SPIDER_MITE',
  WHITEFLIES = 'WHITEFLIES',
  MEALYBUGS = 'MEALYBUGS',
  CATERPILLARS = 'CATERPILLARS',
  FALSE_CODLING_MOTH = 'FALSE_CODLING_MOTH',
  PEST_OTHER = 'PEST_OTHER',
  // Diseases
  DOWNY_MILDEW = 'DOWNY_MILDEW',
  POWDERY_MILDEW = 'POWDERY_MILDEW',
  BOTRYTIS = 'BOTRYTIS',
  VERTICILLIUM = 'VERTICILLIUM',
  BACTERIAL_WILT = 'BACTERIAL_WILT',
  DISEASE_OTHER = 'DISEASE_OTHER',
  // Beneficial
  BENEFICIAL_PP = 'BENEFICIAL_PP',
  BENEFICIAL_OTHER = 'BENEFICIAL_OTHER'
}

export interface AuthorityAlertResponse {
  id: string;
  alertType: AuthorityAlertType;
  severity: AuthorityAlertSeverity;
  issuingAuthority: string;
  title: string;
  messageBody: string;
  suggestedMitigation: string;
  country: string;
  state: string | null;
  linkedSpecies: SpeciesCode | null;
  sourceUrl: string | null;
  issuedDate: string; // ISO 8601 date
  expiryDate: string | null; // ISO 8601 date
  active: boolean;
  highlighted: boolean; // OUTBREAK or EMERGENCY
  createdAt: string; // ISO 8601 datetime
  updatedAt: string; // ISO 8601 datetime
}

export interface AuthorityAlertUpsertRequest {
  alertType: AuthorityAlertType;
  severity: AuthorityAlertSeverity;
  issuingAuthority: string;
  title: string;
  messageBody: string;
  suggestedMitigation?: string;
  country: string;
  state?: string;
  linkedSpecies?: SpeciesCode;
  sourceUrl?: string;
  issuedDate: string; // ISO 8601 date
  expiryDate?: string; // ISO 8601 date
  active: boolean;
}

export interface AlertCoverageDto {
  name: string;
  activeAlertCount: number;
}

export interface RegionOption {
  country: string;
  states: string[];
}

export const SUPPORTED_REGIONS: Record<string, string[]> = {
  'United States': [
    'Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado', 'Connecticut',
    'Delaware', 'District of Columbia', 'Florida', 'Georgia', 'Hawaii', 'Idaho', 'Illinois',
    'Indiana', 'Iowa', 'Kansas', 'Kentucky', 'Louisiana', 'Maine', 'Maryland', 'Massachusetts',
    'Michigan', 'Minnesota', 'Mississippi', 'Missouri', 'Montana', 'Nebraska', 'Nevada',
    'New Hampshire', 'New Jersey', 'New Mexico', 'New York', 'North Carolina', 'North Dakota',
    'Ohio', 'Oklahoma', 'Oregon', 'Pennsylvania', 'Rhode Island', 'South Carolina', 'South Dakota',
    'Tennessee', 'Texas', 'Utah', 'Vermont', 'Virginia', 'Washington', 'West Virginia',
    'Wisconsin', 'Wyoming'
  ],
  'Canada': [
    'Alberta', 'British Columbia', 'Manitoba', 'New Brunswick', 'Newfoundland and Labrador',
    'Northwest Territories', 'Nova Scotia', 'Nunavut', 'Ontario', 'Prince Edward Island',
    'Quebec', 'Saskatchewan', 'Yukon'
  ],
  'Mexico': [
    'Aguascalientes', 'Baja California', 'Baja California Sur', 'Campeche', 'Chiapas',
    'Chihuahua', 'Coahuila', 'Colima', 'Durango', 'Guanajuato', 'Guerrero', 'Hidalgo',
    'Jalisco', 'Mexico City', 'Michoacan', 'Morelos', 'Nayarit', 'Nuevo Leon', 'Oaxaca',
    'Puebla', 'Queretaro', 'Quintana Roo', 'San Luis Potosi', 'Sinaloa', 'Sonora',
    'State of Mexico', 'Tabasco', 'Tamaulipas', 'Tlaxcala', 'Veracruz', 'Yucatan', 'Zacatecas'
  ]
};

export const DEFAULT_MITIGATIONS: Record<AuthorityAlertType, string> = {
  [AuthorityAlertType.NEW_DETECTION]: 'Increase monitoring frequency and confirm any suspicious findings immediately.',
  [AuthorityAlertType.ADVISORY]: 'Review the advisory details and align current monitoring and hygiene practices.',
  [AuthorityAlertType.OUTBREAK]: 'Inspect affected production areas urgently and isolate suspect material where feasible.',
  [AuthorityAlertType.QUARANTINE]: 'Follow quarantine restrictions exactly and pause movements that could spread the threat.',
  [AuthorityAlertType.ERADICATION_COMPLETE]: 'Resume normal operations carefully while maintaining verification monitoring.',
  [AuthorityAlertType.OTHER]: 'Review the notice and apply local authority guidance to farm operations.'
};

export const SEVERITY_COLORS: Record<AuthorityAlertSeverity, string> = {
  [AuthorityAlertSeverity.EMERGENCY]: '#dc2626',    // Red
  [AuthorityAlertSeverity.WARNING]: '#ea580c',      // Orange
  [AuthorityAlertSeverity.WATCH]: '#eab308',        // Yellow
  [AuthorityAlertSeverity.ADVISORY]: '#3b82f6'      // Blue
};
```

---

## Custom React Hooks

Create a file: `src/hooks/useAuthorityAlerts.ts`

```typescript
import { useState, useCallback, useEffect, useRef } from 'react';
import AuthorityAlertService from '@/services/authorityAlertService';
import { AuthorityAlertResponse, AlertCoverageDto } from '@/types/alerts';

export function useRegionalAlerts(country: string, states: string[]) {
  const [alerts, setAlerts] = useState<AuthorityAlertResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const fetch = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await AuthorityAlertService.getRegionalAlerts(country, states);
      setAlerts(data);
    } catch (err) {
      setError((err as any)?.details?.error || 'Failed to load alerts');
    } finally {
      setLoading(false);
    }
  }, [country, states]);
  
  useEffect(() => {
    fetch();
  }, [fetch]);
  
  return { alerts, loading, error, refetch: fetch };
}

export function useEmergencyFeed(pollIntervalMs = 45000) {
  const [alerts, setAlerts] = useState<AuthorityAlertResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  
  const fetch = useCallback(async () => {
    try {
      const data = await AuthorityAlertService.getEmergencyFeed();
      setAlerts(data);
      setError(null);
    } catch (err) {
      console.error('Emergency feed fetch failed:', err);
      setError((err as any)?.details?.error || 'Failed to load emergency alerts');
    }
  }, []);
  
  useEffect(() => {
    setLoading(true);
    fetch().finally(() => setLoading(false));
    
    intervalRef.current = setInterval(fetch, pollIntervalMs);
    
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [fetch, pollIntervalMs]);
  
  return { alerts, loading, error };
}

export function useFarmAlerts(farmId: string | null) {
  const [alerts, setAlerts] = useState<AuthorityAlertResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const fetch = useCallback(async () => {
    if (!farmId) return;
    
    setLoading(true);
    setError(null);
    try {
      const data = await AuthorityAlertService.getFarmAlerts(farmId);
      setAlerts(data);
    } catch (err) {
      console.error('Farm alerts fetch failed:', err);
      setError((err as any)?.details?.error || 'Failed to load farm alerts');
    } finally {
      setLoading(false);
    }
  }, [farmId]);
  
  useEffect(() => {
    fetch();
  }, [fetch]);
  
  return { alerts, loading, error, refetch: fetch };
}

export function useMapCoverage(country: string | null) {
  const [coverage, setCoverage] = useState<AlertCoverageDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const fetch = useCallback(async () => {
    if (!country) return;
    
    setLoading(true);
    setError(null);
    try {
      const data = await AuthorityAlertService.getStateCoverage(country);
      setCoverage(data);
    } catch (err) {
      setError((err as any)?.details?.error || 'Failed to load coverage');
    } finally {
      setLoading(false);
    }
  }, [country]);
  
  useEffect(() => {
    fetch();
  }, [fetch]);
  
  return { coverage, loading, error, refetch: fetch };
}
```

---

## Alert Formatter Utilities

Create a file: `src/utils/alertFormatters.ts`

```typescript
import { AuthorityAlertResponse, AuthorityAlertSeverity } from '@/types/alerts';

export function formatDate(dateString: string): string {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
}

export function formatDateTime(dateTimeString: string): string {
  const date = new Date(dateTimeString);
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

export function getSeverityLabel(severity: AuthorityAlertSeverity): string {
  const labels: Record<AuthorityAlertSeverity, string> = {
    [AuthorityAlertSeverity.EMERGENCY]: '🚨 Emergency',
    [AuthorityAlertSeverity.WARNING]: '⚠️ Warning',
    [AuthorityAlertSeverity.WATCH]: '👁️ Watch',
    [AuthorityAlertSeverity.ADVISORY]: 'ℹ️ Advisory'
  };
  return labels[severity];
}

export function sortAlertsByPriority(alerts: AuthorityAlertResponse[]): AuthorityAlertResponse[] {
  const severityRank: Record<AuthorityAlertSeverity, number> = {
    [AuthorityAlertSeverity.EMERGENCY]: 0,
    [AuthorityAlertSeverity.WARNING]: 1,
    [AuthorityAlertSeverity.WATCH]: 2,
    [AuthorityAlertSeverity.ADVISORY]: 3
  };
  
  return [...alerts].sort((a, b) => {
    // Highlighted (OUTBREAK/EMERGENCY) first
    if (a.highlighted !== b.highlighted) {
      return a.highlighted ? -1 : 1;
    }
    
    // Then by severity
    if (severityRank[a.severity] !== severityRank[b.severity]) {
      return severityRank[a.severity] - severityRank[b.severity];
    }
    
    // Then by issued date (newest first)
    return new Date(b.issuedDate).getTime() - new Date(a.issuedDate).getTime();
  });
}

export function isAlertExpired(alert: AuthorityAlertResponse, today = new Date()): boolean {
  if (!alert.active) return true;
  if (alert.expiryDate && new Date(alert.expiryDate) < today) return true;
  if (new Date(alert.issuedDate) > today) return true;
  return false;
}

export function getDaysUntilExpiry(alert: AuthorityAlertResponse): number | null {
  if (!alert.expiryDate) return null;
  const expiry = new Date(alert.expiryDate);
  const today = new Date();
  const diff = expiry.getTime() - today.getTime();
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}
```

---

## Component Example: Alert Card

Create a file: `src/components/AlertCard.tsx`

```typescript
import React from 'react';
import { AuthorityAlertResponse } from '@/types/alerts';
import { formatDate, getSeverityLabel } from '@/utils/alertFormatters';
import './AlertCard.css';

interface AlertCardProps {
  alert: AuthorityAlertResponse;
  onClick?: () => void;
  showState?: boolean;
}

export const AlertCard: React.FC<AlertCardProps> = ({ alert, onClick, showState = false }) => {
  const priorityClass = alert.highlighted ? 'alert-card-critical' : 'alert-card-normal';
  
  return (
    <div className={`alert-card ${priorityClass}`} onClick={onClick} role="button" tabIndex={0}>
      <div className="alert-card-header">
        <div className="alert-badges">
          <span className={`severity-badge severity-${alert.severity.toLowerCase()}`}>
            {getSeverityLabel(alert.severity)}
          </span>
          <span className={`type-badge type-${alert.alertType.toLowerCase()}`}>
            {alert.alertType.replace(/_/g, ' ')}
          </span>
          {alert.highlighted && <span className="highlight-badge">🔴 CRITICAL</span>}
        </div>
        <div className="alert-metadata">
          <span className="issuing-authority">{alert.issuingAuthority}</span>
          <span className="issued-date">{formatDate(alert.issuedDate)}</span>
        </div>
      </div>
      
      <div className="alert-card-body">
        <h3 className="alert-title">{alert.title}</h3>
        {showState && alert.state && (
          <p className="alert-state">📍 {alert.state}</p>
        )}
        <p className="alert-message">{alert.messageBody.substring(0, 150)}...</p>
      </div>
      
      <div className="alert-card-footer">
        {alert.linkedSpecies && (
          <span className="linked-species">Species: {alert.linkedSpecies}</span>
        )}
        {alert.sourceUrl && (
          <a href={alert.sourceUrl} target="_blank" rel="noopener noreferrer" className="source-link">
            View Source
          </a>
        )}
      </div>
    </div>
  );
};

export default AlertCard;
```

### AlertCard.css

```css
.alert-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  background-color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.alert-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.alert-card-critical {
  border-left: 6px solid #dc2626;
  background-color: #fef2f2;
  box-shadow: 0 2px 8px rgba(220, 38, 38, 0.15);
}

.alert-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.alert-badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.severity-badge,
.type-badge,
.highlight-badge {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.severity-emergency {
  background-color: #dc2626;
  color: white;
}
.severity-warning {
  background-color: #ea580c;
  color: white;
}
.severity-watch {
  background-color: #eab308;
  color: #000;
}
.severity-advisory {
  background-color: #3b82f6;
  color: white;
}

.alert-metadata {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
}

.alert-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.alert-message {
  margin: 0;
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
}

.alert-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  font-size: 12px;
}

.source-link {
  color: #3b82f6;
  text-decoration: none;
}
.source-link:hover {
  text-decoration: underline;
}
```

---

## Component Example: Alert Form

Create a file: `src/components/AlertForm.tsx`

```typescript
import React, { useState, useMemo } from 'react';
import {
  AuthorityAlertType,
  AuthorityAlertSeverity,
  AuthorityAlertUpsertRequest,
  AuthorityAlertResponse,
  SUPPORTED_REGIONS,
  DEFAULT_MITIGATIONS,
  SpeciesCode
} from '@/types/alerts';
import AuthorityAlertService from '@/services/authorityAlertService';

interface AlertFormProps {
  alert?: AuthorityAlertResponse; // If provided, this is an edit operation
  onSuccess: () => void;
  onCancel: () => void;
}

export const AlertForm: React.FC<AlertFormProps> = ({ alert, onSuccess, onCancel }) => {
  const isEditing = !!alert;
  
  const [formData, setFormData] = useState<AuthorityAlertUpsertRequest>({
    alertType: alert?.alertType || AuthorityAlertType.ADVISORY,
    severity: alert?.severity || AuthorityAlertSeverity.ADVISORY,
    issuingAuthority: alert?.issuingAuthority || '',
    title: alert?.title || '',
    messageBody: alert?.messageBody || '',
    suggestedMitigation: alert?.suggestedMitigation || '',
    country: alert?.country || 'United States',
    state: alert?.state || undefined,
    linkedSpecies: alert?.linkedSpecies || undefined,
    sourceUrl: alert?.sourceUrl || '',
    issuedDate: alert?.issuedDate || new Date().toISOString().split('T')[0],
    expiryDate: alert?.expiryDate || undefined,
    active: alert?.active !== undefined ? alert.active : true
  });
  
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  
  const validStates = useMemo(() => SUPPORTED_REGIONS[formData.country] || [], [formData.country]);
  
  const defaultMitigation = DEFAULT_MITIGATIONS[formData.alertType];
  const currentMitigation = formData.suggestedMitigation || defaultMitigation;
  
  const handleChange = (field: keyof AuthorityAlertUpsertRequest, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value,
      ...(field === 'country' && { state: undefined }) // Reset state when country changes
    }));
    // Clear error for this field
    if (errors[field]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[field];
        return newErrors;
      });
    }
  };
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    
    try {
      if (isEditing && alert) {
        await AuthorityAlertService.updateAlert(alert.id, formData);
      } else {
        await AuthorityAlertService.createAlert(formData);
      }
      onSuccess();
    } catch (err: any) {
      if (err.status === 400 && err.details?.details) {
        setErrors(err.details.details);
      } else {
        setErrors({ _general: err.details?.error || 'An error occurred' });
      }
    } finally {
      setSubmitting(false);
    }
  };
  
  return (
    <form onSubmit={handleSubmit} className="alert-form">
      <h2>{isEditing ? 'Edit Alert' : 'Create Alert'}</h2>
      
      {errors._general && <div className="error-banner">{errors._general}</div>}
      
      <fieldset>
        <legend>Alert Details</legend>
        
        <div className="form-group">
          <label htmlFor="alertType">Alert Type *</label>
          <select
            id="alertType"
            value={formData.alertType}
            onChange={e => handleChange('alertType', e.target.value)}
            required
          >
            {Object.values(AuthorityAlertType).map(type => (
              <option key={type} value={type}>{type.replace(/_/g, ' ')}</option>
            ))}
          </select>
          {errors.alertType && <span className="error">{errors.alertType}</span>}
        </div>
        
        <div className="form-group">
          <label htmlFor="severity">Severity *</label>
          <select
            id="severity"
            value={formData.severity}
            onChange={e => handleChange('severity', e.target.value)}
            required
          >
            {Object.values(AuthorityAlertSeverity).map(sev => (
              <option key={sev} value={sev}>{sev}</option>
            ))}
          </select>
          {errors.severity && <span className="error">{errors.severity}</span>}
        </div>
        
        <div className="form-group">
          <label htmlFor="issuingAuthority">Issuing Authority *</label>
          <input
            id="issuingAuthority"
            type="text"
            value={formData.issuingAuthority}
            onChange={e => handleChange('issuingAuthority', e.target.value)}
            placeholder="e.g., USDA-APHIS"
            required
            maxLength={255}
          />
          {errors.issuingAuthority && <span className="error">{errors.issuingAuthority}</span>}
        </div>
        
        <div className="form-group">
          <label htmlFor="title">Title *</label>
          <input
            id="title"
            type="text"
            value={formData.title}
            onChange={e => handleChange('title', e.target.value)}
            required
            maxLength={255}
          />
          {errors.title && <span className="error">{errors.title}</span>}
        </div>
        
        <div className="form-group">
          <label htmlFor="messageBody">Message Body *</label>
          <textarea
            id="messageBody"
            value={formData.messageBody}
            onChange={e => handleChange('messageBody', e.target.value)}
            required
            maxLength={4000}
            rows={6}
          />
          {errors.messageBody && <span className="error">{errors.messageBody}</span>}
        </div>
      </fieldset>
      
      <fieldset>
        <legend>Mitigation</legend>
        
        <div className="form-group">
          <label htmlFor="suggestedMitigation">Suggested Mitigation (optional)</label>
          <p className="field-hint">Leave blank to use default mitigation for this alert type</p>
          <textarea
            id="suggestedMitigation"
            value={formData.suggestedMitigation || ''}
            onChange={e => handleChange('suggestedMitigation', e.target.value)}
            maxLength={2000}
            rows={4}
          />
          
          <div className="default-mitigation-preview">
            <strong>Default Mitigation for {formData.alertType}:</strong>
            <p>{defaultMitigation}</p>
          </div>
          
          {errors.suggestedMitigation && <span className="error">{errors.suggestedMitigation}</span>}
        </div>
      </fieldset>
      
      <fieldset>
        <legend>Geographic Region</legend>
        
        <div className="form-group">
          <label htmlFor="country">Country *</label>
          <select
            id="country"
            value={formData.country}
            onChange={e => handleChange('country', e.target.value)}
            required
          >
            {Object.keys(SUPPORTED_REGIONS).map(country => (
              <option key={country} value={country}>{country}</option>
            ))}
          </select>
          {errors.country && <span className="error">{errors.country}</span>}
        </div>
        
        <div className="form-group">
          <label htmlFor="state">State / Province (optional)</label>
          <select
            id="state"
            value={formData.state || ''}
            onChange={e => handleChange('state', e.target.value || undefined)}
          >
            <option value="">Whole country</option>
            {validStates.map(state => (
              <option key={state} value={state}>{state}</option>
            ))}
          </select>
          <p className="field-hint">Leave as "Whole country" for national alerts</p>
          {errors.state && <span className="error">{errors.state}</span>}
        </div>
      </fieldset>
      
      <fieldset>
        <legend>Additional Information</legend>
        
        <div className="form-group">
          <label htmlFor="linkedSpecies">Linked Species (optional)</label>
          <select
            id="linkedSpecies"
            value={formData.linkedSpecies || ''}
            onChange={e => handleChange('linkedSpecies', e.target.value || undefined)}
          >
            <option value="">None</option>
            {Object.values(SpeciesCode).map(species => (
              <option key={species} value={species}>{species.replace(/_/g, ' ')}</option>
            ))}
          </select>
          {errors.linkedSpecies && <span className="error">{errors.linkedSpecies}</span>}
        </div>
        
        <div className="form-group">
          <label htmlFor="sourceUrl">Source URL (optional)</label>
          <input
            id="sourceUrl"
            type="url"
            value={formData.sourceUrl || ''}
            onChange={e => handleChange('sourceUrl', e.target.value)}
            maxLength={1000}
          />
          {errors.sourceUrl && <span className="error">{errors.sourceUrl}</span>}
        </div>
      </fieldset>
      
      <fieldset>
        <legend>Dates</legend>
        
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="issuedDate">Issued Date *</label>
            <input
              id="issuedDate"
              type="date"
              value={formData.issuedDate}
              onChange={e => handleChange('issuedDate', e.target.value)}
              required
            />
            {errors.issuedDate && <span className="error">{errors.issuedDate}</span>}
          </div>
          
          <div className="form-group">
            <label htmlFor="expiryDate">Expiry Date (optional)</label>
            <input
              id="expiryDate"
              type="date"
              value={formData.expiryDate || ''}
              onChange={e => handleChange('expiryDate', e.target.value || undefined)}
              min={formData.issuedDate}
            />
            <p className="field-hint">Must be after or equal to issued date</p>
            {errors.expiryDate && <span className="error">{errors.expiryDate}</span>}
          </div>
        </div>
      </fieldset>
      
      <fieldset>
        <legend>Status</legend>
        
        <div className="form-group checkbox">
          <input
            id="active"
            type="checkbox"
            checked={formData.active}
            onChange={e => handleChange('active', e.target.checked)}
          />
          <label htmlFor="active">Active</label>
          <p className="field-hint">Uncheck to deactivate this alert (can be reactivated later)</p>
        </div>
      </fieldset>
      
      <div className="form-actions">
        <button type="button" onClick={onCancel} disabled={submitting}>
          Cancel
        </button>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Saving...' : isEditing ? 'Update Alert' : 'Create Alert'}
        </button>
      </div>
    </form>
  );
};

export default AlertForm;
```

### AlertForm.css

```css
.alert-form {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.alert-form h2 {
  margin-top: 0;
  margin-bottom: 24px;
}

.error-banner {
  background-color: #fee2e2;
  color: #991b1b;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 20px;
  border-left: 4px solid #dc2626;
}

fieldset {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
}

legend {
  font-size: 16px;
  font-weight: 600;
  padding: 0 8px;
  color: #111827;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 500;
  color: #374151;
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
}

.form-group textarea {
  resize: vertical;
  font-family: monospace;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-group .error {
  display: block;
  color: #dc2626;
  font-size: 12px;
  margin-top: 4px;
}

.field-hint {
  font-size: 12px;
  color: #6b7280;
  margin: 4px 0 0 0;
}

.default-mitigation-preview {
  background-color: #f0fdf4;
  border-left: 4px solid #16a34a;
  padding: 12px;
  border-radius: 4px;
  margin-top: 12px;
  font-size: 13px;
}

.default-mitigation-preview strong {
  display: block;
  margin-bottom: 6px;
  color: #15803d;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group.checkbox {
  display: flex;
  align-items: flex-start;
}

.form-group.checkbox input {
  width: auto;
  margin-right: 8px;
  margin-top: 2px;
}

.form-group.checkbox label {
  margin-bottom: 0;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 32px;
}

.form-actions button {
  padding: 10px 20px;
  border-radius: 6px;
  border: none;
  font-weight: 500;
  cursor: pointer;
  font-size: 14px;
}

.form-actions button[type="submit"] {
  background-color: #3b82f6;
  color: white;
}

.form-actions button[type="submit"]:hover:not(:disabled) {
  background-color: #2563eb;
}

.form-actions button[type="button"] {
  background-color: #e5e7eb;
  color: #374151;
}

.form-actions button[type="button"]:hover:not(:disabled) {
  background-color: #d1d5db;
}

.form-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .alert-form {
    padding: 16px;
  }
  
  .form-row {
    grid-template-columns: 1fr;
  }
}
```

---

## Integration Checklist

- [ ] Add service module (`authorityAlertService.ts`)
- [ ] Add type definitions (`alerts.ts`)
- [ ] Add custom hooks (`useAuthorityAlerts.ts`)
- [ ] Add utility functions (`alertFormatters.ts`)
- [ ] Create Alert Card component
- [ ] Create Alert Form component
- [ ] Create Regional Analyst Dashboard page
- [ ] Create Alert Curator admin page
- [ ] Integrate farm alerts into Farm View
- [ ] Add navigation links (route guards for permissions)
- [ ] Test all API endpoints
- [ ] Test permission checks (403 errors)
- [ ] Accessibility audit (color + icons, keyboard, screen readers)
- [ ] Mobile responsiveness testing
- [ ] Error handling and toast notifications

---

## Testing Example (Vitest / Jest)

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import AuthorityAlertService from '@/services/authorityAlertService';
import { sortAlertsByPriority } from '@/utils/alertFormatters';
import { AuthorityAlertResponse, AuthorityAlertSeverity } from '@/types/alerts';

describe('AlertFormatters', () => {
  it('should sort alerts by priority (highlighted first, then severity)', () => {
    const alerts: AuthorityAlertResponse[] = [
      {
        id: '1',
        severity: AuthorityAlertSeverity.ADVISORY,
        highlighted: false,
        issuedDate: '2026-07-01',
        // ... other fields
      } as AuthorityAlertResponse,
      {
        id: '2',
        severity: AuthorityAlertSeverity.EMERGENCY,
        highlighted: false,
        issuedDate: '2026-07-02',
        // ... other fields
      } as AuthorityAlertResponse,
      {
        id: '3',
        severity: AuthorityAlertSeverity.ADVISORY,
        highlighted: true,
        issuedDate: '2026-06-01',
        // ... other fields
      } as AuthorityAlertResponse
    ];
    
    const sorted = sortAlertsByPriority(alerts);
    
    expect(sorted[0].id).toBe('3'); // Highlighted first
    expect(sorted[1].id).toBe('2'); // Emergency
    expect(sorted[2].id).toBe('1'); // Advisory
  });
});
```


