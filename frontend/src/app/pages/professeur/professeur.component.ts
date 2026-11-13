import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ChatComponent } from '../chat/chat.component';
import { PlanningComponent } from '../planning/planning.component';

import { ThemeService } from '../../services/theme.service';
import { NotificationService, Notification } from '../../services/notification.service';

@Component({
  selector: 'app-professeur',
  standalone: true,
  imports: [CommonModule, FormsModule, ChatComponent, PlanningComponent],
  templateUrl: './professeur.component.html',
  styleUrls: ['./professeur.component.css']
})
export class ProfesseurComponent implements OnInit {

  activeTab = 'accueil';
  profile: any = null;
  currentLang = 'fr';
  isEditingProfile = false;
  editProfile: any = {};
  profileSaveMsg = '';
  profileSaveError = '';

  // ── Notifications ──────────────────────────
  notifications: Notification[] = [];
  dismissedIds = new Set<number>();

  get visibleNotifs(): Notification[] {
    return this.notifications.filter(n => !this.dismissedIds.has(n.id!));
  }

  dismissNotif(id: number) { this.dismissedIds.add(id); }

  constructor(
    private authService: AuthService,
    private router: Router,
    private languageService: LanguageService,
    private themeService: ThemeService,
    private notifSvc: NotificationService,
    private location: Location
  ) {
    this.languageService.currentLang$.subscribe(lang => {
      this.currentLang = lang;
    });
  }

  get isDark(): boolean { return this.themeService.isDark; }
  toggleTheme(): void   { this.themeService.toggleTheme(); }

  ngOnInit(): void {
    if (!this.authService.isLoggedIn() || this.authService.getRole() !== 'PROFESSEUR') {
      this.router.navigate(['/login']);
      return;
    }

    // Toujours charger depuis le backend pour avoir matieresEnseignees à jour
    this.authService.getStudentProfile().subscribe({
      next: (p: any) => {
        if (p) {
          this.authService.saveStudentProfile(p);
          this.profile = p;
          this.loadNotifications();
        }
      },
      error: () => {
        const stored = this.authService.getStoredProfile();
        if (stored) { this.profile = stored; this.loadNotifications(); }
      }
    });
  }

  getMatiereLabels(): string[] {
    const mm = this.profile?.matieresEnseignees;
    if (!mm || mm.length === 0) return [];
    return mm.map((m: any) => m.nom || m.code);
  }

  text(key: string): string {
    const translations: Record<string, Record<string, string>> = {
      fr: {
        professor_space: 'Espace Professeur',
        dashboard: 'Tableau de bord',
        assistant: 'Assistant FPST SmartDesk',
        timetable: 'Mon emploi du temps',
        grades: 'Saisie des notes',
        profile: 'Mon profil',
        logout: 'Deconnexion',
        welcome: 'Bienvenue, Professeur',
        welcome_desc: 'Retrouvez ici votre espace d\'enseignement et de gestion des notes.',
        department: 'Departement',
        specialty: 'Specialite',
        classes: 'Classes affectees',
        undefined: 'Non defini',
        profile_title: 'Mon profil professionnel',
        full_name: 'Nom complet',
        academic_email: 'Email academique',
        phone: 'Telephone',
        classes_count: '3 groupes'
      },
      en: {
        professor_space: 'Professor Area',
        dashboard: 'Dashboard',
        assistant: 'FPST SmartDesk Assistant',
        timetable: 'My timetable',
        grades: 'Grade entry',
        profile: 'My profile',
        logout: 'Sign out',
        welcome: 'Welcome, Professor',
        welcome_desc: 'Find your teaching workspace and grade management tools here.',
        department: 'Department',
        specialty: 'Specialty',
        classes: 'Assigned classes',
        undefined: 'Not set',
        profile_title: 'My professional profile',
        full_name: 'Full name',
        academic_email: 'Academic email',
        phone: 'Phone',
        classes_count: '3 groups'
      },
      ar: {
        professor_space: 'فضاء الأستاذ',
        dashboard: 'لوحة التحكم',
        assistant: 'مساعد FPST SmartDesk',
        timetable: 'جدولي',
        grades: 'إدخال الأعداد',
        profile: 'ملفي الشخصي',
        logout: 'تسجيل الخروج',
        welcome: 'مرحبا أستاذ',
        welcome_desc: 'هنا تجد فضاء التدريس وإدارة الأعداد.',
        department: 'القسم',
        specialty: 'الاختصاص',
        classes: 'الأقسام المسندة',
        undefined: 'غير محدد',
        profile_title: 'ملفي المهني',
        full_name: 'الاسم الكامل',
        academic_email: 'البريد الأكاديمي',
        phone: 'الهاتف',
        classes_count: '3 مجموعات'
      }
    };

    return translations[this.currentLang]?.[key] || translations['fr'][key] || key;
  }

  loadNotifications() {
    const userId = this.profile?.id?.toString();
    if (!userId) return;
    this.notifSvc.getForProfessor(userId).subscribe({
      next: list => this.notifications = list,
      error: () => {}
    });
  }

  setTab(tab: string) {
    this.activeTab = tab;
  }

  startEditProfile() {
    this.isEditingProfile = true;
    this.editProfile = {
      firstName: this.profile?.firstName || '',
      lastName: this.profile?.lastName || '',
      email: this.profile?.email || '',
      phone: this.profile?.phone || '',
      address: this.profile?.address || ''
    };
    this.profileSaveMsg = '';
    this.profileSaveError = '';
  }

  cancelEditProfile() {
    this.isEditingProfile = false;
  }

  saveProfile() {
    this.profileSaveMsg = '';
    this.profileSaveError = '';
    this.authService.updateProfile(this.editProfile).subscribe({
      next: () => {
        Object.assign(this.profile, this.editProfile);
        this.authService.saveStudentProfile(this.profile);
        this.isEditingProfile = false;
        this.profileSaveMsg = this.currentLang === 'ar' ? 'تم تحديث الملف الشخصي' :
                              this.currentLang === 'en' ? 'Profile updated successfully' :
                              'Profil mis a jour avec succes';
        setTimeout(() => this.profileSaveMsg = '', 4000);
      },
      error: () => {
        this.profileSaveError = this.currentLang === 'ar' ? 'خطأ أثناء التحديث' :
                                this.currentLang === 'en' ? 'Error updating profile' :
                                'Erreur lors de la mise a jour';
      }
    });
  }

  goBack() { this.location.back(); }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}

