import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class NoteService {

  private apiUrl = `${environment.apiBaseUrl}/api/notes`;

  constructor(private http: HttpClient) {}

  /** Notes de l'étudiant connecté (identifié par son token) */
  getMesNotes(): Observable<any> {
    return this.http.get(`${this.apiUrl}/me`);
  }

  /** Récupère les notes d'un étudiant (espace étudiant) */
  getNotesByEtudiant(etudiantId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/etudiant/${etudiantId}`);
  }

  /** Récupère toutes les notes (espace admin) */
  getAllNotes(): Observable<any> {
    return this.http.get(this.apiUrl);
  }

  /** Supprime une note (admin) */
  supprimerNote(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`);
  }

  /**
   * Import d'un fichier Excel (.xlsx) ou CSV (.csv) par l'administrateur.
   * Retourne un rapport : { totalLignes, inserted, updated, skipped, errors[] }
   */
  importerNotes(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.apiUrl}/import`, formData);
  }
}
