import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PersonneRegistre, RegistreService, ResultatImport } from '../../services/registre.service';

/**
 * Registre de l'établissement : liste officielle (CIN) des étudiants et professeurs.
 * Les inscriptions sont acceptées uniquement si elles correspondent à ce registre.
 */
@Component({
  selector: 'app-registre',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './registre.component.html',
  styleUrls: ['./registre.component.css']
})
export class RegistreComponent implements OnInit {

  stats: any = {};
  personnes: PersonneRegistre[] = [];
  total = 0;
  page = 0;
  pages = 0;
  readonly taille = 25;

  recherche = '';
  type = '';

  fichier: File | null = null;
  resultat: ResultatImport | null = null;
  message = '';
  erreur = '';
  chargement = false;

  constructor(private registre: RegistreService, private router: Router) {}

  ngOnInit(): void {
    this.chargerStats();
    this.charger();
  }

  get modeExterne(): boolean {
    return this.stats?.mode === 'externe';
  }

  chargerStats(): void {
    this.registre.stats().subscribe({ next: (s) => this.stats = s });
  }

  charger(page = 0): void {
    this.page = page;
    this.registre.rechercher(this.recherche.trim(), this.type, page, this.taille).subscribe({
      next: (res: any) => {
        this.personnes = res.content || [];
        this.total = res.totalElements ?? res.page?.totalElements ?? this.personnes.length;
        this.pages = res.totalPages ?? res.page?.totalPages ?? 1;
      },
      error: (e) => this.erreur = e.error?.message || 'Chargement du registre impossible.'
    });
  }

  choisirFichier(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.fichier = input.files && input.files.length ? input.files[0] : null;
    this.resultat = null;
    this.erreur = '';
    this.message = '';
  }

  importer(): void {
    if (!this.fichier) {
      this.erreur = 'Choisissez d\'abord un fichier .xlsx ou .csv.';
      return;
    }
    this.chargement = true;
    this.erreur = '';
    this.registre.importer(this.fichier).subscribe({
      next: (r) => {
        this.chargement = false;
        this.resultat = r;
        this.message = `Import terminé : ${r.ajoutes} ajout(s), ${r.misAJour} mise(s) à jour, ${r.ignores} ligne(s) ignorée(s).`;
        this.chargerStats();
        this.charger(0);
      },
      error: (e) => {
        this.chargement = false;
        this.erreur = e.error?.message || 'Import impossible.';
      }
    });
  }

  chargerDemo(): void {
    if (!confirm('Charger les données FICTIVES de démonstration (filières, matières, salles, registre, comptes '
      + 'étudiants et professeurs, emplois du temps, notes, présences, notifications) ? Rien n\'est supprimé.')) {
      return;
    }
    this.chargement = true;
    this.erreur = '';
    this.registre.chargerDemo().subscribe({
      next: (r: any) => {
        this.chargement = false;
        this.resultat = r.registre;
        this.message = `Démonstration chargée : ${r.filieresCreees} filière(s), ${r.matieresCreees} matière(s), `
          + `${r.sallesCreees} salle(s), ${r.comptesEtudiantsCrees ?? 0} compte(s) étudiant, `
          + `${r.comptesProfesseursCrees ?? 0} compte(s) professeur, ${r.creneauxCrees ?? 0} créneau(x), `
          + `${r.notesCreees ?? 0} note(s), ${r.presencesCreees ?? 0} présence(s) créés. `
          + `Comptes de démo : identifiant prenom.nom (ex. yasmine.trabelsi), mot de passe « ${r.motDePasseComptesDemo} ».`;
        this.chargerStats();
        this.charger(0);
      },
      error: (e) => {
        this.chargement = false;
        this.erreur = e.error?.message || 'Chargement de la démonstration impossible.';
      }
    });
  }

  supprimer(p: PersonneRegistre): void {
    if (!confirm(`Retirer ${p.prenom} ${p.nom} (CIN ${p.cin}) du registre ? Son compte, s'il existe, n'est pas supprimé.`)) {
      return;
    }
    this.registre.supprimer(p.id).subscribe({
      next: () => { this.chargerStats(); this.charger(this.page); },
      error: (e) => this.erreur = e.error?.message || 'Suppression impossible.'
    });
  }

  /** Modèle CSV (séparateur ";", BOM) : s'ouvre directement dans Excel. */
  telechargerModele(): void {
    const lignes = [
      'cin;nom;prenom;type;filiere;niveau;email;matricule;departement',
      '01234567;Ben Salah;Amine;etudiant;INFO;L1;amine@exemple.tn;E2026001;',
      '07654321;Haddad;Karim;professeur;;;karim@exemple.tn;;Informatique'
    ];
    const blob = new Blob(['﻿' + lignes.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'modele_registre.csv';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }

  retour(): void {
    this.router.navigate(['/admin'], { queryParams: { tab: 'gestion' } });
  }
}
