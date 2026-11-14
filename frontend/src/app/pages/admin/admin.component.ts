import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';
import { LanguageService } from '../../services/language.service';
import { NoteService } from '../../services/note.service';
import { FiliereService } from '../../services/filiere.service';
import { MatiereService } from '../../services/matiere.service';
import { ChatComponent } from '../chat/chat.component';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, ChatComponent],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit {

  stats: any = {
    totalUsers: 0,
    totalMessages: 0,
    totalDocuments: 0,
    totalFilieres: 0,
    totalSalles: 0,
    totalMatieres: 0,
    totalCreneaux: 0,
    nbEtudiants: 0,
    nbProfesseurs: 0,
    nbAdmins: 0
  };

  users: any[] = [];
  messages: any[] = [];
  documents: any[] = [];
  notes: any[] = [];
  filieres: any[] = [];
  allMatieres: any[] = [];
  activeTab = 'stats';
  isLoading = false;

  // ── Import notes ───────────────────────────────────────────────
  importFile: File | null = null;
  importFileName = '';
  isImporting = false;
  importResult: any = null;
  importError = '';
  importSuccess = '';

  currentLang = 'fr';

  constructor(
    private adminService: AdminService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private languageService: LanguageService,
    private noteService: NoteService,
    private themeService: ThemeService,
    private filiereService: FiliereService,
    private matiereService: MatiereService,
    private location: Location
  ) {
    this.languageService.currentLang$.subscribe(lang => {
      this.currentLang = lang;
    });
  }

  get isDark(): boolean { return this.themeService.isDark; }
  toggleTheme(): void   { this.themeService.toggleTheme(); }

  ngOnInit() {

    const role = this.authService.getRole();

    if (role !== 'ADMIN') {
      console.warn('[AdminComponent] Accès refusé (rôle ≠ ADMIN) → redirection /login');
      this.router.navigateByUrl('/login', { replaceUrl: true });
      return;
    }
    this.loadStats();
    this.loadUsers();
    this.loadMessages();
    this.loadDocuments();
    this.loadNotes();
    this.loadFilieres();
    this.loadAllMatieres();

    const tab = this.route.snapshot.queryParamMap.get('tab');
    if (tab) this.activeTab = tab;
  }

  toggleLanguage() { this.languageService.toggleLanguage(); }
  t(key: string): string { return this.languageService.t(key); }

  text(key: string): string {
    const translations: Record<string, Record<string, string>> = {
      fr: {
        next_lang: 'العربية',
        admin_badge: 'Admin',
        title: 'Espace admin',
        dashboard: 'Tableau de bord',
        identities: 'Identifiants',
        users: 'Utilisateurs',
        faculty: 'Gestion faculte',
        grades: 'Notes',
        grades_desc: 'Importez un fichier Excel ou CSV contenant les notes de tous les étudiants.',
        import_file: 'Choisir un fichier (.xlsx ou .csv)',
        import_btn: 'Importer les notes',
        import_success: 'Import réussi',
        import_error_empty: 'Veuillez choisir un fichier avant d\'importer.',
        import_lines: 'Lignes traitées',
        import_inserted: 'Nouvelles notes',
        import_updated: 'Notes mises à jour',
        import_skipped: 'Lignes ignorées',
        import_errors: 'Erreurs d\'import',
        file_format_title: 'Format attendu du fichier',
        file_format_desc: 'Votre fichier doit contenir une ligne d\'en-tête avec les colonnes suivantes :', 
        col_cin: 'CIN — CIN de l\'étudiant, 8 chiffres (obligatoire)',
        col_matiere: 'Matière — nom ou code de la matière',
        col_type: 'Type — Examen (par défaut), DS, TP, Projet',
        col_note: 'Note — sur 20, virgule ou point (obligatoire)',
        col_moyenne: 'moyenne — Moyenne (optionnel)',
        col_commentaire: 'commentaire — Commentaire (optionnel)',

        messages: 'Messages',
        documents: 'Documents',
        bot: 'Chatbot',
        timetable: 'Planning',
        key_badge: 'Cle',
        users_breakdown: 'Repartition des utilisateurs',
        identity_title: 'Generation d identifiant',
        identity_how: 'Fonctionnement',
        identity_note: 'Conservez les identifiants en securite.',
        identity_step1: 'Choisir le role.',
        identity_step2: 'Saisir le prenom.',
        identity_step3: 'Saisir le nom.',
        identity_step4: 'Saisir le CIN.',
        identity_step5: 'Cliquer sur Generer.',
        identity_role: 'Role',
        identity_generate_title: 'Generer un identifiant',
        identity_generate_btn: 'Generer',
        identity_success: 'Identifiant genere',
        copied: 'Identifiant copie',
        confirm_delete: 'Supprimer cet utilisateur ?',
        identity_error_empty: 'Veuillez remplir les champs obligatoires.',
        identity_error_gen: 'Generation impossible.',
        students: 'Etudiants',
        professors: 'Professeurs',
        admins: 'Admins',
        manage_programs: 'Filieres',
        manage_rooms: 'Salles',
        manage_subjects: 'Matieres',
        copy: 'Copier',
        delete: 'Supprimer',
        date: 'Date',
        action: 'Action',
        user: 'Utilisateur',
        message: 'Message',
        response: 'Reponse',
        intent: 'Intention',
        lang: 'Langue',
        type: 'Type',
        name: 'Nom',
        format: 'Format',
        no_users: 'Aucun utilisateur trouve',
        manage_programs_desc: 'Gerer les filieres de la faculte',
        manage_rooms_desc: 'Gerer les salles et laboratoires',
        manage_subjects_desc: 'Gerer les matieres et les classes',
        manage_timetable_desc: 'Gerer les emplois du temps',
        evaluation: 'Evaluation',
        note20: 'Note / 20',
        status: 'Statut',
        passed: 'Valide',
        retake: 'Rattrapage',
        no_grades: 'Aucune note trouvee',
        no_messages: 'Aucun message trouve',
        no_documents: 'Aucune demande de document',
        confirm_delete_msg: 'Supprimer ce message de l\'historique ?'
      },
      en: {
        next_lang: 'Francais',
        admin_badge: 'Admin',
        title: 'Admin dashboard',
        dashboard: 'Dashboard',
        identities: 'Identifiers',
        users: 'Users',
        faculty: 'Faculty management',
        grades: 'Grades',
        grades_desc: 'Import an Excel or CSV file containing all student grades.',
        import_file: 'Choose a file (.xlsx or .csv)',
        import_btn: 'Import grades',
        import_success: 'Import successful',
        import_error_empty: 'Please select a file before importing.',
        import_lines: 'Lines processed',
        import_inserted: 'New grades',
        import_updated: 'Updated grades',
        import_skipped: 'Skipped lines',
        import_errors: 'Import errors',
        file_format_title: 'Expected file format',
        file_format_desc: 'Your file must contain a header row with the following columns:',
        col_cin: 'cin — Student CIN (required)',
        col_matiere: 'Matière — subject name or code',
        col_type: 'typeEvaluation — Type: Examen, DS, TP, Projet',
        col_note: 'note — Grade out of 20 (required)',
        col_moyenne: 'moyenne — Average (optional)',
        col_commentaire: 'commentaire — Comment (optional)',

        messages: 'Messages',
        documents: 'Documents',
        bot: 'Chatbot',
        timetable: 'Timetable',
        key_badge: 'Key',
        users_breakdown: 'Users breakdown',
        identity_title: 'Identifier generation',
        identity_how: 'How it works',
        identity_note: 'Keep identifiers secure.',
        identity_step1: 'Choose a role.',
        identity_step2: 'Enter first name.',
        identity_step3: 'Enter last name.',
        identity_step4: 'Enter national ID.',
        identity_step5: 'Click Generate.',
        identity_role: 'Role',
        identity_generate_title: 'Generate identifier',
        identity_generate_btn: 'Generate',
        identity_success: 'Identifier generated',
        copied: 'Identifier copied',
        confirm_delete: 'Delete this user?',
        identity_error_empty: 'Please fill in required fields.',
        identity_error_gen: 'Unable to generate identifier.',
        students: 'Students',
        professors: 'Professors',
        admins: 'Admins',
        manage_programs: 'Programs',
        manage_rooms: 'Rooms',
        manage_subjects: 'Subjects',
        copy: 'Copy',
        delete: 'Delete',
        date: 'Date',
        action: 'Action',
        user: 'User',
        message: 'Message',
        response: 'Response',
        intent: 'Intent',
        lang: 'Language',
        type: 'Type',
        name: 'Name',
        format: 'Format',
        no_users: 'No users found',
        manage_programs_desc: 'Manage faculty programs',
        manage_rooms_desc: 'Manage rooms and labs',
        manage_subjects_desc: 'Manage subjects and classes',
        manage_timetable_desc: 'Manage timetables',
        evaluation: 'Evaluation',
        note20: 'Grade / 20',
        status: 'Status',
        passed: 'Passed',
        retake: 'Retake',
        no_grades: 'No grades found',
        no_messages: 'No messages found',
        no_documents: 'No document requests',
        confirm_delete_msg: 'Delete this message from history?'
      },
      ar: {
        next_lang: 'English',
        admin_badge: 'مدير',
        title: 'فضاء المدير',
        dashboard: 'لوحة التحكم',
        identities: 'المعرفات',
        users: 'المستخدمون',
        faculty: 'إدارة الكلية',
        grades: 'النتائج',
        grades_desc: 'استورد ملف Excel أو CSV يتضمن نتائج جميع الطلبة.',
        import_file: 'اختر ملف (.xlsx أو .csv)',
        import_btn: 'استيراد النتائج',
        import_success: 'تم الاستيراد بنجاح',
        import_error_empty: 'يرجى اختيار ملف قبل الاستيراد.',
        import_lines: 'الأسطر المعالجة',
        import_inserted: 'أعداد جديدة',
        import_updated: 'أعداد محدّثة',
        import_skipped: 'أسطر متجاوزة',
        import_errors: 'أخطاء الاستيراد',
        file_format_title: 'تنسيق الملف المتوقع',
        file_format_desc: 'يجب أن يحتوي ملفك على سطر عناوين بالأعمدة التالية:',
        col_cin: 'cin — رقم بطاقة الطالب (إلزامي)',
        col_matiere: 'Matière — اسم المادة',
        col_type: 'typeEvaluation — النوع: Examen, DS, TP, Projet',
        col_note: 'note — العدد من 20 (إلزامي)',
        col_moyenne: 'moyenne — المعدل (اختياري)',
        col_commentaire: 'commentaire — ملاحظة (اختياري)',

        messages: 'الرسائل',
        documents: 'الوثائق',
        bot: 'الشات بوت',
        timetable: 'الجدول',
        key_badge: 'مفتاح',
        users_breakdown: 'توزيع المستخدمين',
        identity_title: 'توليد معرف',
        identity_how: 'طريقة العمل',
        identity_note: 'احفظ المعرفات في مكان آمن.',
        identity_step1: 'اختر الدور.',
        identity_step2: 'ادخل الاسم.',
        identity_step3: 'ادخل اللقب.',
        identity_step4: 'ادخل رقم البطاقة.',
        identity_step5: 'اضغط على توليد.',
        identity_role: 'الدور',
        identity_generate_title: 'توليد معرف جديد',
        identity_generate_btn: 'توليد',
        identity_success: 'تم توليد المعرف',
        copied: 'تم نسخ المعرف',
        confirm_delete: 'هل تريد حذف هذا المستخدم؟',
        identity_error_empty: 'يرجى تعبئة كل الحقول المطلوبة.',
        identity_error_gen: 'تعذر توليد المعرف.',
        students: 'الطلبة',
        professors: 'الاساتذة',
        admins: 'المدراء',
        manage_programs: 'الاختصاصات',
        manage_rooms: 'القاعات',
        manage_subjects: 'المواد',
        copy: 'نسخ',
        delete: 'حذف',
        date: 'التاريخ',
        action: 'إجراء',
        user: 'المستخدم',
        message: 'الرسالة',
        response: 'الرد',
        intent: 'النية',
        lang: 'اللغة',
        type: 'النوع',
        name: 'الاسم',
        format: 'الصيغة',
        no_users: 'لا يوجد مستخدمون',
        manage_programs_desc: 'إدارة اختصاصات الكلية',
        manage_rooms_desc: 'إدارة القاعات والمخابر',
        manage_subjects_desc: 'إدارة المواد والأقسام',
        manage_timetable_desc: 'إدارة الجداول',
        evaluation: 'التقييم',
        note20: 'العدد / 20',
        status: 'الحالة',
        passed: 'ناجح',
        retake: 'تدارك',
        no_grades: 'لا توجد أعداد',
        no_messages: 'لا توجد رسائل',
        no_documents: 'لا توجد طلبات وثائق',
        confirm_delete_msg: 'حذف هذه الرسالة من السجل؟'
      }
    };

    return translations[this.currentLang]?.[key] || translations['fr'][key] || key;
  }

  loadStats() {
    this.adminService.getStats().subscribe({
      next: (data) => this.stats = data,
      error: (err) => console.error(err)
    });
  }

  loadUsers() {
    this.adminService.getUsers().subscribe({
      next: (data) => this.users = data,
      error: (err) => console.error(err)
    });
  }

  loadFilieres() {
    this.filiereService.getAll().subscribe({
      next: (data) => this.filieres = data,
      error: (err) => console.error(err)
    });
  }

  loadAllMatieres() {
    this.matiereService.getAll().subscribe({
      next: (data) => this.allMatieres = data,
      error: (err) => console.error(err)
    });
  }

  loadMessages() {
    this.adminService.getMessages().subscribe({
      next: (data) => this.messages = data,
      error: (err) => console.error(err)
    });
  }

  loadDocuments() {
    this.adminService.getDocuments().subscribe({
      next: (data) => this.documents = data,
      error: (err) => console.error(err)
    });
  }

  loadNotes() {
    this.noteService.getAllNotes().subscribe({
      next: (data: any) => this.notes = data,
      error: (err: any) => console.error(err)
    });
  }

  // ── Import de notes ─────────────────────────────────────────────
  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.importFile = input.files[0];
      this.importFileName = this.importFile.name;
      this.importResult = null;
      this.importError = '';
      this.importSuccess = '';
    }
  }

  lancerImport() {
    if (!this.importFile) {
      this.importError = this.text('import_error_empty');
      return;
    }
    this.isImporting = true;
    this.importResult = null;
    this.importError = '';
    this.importSuccess = '';

    this.noteService.importerNotes(this.importFile).subscribe({
      next: (res: any) => {
        this.isImporting = false;
        this.importResult = res;
        this.importSuccess = this.text('import_success');
        this.loadNotes(); // Rafraîchir le tableau
      },
      error: (err: any) => {
        this.isImporting = false;
        this.importError = err.error?.message || err.error?.error || 'Erreur lors de l\'import.';
      }
    });
  }

  supprimerNote(id: number) {
    if (confirm(this.text('confirm_delete'))) {
      this.noteService.supprimerNote(id).subscribe({
        next: () => { this.loadNotes(); },
        error: (err: any) => console.error(err)
      });
    }
  }


  deleteUser(id: number) {
    if (confirm(this.text('confirm_delete'))) {
      this.adminService.deleteUser(id).subscribe({
        next: () => {
          this.users = this.users.filter(u => u.id !== id);
          this.loadStats();
        },
        error: (err) => console.error(err)
      });
    }
  }

  editingSpecialiteId: number | null = null;
  editingSpecialiteValue: string = '';

  startEditSpecialite(user: any) {
    this.editingSpecialiteId = user.id;
    this.editingSpecialiteValue = user.specialite || '';
  }

  saveSpecialite(userId: number) {
    this.adminService.updateSpecialite(userId, this.editingSpecialiteValue).subscribe({
      next: () => {
        const u = this.users.find(u => u.id === userId);
        if (u) u.specialite = this.editingSpecialiteValue;
        this.editingSpecialiteId = null;
      },
      error: (err) => console.error(err)
    });
  }

  cancelEditSpecialite() {
    this.editingSpecialiteId = null;
  }

  // ─── Gestion des filières du professeur ───
  editingFilieresId: number | null = null;
  editingFiliereIds: number[] = [];

  startEditFilieres(user: any) {
    this.editingFilieresId = user.id;
    this.editingFiliereIds = (user.filieresProfesseur || []).map((f: any) => f.id);
  }

  toggleFiliere(filiereId: number) {
    const idx = this.editingFiliereIds.indexOf(filiereId);
    if (idx >= 0) this.editingFiliereIds.splice(idx, 1);
    else this.editingFiliereIds.push(filiereId);
  }

  isFiliereSelected(filiereId: number): boolean {
    return this.editingFiliereIds.includes(filiereId);
  }

  saveFilieres(userId: number) {
    this.adminService.updateFilieresProfesseur(userId, this.editingFiliereIds).subscribe({
      next: () => {
        const u = this.users.find(u => u.id === userId);
        if (u) u.filieresProfesseur = this.filieres.filter(f => this.editingFiliereIds.includes(f.id));
        this.editingFilieresId = null;
      },
      error: (err) => console.error(err)
    });
  }

  cancelEditFilieres() {
    this.editingFilieresId = null;
  }

  getProfFiliereLabels(user: any): string {
    const fp = user.filieresProfesseur;
    if (!fp || fp.length === 0) return '';
    return fp.map((f: any) => f.code || f.nom).join(', ');
  }

  // ─── Gestion des matières du professeur ───
  editingMatieresId: number | null = null;
  editingMatiereIds: number[] = [];

  startEditMatieres(user: any) {
    this.editingMatieresId = user.id;
    this.editingMatiereIds = (user.matieresEnseignees || []).map((m: any) => m.id);
  }

  toggleMatiere(matiereId: number) {
    const idx = this.editingMatiereIds.indexOf(matiereId);
    if (idx >= 0) this.editingMatiereIds.splice(idx, 1);
    else this.editingMatiereIds.push(matiereId);
  }

  isMatiereSelected(matiereId: number): boolean {
    return this.editingMatiereIds.includes(matiereId);
  }

  saveMatieres(userId: number) {
    this.adminService.updateMatieresEnseignees(userId, this.editingMatiereIds).subscribe({
      next: () => {
        const u = this.users.find(u => u.id === userId);
        if (u) u.matieresEnseignees = this.allMatieres.filter(m => this.editingMatiereIds.includes(m.id));
        this.editingMatieresId = null;
      },
      error: (err) => console.error(err)
    });
  }

  cancelEditMatieres() {
    this.editingMatieresId = null;
  }

  getProfMatiereLabels(user: any): string {
    const mm = user.matieresEnseignees;
    if (!mm || mm.length === 0) return '';
    const labels = mm.map((m: any) => m.code || m.nom);
    if (labels.length <= 2) return labels.join(', ');
    return labels.slice(0, 2).join(', ') + ` +${labels.length - 2}`;
  }

  getMatieresForProf(user: any): any[] {
    const profFiliereIds = (user.filieresProfesseur || []).map((f: any) => f.id);
    if (profFiliereIds.length === 0) return this.allMatieres;
    return this.allMatieres.filter(m => {
      const mFiliereIds = [
        ...(m.filieres || []).map((f: any) => f.id),
        m.filiere ? m.filiere.id : null
      ].filter((id: any) => id != null);
      return mFiliereIds.some((id: any) => profFiliereIds.includes(id));
    });
  }

  deleteMessage(id: number) {
    if (confirm(this.text('confirm_delete_msg'))) {
      this.adminService.deleteMessage(id).subscribe({
        next: () => {
          this.messages = this.messages.filter((m: any) => m.id !== id);
          this.loadStats();
        },
        error: (err) => console.error(err)
      });
    }
  }



  goBack() { this.location.back(); }
  setTab(tab: string) { this.activeTab = tab; }
  logout() { this.authService.logout(); this.router.navigate(['/login']); }
  goToChat() { this.router.navigate(['/chat']); }
  goToPlanning() { this.router.navigate(['/planning']); }
  goToFilieres() { this.router.navigate(['/gestion/filieres']); }
  goToSalles() { this.router.navigate(['/gestion/salles']); }
  goToMatieres() { this.router.navigate(['/gestion/matieres']); }
  goToKnowledgeBase() { this.router.navigate(['/gestion/knowledge-base']); }
  goToRegistre() { this.router.navigate(['/gestion/registre']); }
  goToPresences() { this.router.navigate(['/gestion/presences']); }
  goToFiches(id?: number) { this.router.navigate(id ? ['/gestion/fiches', id] : ['/gestion/fiches']); }
}

