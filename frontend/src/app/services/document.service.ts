import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {

  private apiUrl = `${environment.apiBaseUrl}/api/documents`;

  constructor(private http: HttpClient) {}

  /** Génère un document pour l'utilisateur connecté (le backend ignore tout autre nom d'utilisateur). */
  generateDocument(
    documentType: string,
    fullName: string,
    additionalInfo: string,
    format: string,
    _username?: string
  ): Observable<any> {
    return this.http.post(`${this.apiUrl}/generate`, {
      documentType,
      fullName,
      additionalInfo,
      format
    });
  }

  /**
   * Télécharge un document avec le token JWT (un simple lien <a href> ou
   * window.open ne transmet pas l'en-tête Authorization).
   */
  downloadDocument(id: number): void {
    this.http.get(`${this.apiUrl}/download/${id}`, { observe: 'response', responseType: 'blob' })
      .subscribe({
        next: (res: HttpResponse<Blob>) => {
          const blob = res.body;
          if (!blob) return;
          const disposition = res.headers.get('Content-Disposition') || '';
          const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
          const filename = match ? decodeURIComponent(match[1]) : `document_${id}`;

          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = filename;
          document.body.appendChild(link);
          link.click();
          link.remove();
          setTimeout(() => URL.revokeObjectURL(url), 10_000);
        },
        error: () => alert('Téléchargement impossible. Reconnectez-vous puis réessayez.')
      });
  }

  // liste des documents
  getUserDocuments(username: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/list/${encodeURIComponent(username)}`);
  }
}
