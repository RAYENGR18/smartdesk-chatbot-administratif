import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

  private logStoredSession(): void {
    console.log('[Login] localStorage.role =', localStorage.getItem('role'));
    console.log('[Login] localStorage.username =', localStorage.getItem('username'));
  }

  private navigateAfterLogin(targetUrl: string): void {
    this.isLoading = false;
    this.router.navigateByUrl(targetUrl, { replaceUrl: true }).then((navigated) => {
      console.log('[Login] Navigation success =', navigated, ', target =', targetUrl);
      console.log('[Login] Route finale après navigation =', this.router.url);
    });
  }

  onLogin() {
    const username = this.username?.trim();
    const password = this.password?.trim();

    if (!username || !password) {
      this.errorMessage = this.t('login.empty_fields') || 'Veuillez remplir tous les champs';
      return;
    }

    localStorage.setItem('rememberMe', this.rememberMe.toString());

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(username, password).subscribe({
      next: (response: any) => {
        console.log('[Login] Réponse backend:', response);
        const role = this.authService.saveSession(response);
        this.logStoredSession();

        // Important: utiliser le rôle normalisé par AuthService
        // (gère ADMIN, ROLE_ADMIN, STUDENT, etc.)
        console.log('[Login] Rôle normalisé (via AuthService) =', role);

        // Pour ADMIN et PROFESSEUR : pas de profil étudiant à charger → navigation directe
        if (role === 'ADMIN') {
          console.log('[Login] Rôle ADMIN détecté -> navigation vers /admin');
          this.navigateAfterLogin('/admin');
          return;
          this.isLoading = false;
          console.log('[Login] Rôle ADMIN → navigation vers /admin');
          this.router.navigate(['/admin']).then(() => {
            console.log('[Login] Route finale après navigation =', this.router.url);
          });
          return;
        }

        if (role === 'PROFESSEUR') {
          console.log('[Login] Rôle PROFESSEUR détecté -> chargement profil puis navigation');
          this.authService.getStudentProfile().subscribe({
            next: (profile: any) => { if (profile) this.authService.saveStudentProfile(profile); },
            error: () => {}
          });
          this.navigateAfterLogin('/professeur');
          return;
          this.isLoading = false;
          console.log('[Login] Rôle PROFESSEUR → navigation vers /professeur');
          this.router.navigate(['/professeur']).then(() => {
            console.log('[Login] Route finale après navigation =', this.router.url);
          });
          return;
        }

        // Pour les étudiants : charger le profil avant de naviguer
        if (role !== 'USER') {
          console.error('[Login] Rôle inattendu reçu depuis le backend =', response?.role);
          this.isLoading = false;
          this.errorMessage = this.t('login.error') || 'Role utilisateur non reconnu.';
          return;
        }

        this.authService.getStudentProfile().subscribe({
          next: (profile: any) => {
            this.authService.saveStudentProfile(profile);
            console.log('[Login] Rôle étudiant détecté -> navigation vers /etudiant');
            this.navigateAfterLogin('/etudiant');
            return;
            this.isLoading = false;
            console.log('[Login] Rôle étudiant → navigation vers /etudiant');
            this.router.navigate(['/etudiant']).then(() => {
              console.log('[Login] Route finale après navigation =', this.router.url);
            });
          },
          error: (errProfile: any) => {
            console.error('[Login] Profile fetch failed, navigating anyway', errProfile);
            this.navigateAfterLogin('/etudiant');
            return;
            this.isLoading = false;
            this.router.navigate(['/etudiant']).then(() => {
              console.log('[Login] Route finale après navigation =', this.router.url);
            });
          }
        });
      },
      error: (err: any) => {
        console.error('Login Error: ', err);
        this.isLoading = false;
        this.errorMessage = this.t('login.error') || "Nom d'utilisateur ou mot de passe incorrect. Vérifiez vos informations.";
      }
    });
  }

  goToRegister() {
    this.router.navigate(['/register']);
  }
}




