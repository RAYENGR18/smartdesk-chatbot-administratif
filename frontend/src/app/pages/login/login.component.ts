import { Component } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {

  username = '';
  password = '';
  errorMessage = '';
  isLoading = false;
  currentLang = 'fr';

  rememberMe = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private languageService: LanguageService,
    private themeService: ThemeService
  ) {
    this.languageService.currentLang$.subscribe(lang => {
      this.currentLang = lang;
    });
  }

  get isDark(): boolean {
    return this.themeService.isDark;
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  t(key: string): string {
    return this.languageService.t(key);
  }

  text(key: string): string {
    const translations: Record<string, Record<string, string>> = {
      fr: {
        password_placeholder: 'Mot de passe',
      },
      en: {
        password_placeholder: 'Password',
      },
      ar: {
        password_placeholder: 'كلمة المرور',
      }
    };

    return translations[this.currentLang]?.[key] || translations['fr'][key] || key;
  }

  private navigateAfterLogin(targetUrl: string): void {
    this.isLoading = false;
    this.router.navigateByUrl(targetUrl, { replaceUrl: true });
  }

  onLogin() {
    const username = this.username?.trim();
    const password = this.password; // le mot de passe n'est jamais modifié (espaces compris)

    if (!username || !password) {
      this.errorMessage = this.t('login.empty_fields') || 'Veuillez remplir tous les champs';
      return;
    }

    localStorage.setItem('rememberMe', this.rememberMe.toString());

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(username, password).subscribe({
      next: (response: any) => {
        const role = this.authService.saveSession(response);

        if (role === 'ADMIN') {
          this.navigateAfterLogin('/admin');
          return;
        }

        if (role !== 'USER' && role !== 'PROFESSEUR') {
          this.authService.logout();
          this.isLoading = false;
          this.errorMessage = this.t('login.error') || 'Rôle utilisateur non reconnu.';
          return;
        }

        // Étudiant et professeur : on charge le profil avant d'ouvrir leur espace
        const target = role === 'PROFESSEUR' ? '/professeur' : '/etudiant';
        this.authService.getStudentProfile().subscribe({
          next: (profile: any) => {
            if (profile) this.authService.saveStudentProfile(profile);
            this.navigateAfterLogin(target);
          },
          error: () => this.navigateAfterLogin(target)
        });
      },
      error: (err: any) => {
        this.isLoading = false;
        if (err?.status === 429 || err?.status === 0) {
          // Compte temporairement bloqué ou serveur injoignable : message explicite
          this.errorMessage = err?.status === 0
            ? 'Serveur injoignable. Vérifiez que le backend est démarré.'
            : (err.error?.message || 'Trop de tentatives. Réessayez plus tard.');
          return;
        }
        this.errorMessage = this.t('login.error') || "Nom d'utilisateur ou mot de passe incorrect. Vérifiez vos informations.";
      }
    });
  }

  goToRegister() {
    this.router.navigate(['/register']);
  }
}




