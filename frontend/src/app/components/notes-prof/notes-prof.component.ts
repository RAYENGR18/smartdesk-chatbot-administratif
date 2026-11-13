import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NoteService } from '../../services/note.service';
import { AuthService } from '../../services/auth.service';
import { MatiereService } from '../../services/matiere.service';
import { AdminService } from '../../services/admin.service';
import { LanguageService } from '../../services/language.service';

@Component({
  selector: 'app-notes-prof',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notes-prof.component.html',
  styleUrl: './notes-prof.component.css'
})
export class NotesProfComponent implements OnInit {

  notes: any[]    = [];
  etudiants: any[] = [];
  matieres: any[]  = [];

  newNote = {
    etudiantId:      '' as string | number,
    matiereId:       '' as string | number,
    typeEvaluation:  'Examen',
    valeur:          null as number | null,
    commentaire:     ''
  };

  // Edit mode
  editingNoteId: number | null = null;

  profId      = 0;
  isLoading   = false;
  isSubmitting = false;
  successMsg  = '';
  errorMsg    = '';
  currentLang = 'fr';

  // Search / filter
  searchQuery = '';
  filterType  = '';
  filterMatiere = '';

  constructor(
    private noteService:    NoteService,
    private authService:    AuthService,
    private adminService:   AdminService,
    private matiereService: MatiereService,
    private languageService: LanguageService
  ) {
    this.languageService.currentLang$.subscribe(lang => {
      this.currentLang = lang;
    });
  }

  // ============================================================
  // TRANSLATIONS
  // ============================================================
  text(key: string): string {
    const t: Record<string, Record<string, string>> = {
      fr: {
        title:              'Saisie des notes',
        subtitle:           'Gérez les évaluations de vos étudiants en temps réel.',
        new_evaluation:     'Nouvelle évaluation',
        edit_evaluation:    'Modifier la note',
        student:            'Étudiant',
        select_student:     '— Sélectionner un étudiant —',
        subject:            'Matière',
        select_subject:     '— Sélectionner la matière —',
        evaluation_type:    'Type d\'évaluation',
        grade20:            'Note /20',
        grade_placeholder:  '0.00 – 20.00',
        comment_optional:   'Commentaire (optionnel)',
        comment_placeholder:'Remarques sur la performance…',
        save_grade:         'Enregistrer',
        update_grade:       'Mettre à jour',
        cancel:             'Annuler',
        saved_grades:       'Tableau des notes',
        loading:            'Chargement…',
        searching:          'Rechercher un étudiant ou une matière…',
        all_types:          'Tous les types',
        all_subjects:       'Toutes les matières',
        type:               'Type',
        comment:            'Commentaire',
        date:               'Date',
        action:             'Actions',
        empty:              'Aucune note enregistrée.',
        required_error:     'Veuillez sélectionner un étudiant et une matière.',
        invalid_grade:      'La note doit être comprise entre 0 et 20.',
        success_added:      '✓ Note enregistrée avec succès.',
        success_updated:    '✓ Note mise à jour.',
        success_deleted:    '✓ Note supprimée.',
        add_error:          'Erreur lors de l\'enregistrement. Veuillez réessayer.',
        confirm_delete:     'Supprimer définitivement cette note ?',
        total_notes:        'Notes saisies',
        nb_pass:            'Étudiants admis',
        nb_fail:            'À rattraper',
        moyenne:            'Moyenne générale',
        validated:          'Admis',
        retake:             'Rattrapage',
        empty_dash:         '—',
      },
      en: {
        title:              'Grade entry',
        subtitle:           'Manage your students\' evaluations in real time.',
        new_evaluation:     'New evaluation',
        edit_evaluation:    'Edit grade',
        student:            'Student',
        select_student:     '— Select a student —',
        subject:            'Subject',
        select_subject:     '— Select a subject —',
        evaluation_type:    'Evaluation type',
        grade20:            'Grade /20',
        grade_placeholder:  '0.00 – 20.00',
        comment_optional:   'Comment (optional)',
        comment_placeholder:'Remarks on performance…',
        save_grade:         'Save',
        update_grade:       'Update',
        cancel:             'Cancel',
        saved_grades:       'Grade board',
        loading:            'Loading…',
        searching:          'Search student or subject…',
        all_types:          'All types',
        all_subjects:       'All subjects',
        type:               'Type',
        comment:            'Comment',
        date:               'Date',
        action:             'Actions',
        empty:              'No grades recorded yet.',
        required_error:     'Please select a student and a subject.',
        invalid_grade:      'Grade must be between 0 and 20.',
        success_added:      '✓ Grade saved successfully.',
        success_updated:    '✓ Grade updated.',
        success_deleted:    '✓ Grade deleted.',
        add_error:          'Error saving grade. Please try again.',
        confirm_delete:     'Permanently delete this grade?',
        total_notes:        'Grades entered',
        nb_pass:            'Students passed',
        nb_fail:            'Retake required',
        moyenne:            'Class average',
        validated:          'Passed',
        retake:             'Retake',
        empty_dash:         '—',
      },
      ar: {
        title:              'إدخال الأعداد',
        subtitle:           'أدر تقييمات طلبتك بشكل فوري.',
        new_evaluation:     'تقييم جديد',
        edit_evaluation:    'تعديل العدد',
        student:            'الطالب',
        select_student:     '— اختر طالبا —',
        subject:            'المادة',
        select_subject:     '— اختر المادة —',
        evaluation_type:    'نوع التقييم',
        grade20:            'العدد /20',
        grade_placeholder:  '0.00 – 20.00',
        comment_optional:   'ملاحظة (اختياري)',
        comment_placeholder:'ملاحظات حول الأداء…',
        save_grade:         'حفظ',
        update_grade:       'تحديث',
        cancel:             'إلغاء',
        saved_grades:       'قائمة الأعداد',
        loading:            'جار التحميل…',
        searching:          'ابحث عن طالب أو مادة…',
        all_types:          'جميع الأنواع',
        all_subjects:       'جميع المواد',
        type:               'النوع',
        comment:            'ملاحظة',
        date:               'التاريخ',
        action:             'إجراءات',
        empty:              'لا توجد أعداد مسجلة.',
        required_error:     'يرجى اختيار الطالب والمادة.',
        invalid_grade:      'يجب أن يكون العدد بين 0 و20.',
        success_added:      '✓ تم حفظ العدد بنجاح.',
        success_updated:    '✓ تم تحديث العدد.',
        success_deleted:    '✓ تم حذف العدد.',
        add_error:          'حدث خطأ. يرجى المحاولة مجددا.',
        confirm_delete:     'حذف هذا العدد نهائيا؟',
        total_notes:        'أعداد مسجلة',
        nb_pass:            'طلبة ناجحون',
        nb_fail:            'راسبون',
        moyenne:            'المعدل العام',
        validated:          'ناجح',
        retake:             'راسب',
        empty_dash:         '—',
      }
    };
    return t[this.currentLang]?.[key] ?? t['fr'][key] ?? key;
  }

  typeLabel(v: string): string {
    const lbl: Record<string, Record<string, string>> = {
      DS:      { fr: 'DS / CC',    en: 'Quiz',    ar: 'فرض' },
      Examen:  { fr: 'Examen',     en: 'Exam',    ar: 'امتحان' },
      TP:      { fr: 'TP / TD',    en: 'Lab',     ar: 'تطبيقي' },
      Projet:  { fr: 'Projet',     en: 'Project', ar: 'مشروع' }
    };
    return lbl[v]?.[this.currentLang] ?? v;
  }

  // ============================================================
  // LIFECYCLE
  // ============================================================
  ngOnInit() {
    // Retrieve professor ID — userId is stored at login for all roles
    const storedId = this.authService.getUserId() || this.authService.getStoredProfile()?.id;
    if (storedId) {
      this.profId = Number(storedId);
    }

    this.loadData();
    if (this.profId) this.loadNotes();
  }

  // ============================================================
  // DATA LOADING
  // ============================================================
  loadData() {
    this.matiereService.getAll().subscribe({
      next: (m) => this.matieres = m,
      error: (err) => console.error('Matieres load error:', err)
    });

    this.adminService.getUsers().subscribe({
      next: (users) => {
        // Accept both USER and STUDENT roles for compatibility
        this.etudiants = (users as any[]).filter(
          (u) => u.role === 'USER' || u.role === 'STUDENT' || u.role === 'ETUDIANT'
        ).sort((a: any, b: any) =>
          `${a.lastName}${a.firstName}`.localeCompare(`${b.lastName}${b.firstName}`)
        );
      },
      error: (err) => console.error('Users load error:', err)
    });
  }

  /**
   * @deprecated — La saisie des notes par le professeur a été désactivée.
   * Les notes sont désormais gérées par l'administrateur via import fichier.
   */
  loadNotes() {
    // Anciennes notes professeur supprimées — composant conservé mais inactif
    this.notes = [];
    this.isLoading = false;
  }

  // ============================================================
  // STATISTICS (computed)
  // ============================================================
  get totalNotes()  { return this.filteredNotes.length; }
  get nbPass()      { return this.filteredNotes.filter(n => n.valeur >= 10).length; }
  get nbFail()      { return this.filteredNotes.filter(n => n.valeur < 10).length; }
  get moyenne(): number {
    const n = this.filteredNotes;
    if (!n.length) return 0;
    return n.reduce((s: number, x: any) => s + (x.valeur ?? 0), 0) / n.length;
  }
  get passRate(): number {
    return this.totalNotes ? Math.round((this.nbPass / this.totalNotes) * 100) : 0;
  }

  // ============================================================
  // FILTERED NOTES (search + type + matiere)
  // ============================================================
  get filteredNotes(): any[] {
    const q = this.searchQuery.trim().toLowerCase();
    return this.notes.filter(n => {
      const matchSearch = !q ||
        `${n.etudiant?.firstName} ${n.etudiant?.lastName}`.toLowerCase().includes(q) ||
        (n.matiere?.nom ?? '').toLowerCase().includes(q) ||
        (n.etudiant?.studentId ?? '').toLowerCase().includes(q);
      const matchType    = !this.filterType    || n.typeEvaluation === this.filterType;
      const matchMatiere = !this.filterMatiere || String(n.matiere?.id) === this.filterMatiere;
      return matchSearch && matchType && matchMatiere;
    });
  }

  // ============================================================
  // DATE FORMATTING — handles both String ISO and int[] from JPA
  // ============================================================
  formatDate(d: any): string {
    if (!d) return this.text('empty_dash');
    if (Array.isArray(d)) {
      const [y, mo, day, h = 0, mi = 0] = d;
      return `${String(day).padStart(2,'0')}/${String(mo).padStart(2,'0')}/${y}  ${String(h).padStart(2,'0')}:${String(mi).padStart(2,'0')}`;
    }
    try {
      const dt = new Date(d);
      return dt.toLocaleDateString('fr-FR') + '  ' +
             dt.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    } catch {
      return String(d);
    }
  }

  // ============================================================
  // FORM ACTIONS
  // ============================================================
  private showSuccess(msg: string) {
    this.successMsg = msg;
    this.errorMsg   = '';
    setTimeout(() => this.successMsg = '', 4000);
  }

  private showError(msg: string) {
    this.errorMsg   = msg;
    this.successMsg = '';
    setTimeout(() => this.errorMsg = '', 5000);
  }

  private resetForm() {
    this.newNote = {
      etudiantId:     '',
      matiereId:      '',
      typeEvaluation: 'Examen',
      valeur:         null,
      commentaire:    ''
    };
    this.editingNoteId = null;
  }

  private validate(): boolean {
    if (!this.newNote.etudiantId || !this.newNote.matiereId) {
      this.showError(this.text('required_error'));
      return false;
    }
    const v = Number(this.newNote.valeur);
    if (this.newNote.valeur === null || isNaN(v) || v < 0 || v > 20) {
      this.showError(this.text('invalid_grade'));
      return false;
    }
    return true;
  }

  /**
   * @deprecated — La saisie manuelle des notes par le professeur a été désactivée.
   * Les notes sont désormais importées par l'administrateur.
   */
  ajouterNote() {
    this.showError('Cette fonctionnalité est désactivée. Les notes sont gérées par l\'administrateur.');
  }

  editNote(note: any) {
    this.editingNoteId          = note.id;
    this.newNote.etudiantId     = note.etudiant?.id ?? '';
    this.newNote.matiereId      = note.matiere?.id ?? '';
    this.newNote.typeEvaluation = note.typeEvaluation;
    this.newNote.valeur         = note.valeur;
    this.newNote.commentaire    = note.commentaire ?? '';
    // Scroll to form
    document.querySelector('.note-form-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  cancelEdit() {
    this.resetForm();
    this.errorMsg   = '';
    this.successMsg = '';
  }

  supprimerNote(id: number) {
    if (!confirm(this.text('confirm_delete'))) return;
    this.noteService.supprimerNote(id).subscribe({
      next: () => {
        this.showSuccess(this.text('success_deleted'));
        this.loadNotes();
      },
      error: () => this.showError(this.text('add_error'))
    });
  }
}
