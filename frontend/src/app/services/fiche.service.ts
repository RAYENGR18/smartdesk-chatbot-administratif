import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type TypeFiche = 'ETUDIANT' | 'PROFESSEUR';

export interface LigneFiche {
  id: number;
  nom: string;
  prenom: string;
  cin: string | null;
  username: string;
  email: string;
  // étudiant
  filiere?: string | null;
  niveau?: string | null;
  matricule?: string | null;
  moyenneGenerale?: number | null;
  nbNotes?: number;
  // professeur
  departement?: string | null;
  nbMatieres?: number;
  badge?: boolean;
}

export interface PageFiches {
  contenu: LigneFiche[];
  total: number;
  page: number;
  pages: number;
}

/** Fiches complètes des étudiants et des professeurs (administration). */
@Injectable({ providedIn: 'root' })
export class FicheService {

  private api = `${environment.apiBaseUrl}/api/admin/fiches`;

  constructor(private http: HttpClient) {}

  rechercher(type: TypeFiche, q: string, filiereId: number | null, niveau: string, page = 0): Observable<PageFiches> {
    const params: Record<string, string> = { type, page: String(page), taille: '25' };
    if (q.trim()) params['q'] = q.trim();
    if (filiereId) params['filiereId'] = String(filiereId);
    if (niveau) params['niveau'] = niveau;
    return this.http.get<PageFiches>(this.api, { params });
  }

  fiche(id: number): Observable<any> {
    return this.http.get<any>(`${this.api}/${id}`);
  }
}
