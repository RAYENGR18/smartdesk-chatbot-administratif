import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Presence {
  id: number;
  professeurId: number;
  nom: string;
  prenom: string;
  date: string;
  heureArrivee: string;
  heureDepart: string | null;
  dureeMinutes: number | null;
  duree: string | null;
  statut: 'PRESENT' | 'TERMINE' | 'DEPART_NON_POINTE';
  lecteur: string;
}

export interface BadgeProfesseur {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  badge: string | null;
  statutJour: 'ABSENT' | 'PRESENT' | 'PARTI';
}

/** Présence des professeurs pointée par badge RFID (admin). */
@Injectable({ providedIn: 'root' })
export class PresenceService {

  private api = `${environment.apiBaseUrl}/api/admin/presences`;

  constructor(private http: HttpClient) {}

  private filtres(debut: string, fin: string, professeurId: number | null): Record<string, string> {
    const p: Record<string, string> = {};
    if (debut) p['debut'] = debut;
    if (fin) p['fin'] = fin;
    if (professeurId) p['professeurId'] = String(professeurId);
    return p;
  }

  rechercher(debut: string, fin: string, professeurId: number | null): Observable<Presence[]> {
    return this.http.get<Presence[]>(this.api, { params: this.filtres(debut, fin, professeurId) });
  }

  resume(): Observable<any> {
    return this.http.get(`${this.api}/resume`);
  }

  exporter(debut: string, fin: string, professeurId: number | null): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.api}/export`, {
      params: this.filtres(debut, fin, professeurId), observe: 'response', responseType: 'blob'
    });
  }

  badges(): Observable<BadgeProfesseur[]> {
    return this.http.get<BadgeProfesseur[]>(`${this.api}/badges`);
  }

  attribuerBadge(professeurId: number, uid: string): Observable<any> {
    return this.http.put(`${this.api}/badges/${professeurId}`, { uid });
  }

  badgesInconnus(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/badges-inconnus`);
  }

  passages(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/passages`);
  }

  simuler(professeurId: number): Observable<any> {
    return this.http.post(`${this.api}/simuler/${professeurId}`, {});
  }
}
