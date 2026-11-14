import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BadgeProfesseur, Presence, PresenceService } from '../../services/presence.service';

/**
 * Présence des professeurs : pointage par badge RFID (lecteur Arduino + programme
 * scripts\pointage.cmd), consultation, export, attribution des badges et simulation.
 */
@Component({
  selector: 'app-presences',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './presences.component.html',
  styleUrls: ['./presences.component.css']
})
export class PresencesComponent implements OnInit, OnDestroy {

  resume: any = {};
  presences: Presence[] = [];
  badges: BadgeProfesseur[] = [];
  inconnus: any[] = [];
  passages: any[] = [];

  debut = this.aujourdhui();
  fin = this.aujourdhui();
  professeurId: number | null = null;

  saisieBadge: Record<number, string> = {};
  attributionInconnu: Record<string, number | null> = {};
  simulationProf: number | null = null;

  message = '';
  erreur = '';
  private timer: any;

  constructor(private service: PresenceService, private router: Router) {}

  ngOnInit(): void {
    this.toutCharger();
    // Rafraîchissement automatique : les passages de badge apparaissent sans recharger la page
    this.timer = setInterval(() => this.rafraichir(), 15000);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  private aujourdhui(): string {
    const d = new Date();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const j = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${m}-${j}`;
  }

  toutCharger(): void {
    this.rafraichir();
    this.chargerBadges();
  }

  rafraichir(): void {
    this.service.resume().subscribe({ next: (r) => this.resume = r });
    this.rechercher();
    this.service.badgesInconnus().subscribe({ next: (l) => this.inconnus = l });
    this.service.passages().subscribe({ next: (l) => this.passages = l });
  }

  rechercher(): void {
    this.service.rechercher(this.debut, this.fin, this.professeurId).subscribe({
      next: (l) => { this.presences = l; this.erreur = ''; },
      error: (e) => this.erreur = e.error?.message || 'Chargement des présences impossible.'
    });
  }

  periodeAujourdhui(): void {
    this.debut = this.fin = this.aujourdhui();
    this.rechercher();
  }

  periodeSemaine(): void {
    const d = new Date();
    const lundi = new Date(d);
    lundi.setDate(d.getDate() - ((d.getDay() + 6) % 7));
    const f = (x: Date) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
    this.debut = f(lundi);
    this.fin = this.aujourdhui();
    this.rechercher();
  }

  chargerBadges(): void {
    this.service.badges().subscribe({
      next: (l) => {
        this.badges = l;
        l.forEach(b => this.saisieBadge[b.id] = b.badge || '');
        if (!this.simulationProf && l.length) this.simulationProf = l[0].id;
      }
    });
  }

  get totalMinutes(): number {
    return this.presences.reduce((s, p) => s + (p.dureeMinutes || 0), 0);
  }

  formatMinutes(m: number): string {
    return `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}`;
  }

  exporter(): void {
    this.service.exporter(this.debut, this.fin, this.professeurId).subscribe({
      next: (res) => {
        const blob = res.body;
        if (!blob) return;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `presences_${this.debut}_${this.fin}.csv`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
      },
      error: () => this.erreur = 'Export impossible.'
    });
  }

  enregistrerBadge(b: BadgeProfesseur, retirer = false): void {
    const uid = retirer ? '' : (this.saisieBadge[b.id] || '').trim();
    this.service.attribuerBadge(b.id, uid).subscribe({
      next: () => {
        this.message = retirer ? `Badge retiré à ${b.prenom} ${b.nom}.` : `Badge attribué à ${b.prenom} ${b.nom}.`;
        this.erreur = '';
        this.chargerBadges();
        this.rafraichir();
      },
      error: (e) => { this.erreur = e.error?.message || 'Attribution impossible.'; this.message = ''; }
    });
  }

  attribuerInconnu(uid: string): void {
    const profId = this.attributionInconnu[uid];
    if (!profId) return;
    const prof = this.badges.find(b => b.id === profId);
    if (!prof) return;
    this.saisieBadge[profId] = uid;
    this.enregistrerBadge(prof);
  }

  simuler(): void {
    if (!this.simulationProf) return;
    this.service.simuler(this.simulationProf).subscribe({
      next: (r: any) => {
        this.message = `Simulation : ${r.message}`;
        this.erreur = '';
        this.rafraichir();
        this.chargerBadges();
      },
      error: (e) => this.erreur = e.error?.message || 'Simulation impossible.'
    });
  }

  libelleStatut(s: string): string {
    return s === 'PRESENT' ? 'Présent' : s === 'TERMINE' ? 'Terminé' : s === 'DEPART_NON_POINTE' ? 'Départ non pointé'
      : s === 'ABSENT' ? 'Absent' : s === 'PARTI' ? 'Parti' : s;
  }

  libelleResultat(r: string): string {
    return ({ ARRIVEE: 'Arrivée', DEPART: 'Départ', DEJA_CLOTURE: 'Déjà clôturé', DOUBLON: 'Doublon', INCONNU: 'Badge inconnu' } as any)[r] || r;
  }

  retour(): void {
    this.router.navigate(['/admin'], { queryParams: { tab: 'gestion' } });
  }
}
