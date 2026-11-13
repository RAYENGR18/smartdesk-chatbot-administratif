import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type KbAudience = 'student' | 'professor';

@Injectable({ providedIn: 'root' })
export class KnowledgeBaseService {

  private api = `${environment.apiBaseUrl}/api/knowledge-base`;

  constructor(private http: HttpClient) {}

  getStatus(): Observable<any> {
    return this.http.get(`${this.api}/status`);
  }

  listDocuments(audience: KbAudience = 'student'): Observable<any> {
    return this.http.get(`${this.api}/documents`, { params: { audience } });
  }

  uploadDocument(file: File, audience: KbAudience = 'student'): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    form.append('audience', audience);
    return this.http.post(`${this.api}/upload`, form);
  }

  deleteDocument(filename: string, audience: KbAudience = 'student'): Observable<any> {
    return this.http.delete(`${this.api}/documents/${encodeURIComponent(filename)}`, {
      params: { audience }
    });
  }

  getContent(filename: string, audience: KbAudience = 'student'): Observable<any> {
    return this.http.get(`${this.api}/documents/${encodeURIComponent(filename)}/content`, {
      params: { audience }
    });
  }

  updateContent(filename: string, content: string, audience: KbAudience = 'student'): Observable<any> {
    return this.http.put(
      `${this.api}/documents/${encodeURIComponent(filename)}/content`,
      { content },
      { params: { audience } }
    );
  }

  reindex(audience: string = 'all'): Observable<any> {
    return this.http.post(`${this.api}/reindex`, {}, { params: { audience } });
  }
}
