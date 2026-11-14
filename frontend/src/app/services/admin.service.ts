import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  private apiUrl = `${environment.apiBaseUrl}/api/admin`;

  constructor(private http: HttpClient) {}

  // statistiques
  getStats(): Observable<any> {
    return this.http.get(`${this.apiUrl}/stats`);
  }

  // liste utilisateurs
  getUsers(): Observable<any> {
    return this.http.get(`${this.apiUrl}/users`);
  }

  // supprimer utilisateur
  deleteUser(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/users/${id}`);
  }

  // modifier la spécialité d'un professeur
  updateSpecialite(id: number, specialite: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/users/${id}/specialite`, { specialite });
  }

  // affecter les filières d'un professeur
  updateFilieresProfesseur(id: number, filiereIds: number[]): Observable<any> {
    return this.http.put(`${this.apiUrl}/users/${id}/filieres`, filiereIds);
  }

  // affecter les matières d'un professeur
  updateMatieresEnseignees(id: number, matiereIds: number[]): Observable<any> {
    return this.http.put(`${this.apiUrl}/users/${id}/matieres`, matiereIds);
  }

  // liste conversations
  getMessages(): Observable<any> {
    return this.http.get(`${this.apiUrl}/messages`);
  }

  // supprimer un message
  deleteMessage(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/messages/${id}`);
  }

  // liste documents
  getDocuments(): Observable<any> {
    return this.http.get(`${this.apiUrl}/documents`);
  }
}