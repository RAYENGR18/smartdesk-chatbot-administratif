import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { FicheService, LigneFiche, TypeFiche } from '../../services/fiche.service';

/**
 * Fiches de l'administration : tout ce qui concerne un étudiant (CIN, identité,
 * registre, scolarité, notes et moyennes, emploi du temps, documents) ou un professeur
 * (matières, emploi du temps, badge et présences) au même endroit, imprimable.
 */
@Component({
  selector: 'app-fiches',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fiches.component.html',
  styleUrls: ['./fiches.component.css']
})
export class FichesComponent implements OnInit {

  readonly jours = ['LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'];
  readonly niveaux = ['L1', 'L2', 'L3', 'M1', 'M2', 'ING1', 'ING2', 'ING3'];
  readonly aujourdhui = new Date();

  type: TypeFiche = 'ETUDIANT';
  recherche = '';
  filiereId: number | null = null;
  niveau = '';
  filieres: { id: number; code: string; nom: string }[] = [];

  liste: LigneFiche[] = [];
  total = 0;
  page = 0;
  pages = 0;
  chargementListe = false;

  selection: number | null = null;
  fiche: any = null;
  chargementFiche = false;
  erreur = '';

  constructor(private service: FicheService, private http: HttpClient,
              private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.http.get<any[]>(`${environment.apiBaseUrl}/api/auth/filieres`)
      .subscribe({ next: (f) => this.filieres = f });
    const qp = this.route.snapshot.queryParamMap;
    if (qp.get('type') === 'PROFESSEUR') this.type = 'PROFESSEUR';
    this.charger(0);
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) this.ouvrir(id);
  }

  changerType(t: TypeFiche): void {
    if (this.type === t) return;
    this.type = t;
    this.filiereId = null;
    this.niveau = '';
    this.charger(0);
  }

  charger(page: number): void {
    this.chargementListe = true;
    this.service.rechercher(this.type, this.recherche, this.filiereId, this.niveau, page).subscribe({
      next: (r) => {
        this.liste = r.contenu;
        this.total = r.total;
        this.page = r.page;
        this.pages = r.pages;
        this.chargementListe = false;
      },
      error: (e) => {
        this.chargementListe = false;
        this.erreur = e.error?.message || 'Chargement de la liste impossible.';
      }
    });
  }

  ouvrir(id: number): void {
    this.selection = id;
    this.chargementFiche = true;
    this.erreur = '';
    this.router.navigate(['/gestion/fiches', id], { replaceUrl: true });
    this.service.fiche(id).subscribe({
      next: (f) => {
        this.fiche = f;
        this.chargementFiche = false;
        if (f.type === 'PROFESSEUR' && this.type !== 'PROFESSEUR') { this.type = 'PROFESSEUR'; this.charger(0); }
        if (f.type === 'ETUDIANT' && this.type !== 'ETUDIANT') { this.type = 'ETUDIANT'; this.charger(0); }
      },
      error: (e) => {
        this.chargementFiche = false;
        this.fiche = null;
        this.erreur = e.error?.message || 'Fiche introuvable.';
      }
    });
  }

  imprimer(): void {
    window.print();
  }

  retour(): void {
    this.router.navigate(['/admin']);
  }

  // ── Affichage ──────────────────────────────────────────────────────

  creneauxDu(jour: string): any[] {
    return (this.fiche?.emploiDuTemps || []).filter((c: any) => c.jour === jour);
  }

  joursAvecCours(): string[] {
    return this.jours.filter(j => this.creneauxDu(j).length > 0);
  }

  types(resultats: any[]): string[] {
    const t = new Set<string>();
    (resultats || []).forEach(r => Object.keys(r.notes || {}).forEach(k => t.add(k)));
    const ordre = ['DS', 'TP', 'Projet', 'Examen', 'Rattrapage'];
    return [...t].sort((a, b) => (ordre.indexOf(a) + 1 || 99) - (ordre.indexOf(b) + 1 || 99));
  }

  classeNote(v: number | null | undefined): string {
    if (v === null || v === undefined) return '';
    return v >= 14 ? 'bien' : v >= 10 ? 'ok' : 'ko';
  }

  jourCourt(j: string): string {
    return j.charAt(0) + j.slice(1, 3).toLowerCase();
  }

  jourSemaine(iso: string): string {
    return ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'][new Date(iso + 'T00:00:00').getDay()] || '';
  }

  libelleStatut(s: string): string {
    return ({ PRESENT: 'Présent', TERMINE: 'Terminé', DEPART_NON_POINTE: 'Départ non pointé' } as any)[s] || s;
  }

  initiales(l: { prenom?: string; nom?: string }): string {
    return ((l.prenom || '?').charAt(0) + (l.nom || '').charAt(0)).toUpperCase();
  }

  valeur(v: any): string {
    return v === null || v === undefined || v === '' ? '—' : String(v);
  }
}
