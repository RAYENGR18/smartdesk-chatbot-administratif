import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

interface AuthSessionResponse {
  token?: string | null;
  username?: string | null;
  role?: unknown;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = `${environment.apiBaseUrl}/api/auth`;

  // Profil étudiant : exposé par StudentController sur /api/student/profile/{username}
  private profileUrl = `${environment.apiBaseUrl}/api/student`;

  constructor(private http: HttpClient) {}

  private extractRoleValue(role: unknown): string | null {
    if (Array.isArray(role)) {
      return this.extractRoleValue(role[0]);
    }

    if (role && typeof role === 'object') {
      const authority = (role as { authority?: unknown }).authority;
      if (authority !== undefined) {
        return this.extractRoleValue(authority);
      }
    }

    if (typeof role !== 'string') {
      return role == null ? null : String(role);
    }

    const trimmedRole = role.trim();
    if (!trimmedRole) return null;

    try {
      const parsedRole = JSON.parse(trimmedRole);
      if (parsedRole !== trimmedRole) {
        return this.extractRoleValue(parsedRole);
      }
    } catch {
      // Ce n'est pas du JSON, on garde la valeur telle quelle.
    }

    return trimmedRole;
  }

  private normalizeRole(role: unknown): string | null {
    const raw = (this.extractRoleValue(role) || '')
      .replace(/^['"]+|['"]+$/g, '')
      .trim()
      .toUpperCase();

    if (!raw) return null;

    if (raw === 'ROLE_ADMIN' || raw === 'ADMIN') return 'ADMIN';
    if (raw === 'ROLE_PROFESSEUR' || raw === 'PROFESSEUR' || raw === 'PROFESSOR') return 'PROFESSEUR';
    if (raw === 'ROLE_STUDENT' || raw === 'STUDENT' || raw === 'ETUDIANT' || raw === 'USER') return 'USER';

    return raw;
  }

  register(studentData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, studentData);
  }

  login(username: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, { username, password });
  }

  saveSession(response: AuthSessionResponse | null | undefined): string | null {
    const token = String(response?.token ?? '').trim();
    const username = String(response?.username ?? '').trim();
    const normalizedRole = this.normalizeRole(response?.role);
    const userId = (response as any)?.userId;

    this.saveToken(token);
    this.saveUserInfo(username, normalizedRole);

    if (userId) {
      localStorage.setItem('userId', String(userId));
    }

    console.log('[AuthService] saveSession() -> response =', response);
    console.log('[AuthService] saveSession() -> localStorage.role =', localStorage.getItem('role'));
    console.log('[AuthService] saveSession() -> localStorage.username =', localStorage.getItem('username'));

    return normalizedRole;
  }

  // Récupère le profil depuis /api/student/profile/{username} (tous les rôles)
  getStudentProfile(): Observable<any> {
    const username = this.getUsername();
    if (!username) return of(null);
    return this.http.get(`${this.profileUrl}/profile/${username}`).pipe(
      catchError(() => of(null))
    );
  }

  saveToken(token: string | null | undefined): void {
    const normalizedToken = (token || '').trim();

    if (normalizedToken) {
      localStorage.setItem('token', normalizedToken);
      return;
    }

    localStorage.removeItem('token');
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getUserId(): number | null {
    const id = localStorage.getItem('userId');
    return id ? Number(id) : null;
  }

  isLoggedIn(): boolean {
    return this.getToken() !== null;
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    localStorage.removeItem('studentProfile');
    localStorage.removeItem('userId');
  }

  saveUserInfo(username: string | null | undefined, role: unknown): void {
    const normalizedUsername = (username || '').trim();
    const normalizedRole = this.normalizeRole(role);

    localStorage.removeItem('studentProfile');

    if (normalizedUsername) {
      localStorage.setItem('username', normalizedUsername);
    } else {
      localStorage.removeItem('username');
    }

    if (normalizedRole) {
      localStorage.setItem('role', normalizedRole);
    } else {
      localStorage.removeItem('role');
    }

    console.log('[AuthService] saveUserInfo() → username =', normalizedUsername, ', role =', normalizedRole);
  }

  saveStudentProfile(profile: any): void {
    if (profile) {
      localStorage.setItem('studentProfile', JSON.stringify(profile));
    }
  }

  getStoredProfile(): any {
    const p = localStorage.getItem('studentProfile');
    return p ? JSON.parse(p) : null;
  }

  getUsername(): string | null {
    return localStorage.getItem('username');
  }

  updateProfile(updates: any): Observable<any> {
    const username = this.getUsername();
    if (!username) return of(null);
    return this.http.put(`${environment.apiBaseUrl}/api/student/profile/${username}`, updates);
  }

  getRole(): string | null {
    const rawRole = localStorage.getItem('role');
    const role = this.normalizeRole(rawRole);

    if (role && rawRole !== role) {
      localStorage.setItem('role', role);
    }

    if (!role && rawRole) {
      localStorage.removeItem('role');
    }
    console.log('[AuthService] getRole() → localStorage.role (raw) =', rawRole, ', normalized =', role);

    return role;
  }
}
