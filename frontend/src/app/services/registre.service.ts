import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PersonneRegistre {
  id: number;
  cin: string;
  nom: string;
  prenom: string;
  type: 'ETUDIANT' | 'PROFESSEUR';
  filiere?: string;
  niveau?: string;
  email?: string;
  matricule?: string;
  departement?: string;
  origine: string;
  compteCree: boolean;
}

export interface ResultatImport {
  lignes: number;
  ajoutes: number;
  misAJour: number;
  ignores: number;
  erreurs: string[];
}

/** Registre de l'établissement (admin) : base de vérification des inscriptions par CIN. */
@Injectable({ providedIn: 'root' })
export class RegistreService {

  private api = `${environment.apiBaseUrl}/api/admin/registre`;

  constructor(private http: HttpClient) {}

  rechercher(q: string, type: string, page: number, size = 25): Observable<any> {
    const params: Record<string, string> = { q, page: String(page), size: String(size) };
    if (type) params['type'] = type;
    return this.http.get(this.api, { params });
  }

  stats(): Observable<any> {
    return this.http.get(`${this.api}/stats`);
  }

  importer(file: File): Observable<ResultatImport> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ResultatImport>(`${this.api}/import`, form);
  }

  chargerDemo(): Observable<any> {
    return this.http.post(`${this.api}/demo`, {});
  }

  supprimer(id: number): Observable<any> {
    return this.http.delete(`${this.api}/${id}`);
  }
}
