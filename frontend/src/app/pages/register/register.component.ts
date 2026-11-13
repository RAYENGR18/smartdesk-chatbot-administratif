import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {

  selectedRole = '';

  // ── Identifiants admin ──
  studentUniqueId = '';
  profUniqueId    = '';

  // ── Informations personnelles communes ──
  firstName   = '';
  lastName    = '';
  birthDate   = '';
  birthPlace  = '';
  gender      = '';
  nationality = '';
  cin         = '';

  // ── Coordonnées ──
  email      = '';
  phone      = '';
  address    = '';
  city       = '';
  postalCode = '';
  country    = '';

  // ── Infos académiques antérieures (étudiant) ──
  lastDiploma        = '';
  diplomeSpecialite  = '';
  etablissementOrigine = '';
  anneeObtention     = '';
  moyenneGenerale    = '';
  mention            = '';

  // ── Choix de formation (étudiant) ──
  cycleChoisi       = '';
  specialiteChoisie = '';
  modeEtude         = '';

  // ── Financier (étudiant) ──
  modePaiement = '';
  demandeB     = '';

  // ── Enseignement (professeur) ──
  matieresProfesseur = '';
  cyclesConcernes: string[] = [];

  // ── Compte ──
  username        = '';
  password        = '';
  confirmPassword = '';

  // ── Validation ──
  isConfirmed         = false;
  isConfirmedAccuracy = false;

  showPassword        = false;
  showConfirmPassword = false;
  errorMessage        = '';
  successMessage      = '';
  isLoading           = false;
  currentLang         = 'fr';

  constructor(
    private authService:    AuthService,
    private router:         Router,
    private languageService: LanguageService,
    private themeService:   ThemeService,
    private location:       Location
  ) {
    this.languageService.currentLang$.subscribe(lang => { this.currentLang = lang; });
  }

  get isDark(): boolean { return this.themeService.isDark; }
  toggleTheme(): void   { this.themeService.toggleTheme(); }
  t(key: string): string { return this.languageService.t(key); }

  private L(fr: string, en: string, ar: string): string {
    if (this.currentLang === 'en') return en;
    if (this.currentLang === 'ar') return ar;
    return fr;
  }

  toggleCycle(cycle: string) {
    const i = this.cyclesConcernes.indexOf(cycle);
    if (i === -1) this.cyclesConcernes.push(cycle);
    else          this.cyclesConcernes.splice(i, 1);
  }
  hasCycle(c: string): boolean { return this.cyclesConcernes.includes(c); }

  goBack() { this.location.back(); }
  goToLogin() { this.router.navigate(['/login']); }

  selectRole(role: string) {
    this.selectedRole = role;
    this.errorMessage = '';
  }

  onRegister() {
    this.errorMessage  = '';
    this.successMessage = '';

    if (!this.selectedRole) {
      this.errorMessage = this.L('Veuillez choisir un rôle.', 'Please choose a role.', 'يرجى اختيار الدور.');
      return;
    }

    const id = this.selectedRole === 'USER' ? this.studentUniqueId.trim() : this.profUniqueId.trim();
    if (!id) {
      this.errorMessage = this.L('L\'identifiant administratif est obligatoire.', 'Admin ID is required.', 'المعرف الإداري إجباري.');
      return;
    }

    if (!this.email.trim()) {
      this.errorMessage = this.L('L\'email est obligatoire.', 'Email is required.', 'البريد الإلكتروني إجباري.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email)) {
      this.errorMessage = this.L('Email invalide.', 'Invalid email.', 'بريد إلكتروني غير صحيح.');
      return;
    }
    if (!this.cin.trim()) {
      this.errorMessage = this.L('Le CIN / Passeport est obligatoire.', 'CIN / Passport is required.', 'رقم البطاقة إجباري.');
      return;
    }
    if (!this.username.trim() || this.username.trim().length < 3) {
      this.errorMessage = this.L('Nom d\'utilisateur trop court (min 3).', 'Username too short (min 3).', 'اسم المستخدم قصير جدا.');
      return;
    }
    if (!this.password || this.password.length < 6) {
      this.errorMessage = this.L('Mot de passe trop court (min 6).', 'Password too short (min 6).', 'كلمة المرور قصيرة جدا.');
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage = this.L('Les mots de passe ne correspondent pas.', 'Passwords do not match.', 'كلمتا المرور غير متطابقتين.');
      return;
    }
    if (!this.isConfirmed) {
      this.errorMessage = this.L('Veuillez accepter les conditions générales.', 'Please accept the terms.', 'يرجى قبول الشروط العامة.');
      return;
    }
    if (!this.isConfirmedAccuracy) {
      this.errorMessage = this.L('Veuillez confirmer l\'exactitude des informations.', 'Please confirm accuracy.', 'يرجى تأكيد صحة المعلومات.');
      return;
    }

    // Build extra info as JSON
    const additionalInfo = this.selectedRole === 'USER'
      ? JSON.stringify({
          lastDiploma:         this.lastDiploma,
          diplomeSpecialite:   this.diplomeSpecialite,
          etablissementOrigine:this.etablissementOrigine,
          anneeObtention:      this.anneeObtention,
          moyenneGenerale:     this.moyenneGenerale,
          mention:             this.mention,
          cycleChoisi:         this.cycleChoisi,
          specialiteChoisie:   this.specialiteChoisie,
          modeEtude:           this.modeEtude,
          modePaiement:        this.modePaiement,
          demandeB:            this.demandeB,
          city:                this.city,
          postalCode:          this.postalCode,
          country:             this.country
        })
      : JSON.stringify({
          matieresProfesseur: this.matieresProfesseur,
          cyclesConcernes:    this.cyclesConcernes,
          city:               this.city,
          postalCode:         this.postalCode,
          country:            this.country
        });

    const payload: any = {
      username:      this.username.trim(),
      email:         this.email.trim(),
      password:      this.password,
      cin:           this.cin.trim(),
      studentId:     id,
      role:          this.selectedRole === 'USER' ? 'USER' : 'PROFESSEUR',
      firstName:     this.firstName.trim(),
      lastName:      this.lastName.trim(),
      birthDate:     this.birthDate,
      birthPlace:    this.birthPlace.trim(),
      gender:        this.gender,
      nationality:   this.nationality.trim(),
      phone:         this.phone.trim(),
      address:       this.address.trim(),
      department:    this.selectedRole === 'USER' ? '' : this.matieresProfesseur.trim(),
      academicLevel: this.selectedRole === 'USER' ? this.cycleChoisi : '',
      institution:   this.selectedRole === 'USER' ? this.etablissementOrigine.trim() : '',
      fieldOfStudy:  this.selectedRole === 'USER' ? this.diplomeSpecialite.trim() : '',
      scholarship:   this.selectedRole === 'USER' ? this.demandeB : '',
      additionalInfo
    };

    this.isLoading = true;
    this.authService.register(payload).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.successMessage = this.L('Compte créé avec succès !', 'Account created successfully!', 'تم إنشاء الحساب بنجاح!');
        this.authService.saveSession(res);
        setTimeout(() => {
          this.router.navigate([this.selectedRole === 'PROFESSEUR' ? '/professeur' : '/etudiant']);
        }, 1600);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage =
          err.error?.message || err.error?.error ||
          this.L('Erreur lors de l\'inscription.', 'Registration failed.', 'حدث خطأ أثناء التسجيل.');
      }
    });
  }
}
