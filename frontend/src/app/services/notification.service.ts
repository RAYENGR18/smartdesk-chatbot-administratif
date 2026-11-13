import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Notification {
  id?: number;
  title: string;
  content: string;
  target: 'STUDENT' | 'PROFESSOR';
  targetIds: string;
  priority: 'INFO' | 'WARNING' | 'URGENT';
  active: boolean;
  createdAt?: string;
  createdBy?: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {

  private api = `${environment.apiBaseUrl}/api/notifications`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Notification[]> {
    return this.http.get<Notification[]>(this.api);
  }

  create(notif: Partial<Notification>): Observable<Notification> {
    return this.http.post<Notification>(this.api, notif);
  }

  toggle(id: number): Observable<Notification> {
    return this.http.patch<Notification>(`${this.api}/${id}/toggle`, {});
  }

  delete(id: number): Observable<any> {
    return this.http.delete(`${this.api}/${id}`);
  }

  getForStudent(fieldOfStudy: string): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.api}/student`, {
      params: { fieldOfStudy }
    });
  }

  getForProfessor(userId: string): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.api}/professor`, {
      params: { userId }
    });
  }

  getStats(): Observable<any> {
    return this.http.get(`${this.api}/stats`);
  }
}
