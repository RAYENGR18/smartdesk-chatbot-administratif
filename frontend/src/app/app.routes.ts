import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { ChatComponent } from './pages/chat/chat.component';
import { AdminComponent } from './pages/admin/admin.component';
import { PlanningComponent } from './pages/planning/planning.component';
import { GestionFilieresComponent } from './pages/gestion-filieres/gestion-filieres.component';
import { GestionSallesComponent } from './pages/gestion-salles/gestion-salles.component';
import { GestionMatieresComponent } from './pages/gestion-matieres/gestion-matieres.component';
import { KnowledgeBaseComponent } from './pages/knowledge-base/knowledge-base.component';
import { EtudiantComponent } from './pages/etudiant/etudiant.component';
import { ProfesseurComponent } from './pages/professeur/professeur.component';
import { RegistreComponent } from './pages/registre/registre.component';
import { PresencesComponent } from './pages/presences/presences.component';
import { FichesComponent } from './pages/fiches/fiches.component';
import { guestGuard, roleGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'register', component: RegisterComponent, canActivate: [guestGuard] },

  { path: 'etudiant', component: EtudiantComponent, canActivate: [roleGuard('USER')] },
  { path: 'professeur', component: ProfesseurComponent, canActivate: [roleGuard('PROFESSEUR')] },
  { path: 'chat', component: ChatComponent, canActivate: [roleGuard()] },
  { path: 'planning', component: PlanningComponent, canActivate: [roleGuard()] },

  { path: 'admin', component: AdminComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/filieres', component: GestionFilieresComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/salles', component: GestionSallesComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/matieres', component: GestionMatieresComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/knowledge-base', component: KnowledgeBaseComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/registre', component: RegistreComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/presences', component: PresencesComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/fiches', component: FichesComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'gestion/fiches/:id', component: FichesComponent, canActivate: [roleGuard('ADMIN')] },

  { path: '**', redirectTo: '/login' }
];
