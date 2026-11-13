import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LanguageService } from '../../services/language.service';
import { ChatComponent } from '../chat/chat.component';
import { PlanningComponent } from '../planning/planning.component';
import { NoteService } from '../../services/note.service';
import { ThemeService } from '../../services/theme.service';
import { NotificationService, Notification } from '../../services/notification.service';

@Component({
  selector: 'app-etudiant',
  standalone: true,
  imports: [CommonModule, FormsModule, ChatComponent, PlanningComponent],
  templateUrl: './etudiant.component.html',
  styleUrls: ['./etudiant.component.css']
})
export class EtudiantComponent implements OnInit {

  activeTab = 'accueil';
  profile: any = null;
  currentLang = 'fr';
  mesNotes: any[] = [];
  isLoadingNotes = false;
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
    private location: Location,
    private languageService: LanguageService,
    private noteService: NoteService,
    private themeService: ThemeService,
    private notifSvc: NotificationService
  ) {
    this.languageService.currentLang$.subscribe(lang => {
      this.currentLang = lang;
    });
  }

  get isDark(): boolean { return this.themeService.isDark; }
  toggleTheme(): void   { this.themeService.toggleTheme(); }

  ngOnInit(): void {
    if (!this.authService.isLoggedIn() || this.authService.getRole() !== 'USER') {
      this.router.navigate(['/login']);
      return;
    }

    this.profile = this.authService.getStoredProfile();
    this.loadNotes();
    this.loadNotifications();
  }

  t(key: string): string {
    return this.languageService.t(key);
  }

  text(key: string): string {
    const translations: Record<string, Record<string, string>> = {
      fr: {
        student_space: 'Espace Etudiant',
        dashboard: 'Tableau de bord',
        assistant: 'Assistant FPST SmartDesk',
        timetable: 'Emploi du temps',
        grades: 'Notes et releves',
        profile: 'Mon profil',
        logout: 'Deconnexion',
        welcome: 'Bienvenue',
        welcome_desc: 'Retrouvez ici toutes vos informations academiques et accedez a l\'assistant FPST SmartDesk.',
        field: 'Filiere',
        level: 'Niveau academique',
        academic_year: 'Annee universitaire',
        undefined: 'Non defini',
        notes_title: 'Releve de notes',
        notes_desc: 'Cette section affiche vos notes officielles importées par l\'administrateur.',
        loading: 'Chargement de vos notes...',
        subject: 'Matiere',
        professor: 'Professeur',
        type: 'Type',
        date: 'Date',
        grade: 'Note /20',
        status: 'Statut',
        comment: 'Commentaire',
        no_grades: 'Aucune note disponible pour le moment. L\'administrateur n\'a pas encore importé vos notes.',
        validated: 'Valide',
        retake: 'Rattrapage',
        nb_pass: 'Notes ≥ 10',
        nb_fail: 'Notes < 10',
        moyenne: 'Moyenne',
        total: 'Total notes',
        info_title: 'Mes informations',
        full_name: 'Nom complet',
        student_id: 'ID etudiant',
        email: 'Email',
        phone: 'Telephone',
        cin: 'CIN',
        field_level: 'Filiere et niveau'
      },
      en: {
        student_space: 'Student Area',
        dashboard: 'Dashboard',
        assistant: 'FPST SmartDesk Assistant',
        timetable: 'Timetable',
        grades: 'Grades and transcripts',
        profile: 'My profile',
        logout: 'Sign out',
        welcome: 'Welcome',
        welcome_desc: 'Find your academic information here and access the FPST SmartDesk assistant.',
        field: 'Program',
        level: 'Academic level',
        academic_year: 'Academic year',
        undefined: 'Not set',
        notes_title: 'Transcript',
        notes_desc: 'This section shows your official grades imported by the administrator.',
        loading: 'Loading your grades...',
        subject: 'Subject',
        professor: 'Professor',
        type: 'Type',
        date: 'Date',
        grade: 'Grade /20',
        status: 'Status',
        comment: 'Comment',
        no_grades: 'No grades available yet. The administrator has not imported your grades yet.',
        validated: 'Passed',
        retake: 'Retake',
        nb_pass: 'Grades ≥ 10',
        nb_fail: 'Grades < 10',
        moyenne: 'Average',
        total: 'Total grades',
        info_title: 'My information',
        full_name: 'Full name',
        student_id: 'Student ID',
        email: 'Email',
        phone: 'Phone',
        cin: 'National ID',
        field_level: 'Program and level'
      },
      ar: {
        student_space: 'فضاء الطالب',
        dashboard: 'لوحة التحكم',
        assistant: 'مساعد FPST SmartDesk',
        timetable: 'جدول الحصص',
        grades: 'النتائج وكشوف الأعداد',
        profile: 'ملفي الشخصي',
        logout: 'تسجيل الخروج',
        welcome: 'مرحبا',
        welcome_desc: 'هنا تجد معلوماتك الأكاديمية وتصل إلى مساعد FPST SmartDesk.',
        field: 'الاختصاص',
        level: 'المستوى الأكاديمي',
        academic_year: 'السنة الجامعية',
        undefined: 'غير محدد',
        notes_title: 'كشف الأعداد',
        notes_desc: 'هذا القسم يعرض أعدادك الرسمية التي استوردها المدير.',
        loading: 'جار تحميل الأعداد...',
        subject: 'المادة',
        professor: 'الأستاذ',
        type: 'النوع',
        date: 'التاريخ',
        grade: 'العدد /20',
        status: 'الحالة',
        comment: 'ملاحظة',
        no_grades: 'لا توجد أعداد متاحة حاليا. لم يقم المدير بعد باستيراد نتائجك.',
        validated: 'ناجح',
        retake: 'تدارك',
        nb_pass: 'أعداد ≥ 10',
        nb_fail: 'أعداد < 10',
        moyenne: 'المعدل',
        total: 'مجموع الأعداد',
        info_title: 'معلوماتي',
        full_name: 'الاسم الكامل',
        student_id: 'معرف الطالب',
        email: 'البريد الإلكتروني',
        phone: 'الهاتف',
        cin: 'بطاقة التعريف',
        field_level: 'الاختصاص والمستوى'
      }
    };

    return translations[this.currentLang]?.[key] || translations['fr'][key] || key;
  }

  // ── STATISTICS ────────────────────────────────────────
  get totalNotes()  { return this.mesNotes.length; }
  get nbPass()      { return this.mesNotes.filter(n => n.valeur >= 10).length; }
  get nbFail()      { return this.mesNotes.filter(n => n.valeur < 10).length; }
  get moyenne(): number {
    if (!this.mesNotes.length) return 0;
    return this.mesNotes.reduce((s: number, n: any) => s + (n.valeur ?? 0), 0) / this.mesNotes.length;
  }

  formatDate(d: any): string {
    if (!d) return '—';
    if (Array.isArray(d)) {
      const [y, mo, day] = d;
      return `${String(day).padStart(2,'0')}/${String(mo).padStart(2,'0')}/${y}`;
    }
    try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return String(d); }
  }

  typeLabel(v: string): string {
    const lbl: Record<string, Record<string, string>> = {
      DS:     { fr: 'DS / CC',  en: 'Quiz',    ar: 'فرض' },
      Examen: { fr: 'Examen',   en: 'Exam',    ar: 'امتحان' },
      TP:     { fr: 'TP / TD',  en: 'Lab',     ar: 'تطبيقي' },
      Projet: { fr: 'Projet',   en: 'Project', ar: 'مشروع' }
    };
    return lbl[v]?.[this.currentLang] ?? v;
  }

  loadNotes() {
    this.isLoadingNotes = true;
    const userId = this.profile?.id || Number(localStorage.getItem('userId')) || 0;
    if (!userId) {
      this.isLoadingNotes = false;
      return;
    }
    this.noteService.getNotesByEtudiant(userId).subscribe({
      next: (data) => {
        this.mesNotes = data;
        this.isLoadingNotes = false;
      },
      error: () => this.isLoadingNotes = false
    });
  }

  loadNotifications() {
    const fieldOfStudy = this.profile?.fieldOfStudy;
    if (!fieldOfStudy) return;
    this.notifSvc.getForStudent(fieldOfStudy).subscribe({
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
      address: this.profile?.address || '',
      fieldOfStudy: this.profile?.fieldOfStudy || '',
      academicLevel: this.profile?.academicLevel || ''
    };
    this.profileSaveMsg = '';
    this.profileSaveError = '';
  }

  cancelEditProfile() {
    this.isEditingProfile = false;
    this.profileSaveMsg = '';
    this.profileSaveError = '';
  }

  saveProfile() {
    this.profileSaveMsg = '';
    this.profileSaveError = '';
    this.authService.updateProfile(this.editProfile).subscribe({
      next: () => {
        // Update local profile
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

