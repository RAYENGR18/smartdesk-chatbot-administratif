import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'fasto_theme';
  private themeSubject: BehaviorSubject<Theme>;

  isDark$: BehaviorSubject<boolean>;

  constructor() {
    const saved = localStorage.getItem(this.STORAGE_KEY) as Theme | null;
    const initial: Theme = saved ?? 'dark'; // default to dark for premium look
    this.themeSubject = new BehaviorSubject<Theme>(initial);
    this.isDark$ = new BehaviorSubject<boolean>(initial === 'dark');
    this.applyTheme(initial);
  }

  get theme(): Theme {
    return this.themeSubject.value;
  }

  get isDark(): boolean {
    return this.themeSubject.value === 'dark';
  }

  toggleTheme(): void {
    const next: Theme = this.isDark ? 'light' : 'dark';
    this.setTheme(next);
  }

  setTheme(theme: Theme): void {
    localStorage.setItem(this.STORAGE_KEY, theme);
    this.themeSubject.next(theme);
    this.isDark$.next(theme === 'dark');
    this.applyTheme(theme);
  }

  private applyTheme(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
    document.documentElement.classList.toggle('dark', theme === 'dark');
    document.documentElement.classList.toggle('light', theme === 'light');
    // Update meta theme-color
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      meta.setAttribute('content', theme === 'dark' ? '#0f1117' : '#4f6ef7');
    }
  }
}
