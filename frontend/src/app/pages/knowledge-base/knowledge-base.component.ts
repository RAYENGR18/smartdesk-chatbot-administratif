import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Location } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { KnowledgeBaseService, KbAudience } from '../../services/knowledge-base.service';
import { NotificationService, Notification } from '../../services/notification.service';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';
import { LanguageService, Lang } from '../../services/language.service';
import { environment } from '../../../environments/environment';

type AudienceTab = 'student' | 'professor';
type SubTab = 'docs' | 'notifs';

@Component({
  selector: 'app-knowledge-base',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './knowledge-base.component.html',
  styleUrl: './knowledge-base.component.css'
})
export class KnowledgeBaseComponent implements OnInit {

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  // ── Tabs ───────────────────────────────────
  audienceTab: AudienceTab = 'student';
  subTab: SubTab = 'docs';

  // ── State docs ─────────────────────────────
  studentDocs: any[] = [];
  profDocs:    any[] = [];
  status: any  = null;
  isDragOver   = false;
  filterType   = 'ALL';

  // ── State notifications ────────────────────
  notifications: Notification[] = [];
  newNotif: Partial<Notification> = {
    title: '', content: '', target: 'STUDENT',
    targetIds: 'ALL', priority: 'INFO', active: true
  };
  // Filières chargées depuis l'API
  filieres: string[] = [];
  selectedFilieres: string[] = [];
  allFilieres = true;
  // Professeurs chargés depuis l'API
  availableProfessors: {id: number, firstName: string, lastName: string, email: string, department: string}[] = [];
  selectedProfIds: number[] = [];
  allProfessors = true;
  showNotifForm = false;

  // ── Loading flags ──────────────────────────
  isLoadingDocs  = false;
  isUploading    = false;
  isReindexing   = false;
  isSavingNotif  = false;

  // ── Messages ───────────────────────────────
  successMsg = '';
  errorMsg   = '';

  // ── Edition TXT ───────────────────────────
  editingDoc:    any     = null;
  editContent:   string  = '';
  isLoadingEdit: boolean = false;
  isSavingEdit:  boolean = false;

  currentLang: Lang = 'fr';

  get dark(): boolean { return this.themeService.isDark; }
  toggleTheme() { this.themeService.toggleTheme(); }

  // ── Translations ───────────────────────────
  text(key: string): string {
    const T: Record<Lang, Record<string, string>> = {
      fr: {
        page_title: 'Base de connaissances RAG',
        page_subtitle: 'Documents + notifications ciblées pour étudiants et professeurs',
        back: 'Retour Admin',
        reindex_all: 'Réindexer tout',
        reindexing: 'Réindexation...',
        rag_service: 'Service RAG',
        rag_ok: 'Opérationnel',
        rag_partial: 'Partiel (pas de clé Groq)',
        rag_missing: 'Clé API manquante',
        docs_students: 'Docs Étudiants',
        docs_professors: 'Docs Professeurs',
        ai_model: 'Modèle IA',
        not_configured: 'Non configuré',
        tab_students: 'Base Étudiants',
        tab_professors: 'Base Professeurs',
        subtab_docs: 'Documents RAG',
        subtab_notifs: 'Informations importantes',
        upload_drag: 'Glissez un document ou',
        upload_click: 'cliquez pour choisir',
        upload_dest: 'Destination',
        upload_hint: 'PDF · DOCX · TXT — Max 10 Mo',
        indexing: 'Indexation en cours...',
        docs_title_s: 'Documents étudiants',
        docs_title_p: 'Documents professeurs',
        filter_all: 'Tous',
        loading: 'Chargement...',
        no_docs: 'Aucun document',
        no_docs_desc: 'Ajoutez des fichiers PDF, DOCX ou TXT pour enrichir la base',
        doc_col: 'Document', type_col: 'Type', size_col: 'Taille',
        modified_col: 'Modifié', actions_col: 'Actions',
        edit_btn: 'Éditer', read_only: 'Lecture seule', delete_btn: 'Supprimer',
        notifs_title_s: 'Informations importantes — Étudiants',
        notifs_title_p: 'Informations importantes — Professeurs',
        new_notif: 'Nouvelle notification',
        create_notif_s: 'Créer une notification — Étudiants',
        create_notif_p: 'Créer une notification — Professeurs',
        field_title: 'Titre *',
        field_message: 'Message *',
        field_priority: 'Priorité',
        prio_info: '🔵 Info',
        prio_warning: '🟡 Important',
        prio_urgent: '🔴 Urgent',
        field_filieres: 'Filières ciblées',
        all_filieres: 'Toutes les filières',
        no_filieres: 'Aucune filière trouvée (aucun étudiant inscrit).',
        field_recipients: 'Destinataires',
        all_profs: 'Tous les professeurs',
        no_profs: 'Aucun professeur trouvé dans la base.',
        hint_filieres: 'Envoyé aux étudiants de :',
        hint_profs: 'professeur(s) sélectionné(s).',
        cancel: 'Annuler',
        send_notif: 'Envoyer la notification',
        sending: 'Envoi...',
        no_notifs: 'Aucune notification',
        no_notifs_desc: 'Créez une information importante pour la transmettre aux',
        students_lbl: 'étudiants',
        professors_lbl: 'professeurs',
        title_placeholder: 'Ex : Absence du professeur Mardi 20 Mai',
        content_placeholder: 'Ex : Le cours de Génie Logiciel du Mardi 20 Mai est annulé...',
        // editor
        editor_subtitle: 'Modifiez le contenu puis sauvegardez pour réindexer automatiquement.',
        editor_loading: 'Chargement...',
        editor_chars: 'caractères',
        editor_save: 'Sauvegarder & Réindexer',
        editor_saving: 'Sauvegarde...',
        file_count: 'fichier(s)',
        'envoyé à': 'Envoyé à'
      },
      en: {
        page_title: 'RAG Knowledge Base',
        page_subtitle: 'Documents + targeted notifications for students and professors',
        back: 'Back to Admin',
        reindex_all: 'Reindex all',
        reindexing: 'Reindexing...',
        rag_service: 'RAG Service',
        rag_ok: 'Operational',
        rag_partial: 'Partial (no Groq key)',
        rag_missing: 'Missing API key',
        docs_students: 'Student Docs',
        docs_professors: 'Professor Docs',
        ai_model: 'AI Model',
        not_configured: 'Not configured',
        tab_students: 'Student Base',
        tab_professors: 'Professor Base',
        subtab_docs: 'RAG Documents',
        subtab_notifs: 'Important info',
        upload_drag: 'Drag a document or',
        upload_click: 'click to choose',
        upload_dest: 'Destination',
        upload_hint: 'PDF · DOCX · TXT — Max 10 MB',
        indexing: 'Indexing...',
        docs_title_s: 'Student documents',
        docs_title_p: 'Professor documents',
        filter_all: 'All',
        loading: 'Loading...',
        no_docs: 'No documents',
        no_docs_desc: 'Add PDF, DOCX or TXT files to enrich the base',
        doc_col: 'Document', type_col: 'Type', size_col: 'Size',
        modified_col: 'Modified', actions_col: 'Actions',
        edit_btn: 'Edit', read_only: 'Read-only', delete_btn: 'Delete',
        notifs_title_s: 'Important info — Students',
        notifs_title_p: 'Important info — Professors',
        new_notif: 'New notification',
        create_notif_s: 'Create notification — Students',
        create_notif_p: 'Create notification — Professors',
        field_title: 'Title *',
        field_message: 'Message *',
        field_priority: 'Priority',
        prio_info: '🔵 Info',
        prio_warning: '🟡 Important',
        prio_urgent: '🔴 Urgent',
        field_filieres: 'Target programs',
        all_filieres: 'All programs',
        no_filieres: 'No programs found (no students enrolled).',
        field_recipients: 'Recipients',
        all_profs: 'All professors',
        no_profs: 'No professors found in the database.',
        hint_filieres: 'Sent to students of:',
        hint_profs: 'professor(s) selected.',
        cancel: 'Cancel',
        send_notif: 'Send notification',
        sending: 'Sending...',
        no_notifs: 'No notifications',
        no_notifs_desc: 'Create an important notification to send to',
        students_lbl: 'students',
        professors_lbl: 'professors',
        title_placeholder: 'e.g. Professor absence Tuesday 20 May',
        content_placeholder: 'e.g. The Software Engineering class on Tuesday 20 May is cancelled...',
        editor_subtitle: 'Edit the content then save to automatically reindex.',
        editor_loading: 'Loading...',
        editor_chars: 'characters',
        editor_save: 'Save & Reindex',
        editor_saving: 'Saving...',
        file_count: 'file(s)',
        'envoyé à': 'Sent to'
      },
      ar: {
        page_title: 'قاعدة المعرفة RAG',
        page_subtitle: 'وثائق + إشعارات موجهة للطلاب والأساتذة',
        back: 'العودة للإدارة',
        reindex_all: 'إعادة الفهرسة الكاملة',
        reindexing: 'جار الفهرسة...',
        rag_service: 'خدمة RAG',
        rag_ok: 'تعمل بشكل سليم',
        rag_partial: 'جزئية (لا مفتاح Groq)',
        rag_missing: 'مفتاح API مفقود',
        docs_students: 'وثائق الطلاب',
        docs_professors: 'وثائق الأساتذة',
        ai_model: 'نموذج الذكاء الاصطناعي',
        not_configured: 'غير مضبوط',
        tab_students: 'قاعدة الطلاب',
        tab_professors: 'قاعدة الأساتذة',
        subtab_docs: 'وثائق RAG',
        subtab_notifs: 'معلومات مهمة',
        upload_drag: 'اسحب وثيقة أو',
        upload_click: 'انقر للاختيار',
        upload_dest: 'الوجهة',
        upload_hint: 'PDF · DOCX · TXT — الحد الأقصى 10 MB',
        indexing: 'جار الفهرسة...',
        docs_title_s: 'وثائق الطلاب',
        docs_title_p: 'وثائق الأساتذة',
        filter_all: 'الكل',
        loading: 'جار التحميل...',
        no_docs: 'لا توجد وثائق',
        no_docs_desc: 'أضف ملفات PDF أو DOCX أو TXT لإثراء القاعدة',
        doc_col: 'الوثيقة', type_col: 'النوع', size_col: 'الحجم',
        modified_col: 'التعديل', actions_col: 'إجراءات',
        edit_btn: 'تعديل', read_only: 'للقراءة فقط', delete_btn: 'حذف',
        notifs_title_s: 'معلومات مهمة — الطلاب',
        notifs_title_p: 'معلومات مهمة — الأساتذة',
        new_notif: 'إشعار جديد',
        create_notif_s: 'إنشاء إشعار — الطلاب',
        create_notif_p: 'إنشاء إشعار — الأساتذة',
        field_title: 'العنوان *',
        field_message: 'الرسالة *',
        field_priority: 'الأولوية',
        prio_info: '🔵 معلومة',
        prio_warning: '🟡 مهم',
        prio_urgent: '🔴 عاجل',
        field_filieres: 'الاختصاصات المستهدفة',
        all_filieres: 'كل الاختصاصات',
        no_filieres: 'لا توجد اختصاصات (لا يوجد طلاب مسجلون).',
        field_recipients: 'المستلمون',
        all_profs: 'كل الأساتذة',
        no_profs: 'لا يوجد أساتذة في القاعدة.',
        hint_filieres: 'يُرسل إلى طلاب:',
        hint_profs: 'أستاذ(ة) محدد(ة).',
        cancel: 'إلغاء',
        send_notif: 'إرسال الإشعار',
        sending: 'جار الإرسال...',
        no_notifs: 'لا توجد إشعارات',
        no_notifs_desc: 'أنشئ معلومة مهمة لإرسالها إلى',
        students_lbl: 'الطلاب',
        professors_lbl: 'الأساتذة',
        title_placeholder: 'مثال: غياب الأستاذ يوم الثلاثاء 20 ماي',
        content_placeholder: 'مثال: تم إلغاء حصة هندسة البرمجيات يوم الثلاثاء 20 ماي...',
        editor_subtitle: 'عدّل المحتوى ثم احفظ لإعادة الفهرسة تلقائياً.',
        editor_loading: 'جار التحميل...',
        editor_chars: 'حرف',
        editor_save: 'حفظ وإعادة الفهرسة',
        editor_saving: 'جار الحفظ...',
        file_count: 'ملف(ات)',
        'envoyé à': 'أُرسل إلى'
      }
    };
    return T[this.currentLang]?.[key] ?? T['fr'][key] ?? key;
  }

  get currentAudience(): KbAudience {
    return this.audienceTab === 'student' ? 'student' : 'professor';
  }

  get currentDocs(): any[] {
    const docs = this.audienceTab === 'student' ? this.studentDocs : this.profDocs;
    if (this.filterType === 'ALL') return docs;
    return docs.filter(d => d.type === this.filterType);
  }

  get studentNotifs(): Notification[] {
    return this.notifications.filter(n => n.target === 'STUDENT');
  }
  get profNotifs(): Notification[] {
    return this.notifications.filter(n => n.target === 'PROFESSOR');
  }
  get currentNotifs(): Notification[] {
    return this.audienceTab === 'student' ? this.studentNotifs : this.profNotifs;
  }

  constructor(
    private kbService:    KnowledgeBaseService,
    private notifSvc:     NotificationService,
    private authService:  AuthService,
    private themeService: ThemeService,
    private langService:  LanguageService,
    private router:       Router,
    private location:     Location,
    private http:         HttpClient
  ) {
    this.langService.currentLang$.subscribe(l => this.currentLang = l);
  }

  ngOnInit() {
    if (this.authService.getRole() !== 'ADMIN') {
      this.router.navigate(['/login']); return;
    }
    this.loadStatus();
    this.loadDocs('student');
    this.loadDocs('professor');
    this.loadNotifications();
    this.loadFilieres();
    this.loadProfessors();
  }

  // ── Navigation tabs ────────────────────────
  setAudienceTab(tab: AudienceTab) {
    this.audienceTab = tab;
    this.filterType  = 'ALL';
    this.newNotif.target = tab === 'student' ? 'STUDENT' : 'PROFESSOR';
  }

  setSubTab(sub: SubTab) { this.subTab = sub; }

  // ── Chargement ────────────────────────────
  loadStatus() {
    this.kbService.getStatus().subscribe({
      next: s => this.status = s,
      error: () => this.status = null
    });
  }

  loadDocs(audience: KbAudience) {
    this.isLoadingDocs = true;
    this.kbService.listDocuments(audience).subscribe({
      next: res => {
        if (audience === 'student') this.studentDocs = res.documents || [];
        else                        this.profDocs    = res.documents || [];
        this.isLoadingDocs = false;
      },
      error: () => {
        this.showError('Impossible de charger les documents.');
        this.isLoadingDocs = false;
      }
    });
  }

  loadNotifications() {
    this.notifSvc.getAll().subscribe({
      next: list => this.notifications = list,
      error: ()  => {}
    });
  }

  loadFilieres() {
    // Load all defined filières from the faculty DB (not just from enrolled students)
    this.http.get<any[]>(`${environment.apiBaseUrl}/api/filieres`).subscribe({
      next: list => {
        // Use code as the identifier (matches student's fieldOfStudy field)
        this.filieres = list.map(f => f.code || f.nom).filter(Boolean);
      },
      error: () => {
        // Fallback: try to get from enrolled students
        this.http.get<string[]>(`${environment.apiBaseUrl}/api/student/filieres`).subscribe({
          next: list2 => this.filieres = list2,
          error: () => {}
        });
      }
    });
  }

  loadProfessors() {
    this.http.get<any[]>(`${environment.apiBaseUrl}/api/student/professors`).subscribe({
      next: list => this.availableProfessors = list,
      error: () => {}
    });
  }

  // ── Upload ────────────────────────────────
  triggerUpload() { this.fileInput.nativeElement.click(); }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) { this.uploadFile(input.files[0]); input.value = ''; }
  }

  onDrop(event: DragEvent) {
    event.preventDefault(); this.isDragOver = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.uploadFile(file);
  }
  onDragOver(e: DragEvent) { e.preventDefault(); this.isDragOver = true; }
  onDragLeave() { this.isDragOver = false; }

  uploadFile(file: File) {
    const ext = file.name.split('.').pop()?.toLowerCase() || '';
    if (!['pdf', 'docx', 'txt'].includes(ext)) {
      this.showError('Format non supporté. Utilisez PDF, DOCX ou TXT.'); return;
    }
    this.isUploading = true;
    this.kbService.uploadDocument(file, this.currentAudience).subscribe({
      next: res => {
        this.isUploading = false;
        this.showSuccess(res.message || `"${file.name}" indexé avec succès !`);
        this.loadDocs(this.currentAudience);
        this.loadStatus();
      },
      error: () => {
        this.isUploading = false;
        this.showError("Erreur lors de l'upload. Vérifiez que le service NLP est démarré.");
      }
    });
  }

  // ── Suppression ───────────────────────────
  deleteDoc(doc: any) {
    if (!confirm(`Supprimer "${doc.name}" de la base ${this.audienceTab} ?`)) return;
    this.kbService.deleteDocument(doc.name, this.currentAudience).subscribe({
      next: () => {
        this.showSuccess(`"${doc.name}" supprimé et base réindexée.`);
        this.loadDocs(this.currentAudience);
        this.loadStatus();
      },
      error: () => this.showError('Erreur lors de la suppression.')
    });
  }

  // ── Réindexation ─────────────────────────
  reindexAll() {
    if (!confirm('Réindexer toute la base de connaissances ?')) return;
    this.isReindexing = true;
    this.kbService.reindex('all').subscribe({
      next: res => {
        this.isReindexing = false;
        this.showSuccess(res.message || 'Base réindexée avec succès !');
        this.loadStatus();
      },
      error: () => { this.isReindexing = false; this.showError('Erreur réindexation.'); }
    });
  }

  // ── Edition TXT ───────────────────────────
  openEdit(doc: any) {
    this.editingDoc = doc; this.editContent = ''; this.isLoadingEdit = true;
    this.kbService.getContent(doc.name, this.currentAudience).subscribe({
      next: res => { this.editContent = res.content || ''; this.isLoadingEdit = false; },
      error: () => {
        this.isLoadingEdit = false;
        this.showError('Impossible de lire le contenu. Vérifiez que le service NLP est démarré.');
      }
    });
  }

  saveEdit() {
    if (!this.editingDoc) return;
    this.isSavingEdit = true;
    this.kbService.updateContent(this.editingDoc.name, this.editContent, this.currentAudience).subscribe({
      next: () => {
        this.isSavingEdit = false;
        this.showSuccess(`"${this.editingDoc.name}" mis à jour et réindexé.`);
        this.editingDoc = null;
        this.loadDocs(this.currentAudience);
      },
      error: () => { this.isSavingEdit = false; this.showError('Erreur lors de la sauvegarde.'); }
    });
  }

  cancelEdit() { this.editingDoc = null; this.editContent = ''; }

  // ── Notifications ────────────────────────
  toggleNotifForm() {
    this.showNotifForm = !this.showNotifForm;
    if (this.showNotifForm) {
      this.newNotif = {
        title: '', content: '',
        target: this.audienceTab === 'student' ? 'STUDENT' : 'PROFESSOR',
        targetIds: 'ALL', priority: 'INFO', active: true
      };
      this.selectedFilieres = [];
      this.allFilieres = true;
      this.selectedProfIds = [];
      this.allProfessors = true;
    }
  }

  // ── Professor targeting ────────────────────
  toggleProf(id: number) {
    const idx = this.selectedProfIds.indexOf(id);
    if (idx === -1) this.selectedProfIds.push(id);
    else            this.selectedProfIds.splice(idx, 1);
    this.allProfessors = this.selectedProfIds.length === 0;
  }

  isProfSelected(id: number) { return this.selectedProfIds.includes(id); }

  toggleAllProfs() {
    this.allProfessors = true;
    this.selectedProfIds = [];
    this.newNotif.targetIds = 'ALL';
  }

  toggleFiliere(f: string) {
    const idx = this.selectedFilieres.indexOf(f);
    if (idx === -1) this.selectedFilieres.push(f);
    else            this.selectedFilieres.splice(idx, 1);
    this.allFilieres = this.selectedFilieres.length === 0;
  }

  isFiliereSelected(f: string) { return this.selectedFilieres.includes(f); }

  toggleAllFilieres() {
    this.allFilieres = true;
    this.selectedFilieres = [];
    this.newNotif.targetIds = 'ALL';
  }

  saveNotif() {
    if (!this.newNotif.title?.trim() || !this.newNotif.content?.trim()) {
      this.showError('Titre et contenu sont obligatoires.'); return;
    }
    if (this.audienceTab === 'student') {
      this.newNotif.targetIds = this.allFilieres || this.selectedFilieres.length === 0
        ? 'ALL' : this.selectedFilieres.join(',');
    } else {
      this.newNotif.targetIds = this.allProfessors || this.selectedProfIds.length === 0
        ? 'ALL' : this.selectedProfIds.join(',');
    }
    this.isSavingNotif = true;
    this.notifSvc.create(this.newNotif).subscribe({
      next: () => {
        this.isSavingNotif = false;
        this.showNotifForm = false;
        this.showSuccess('Notification envoyée avec succès !');
        this.loadNotifications();
      },
      error: () => { this.isSavingNotif = false; this.showError('Erreur envoi notification.'); }
    });
  }

  toggleNotif(notif: Notification) {
    this.notifSvc.toggle(notif.id!).subscribe({
      next: () => this.loadNotifications(),
      error: () => this.showError('Erreur.')
    });
  }

  deleteNotif(notif: Notification) {
    if (!confirm(`Supprimer la notification "${notif.title}" ?`)) return;
    this.notifSvc.delete(notif.id!).subscribe({
      next: () => { this.showSuccess('Notification supprimée.'); this.loadNotifications(); },
      error: () => this.showError('Erreur suppression.')
    });
  }

  priorityLabel(p: string) {
    return p === 'URGENT' ? this.text('prio_urgent') : p === 'WARNING' ? this.text('prio_warning') : this.text('prio_info');
  }
  priorityClass(p: string) {
    return p === 'URGENT' ? 'prio-urgent' : p === 'WARNING' ? 'prio-warning' : 'prio-info';
  }
  targetLabel(n: Notification) {
    if (n.targetIds === 'ALL') {
      return n.target === 'STUDENT'
        ? `${this.text('filter_all')} ${this.text('students_lbl')}`
        : `${this.text('filter_all')} ${this.text('professors_lbl')}`;
    }
    return n.targetIds;
  }
  activeLabel()   { return this.currentLang === 'ar' ? 'نشط'   : this.currentLang === 'en' ? 'Active'   : 'Actif';   }
  inactiveLabel() { return this.currentLang === 'ar' ? 'معطّل' : this.currentLang === 'en' ? 'Inactive' : 'Inactif'; }

  // ── Helpers docs ─────────────────────────
  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }
  typeIcon(type: string) { return type==='PDF'?'📄':type==='DOCX'?'📝':'📃'; }
  typeBadgeClass(type: string) { return type==='PDF'?'badge-pdf':type==='DOCX'?'badge-docx':'badge-txt'; }

  private showSuccess(msg: string) {
    this.successMsg = msg; this.errorMsg = '';
    setTimeout(() => this.successMsg = '', 5000);
  }
  private showError(msg: string) {
    this.errorMsg = msg; this.successMsg = '';
    setTimeout(() => this.errorMsg = '', 7000);
  }

  goBack() { this.location.back(); }
}
