================================================================
  FASTO - Administrative Chatbot PFE
  README_changes.txt - All Modifications Applied
  Date: April 13, 2026
================================================================

================================================================
1. BUG FIXES
================================================================

A) GRADES (Notes) ERROR FIX
   ---------------------------
   Problem: "Erreur lors de l'enregistrement. Veuillez reessayer."

   Root Causes Found & Fixed:
   - Backend NoteController had restrictive CORS: only allowed
     localhost:4200, blocking requests from port 61748
   - Backend NoteController had no error handling - threw 500 errors
     instead of returning proper error messages

   Files Modified:
   - backend/src/main/java/.../controller/NoteController.java
     * Added CORS for localhost:61748
     * Added try-catch with proper error response for POST and PUT
     * Returns {error: "message"} on failure instead of 500

   - frontend/src/app/services/note.service.ts (already correct)

B) SCHEDULE GENERATION FIX
   -------------------------
   Problem: Auto-generation returned "Not Found" (404)
   Problem: Manual generation not working

   Root Causes Found & Fixed:

   1. CRITICAL URL MISMATCH (caused 404):
      Frontend PlanningService called /api/admin/planning/*
      But backend PlanningController is mapped to /api/planning/*

      File: frontend/src/app/services/planning.service.ts
      Change: /api/admin/planning -> /api/planning

   2. INFINITE LOOP in auto-generation:
      The `i--` in catch block could cause infinite retries
      when all rooms have conflicts.

      File: backend/src/main/java/.../service/PlanningService.java
      Change: Replaced i-- with proper retry logic:
      - Added maxRetries (3) per slot
      - Added slotPlaced boolean tracking
      - Moves to next time slot after max retries
      - Prevents server hang

C) FRONTEND API URL MISMATCHES (ROOT CAUSE OF MOST BUGS)
   -------------------------------------------------------
   The frontend services were calling incorrect API paths:

   | Service          | Was calling          | Backend actual path  |
   |------------------|---------------------|---------------------|
   | PlanningService  | /api/admin/planning | /api/planning       |
   | FiliereService   | /api/admin/filieres | /api/filieres       |
   | MatiereService   | /api/admin/matieres | /api/matieres       |
   | SalleService     | /api/admin/salles   | /api/salles         |
   | AdminService     | hardcoded URL       | environment variable|

   Files Modified:
   - frontend/src/app/services/planning.service.ts
   - frontend/src/app/services/filiere.service.ts
   - frontend/src/app/services/matiere.service.ts
   - frontend/src/app/services/salle.service.ts
   - frontend/src/app/services/admin.service.ts (+ added environment import)

D) DASHBOARD "NON DEFINI" VALUES FIX
   ------------------------------------
   Problem: "Niveau academique : Non defini"
            "Mode de paiement : Non defini"

   Root Cause: The User model has no "paymentMode" field.

   Fix: Replaced "Mode de paiement" card with "Annee universitaire"
   which maps to the existing User.academicYear field.

   Files Modified:
   - frontend/src/app/pages/etudiant/etudiant.component.html
   - frontend/src/app/pages/etudiant/etudiant.component.ts
     (Updated translations in fr, en, ar)


================================================================
2. SYSTEM LOGIC IMPROVEMENTS
================================================================

A) MANY-TO-MANY SUBJECT-FIELD RELATIONSHIP
   ------------------------------------------
   Previously: A subject (Matiere) could only belong to ONE field (Filiere)
   Now: A subject can belong to MULTIPLE fields
   (e.g., "Gestion" can belong to Marketing, Finance, Economy)

   Implementation:

   Backend Changes:
   - Matiere.java: Added @ManyToMany with join table "matiere_filiere"
     * Kept legacy @ManyToOne "filiere" for backward compatibility
     * Added Set<Filiere> filieres with @JoinTable

   - MatiereRepository.java: Added new query methods
     * findByFilieresId() - queries many-to-many
     * findByAnyFiliereId() - queries BOTH legacy and many-to-many

   - MatiereController.java: Updated to accept filiereIds array
     * POST/PUT now accept "filiereIds": [1, 2, 3]
     * Backward compatible: still accepts single "filiereId"

   - PlanningService.java: Updated to use findByAnyFiliereId()

   Frontend Changes:
   - gestion-matieres.component.ts:
     * Added selectedFiliereIds array
     * Added toggleFiliere() and isFiliereSelected() methods
     * Added getFilieresNames() for display
     * Save sends filiereIds array

   - gestion-matieres.component.html:
     * Added multi-select checkboxes for filieres
     * Added "Filieres" column in data table
     * Shows comma-separated filiere names

B) SCHEDULE SYSTEM IMPROVEMENTS
   --------------------------------
   - Auto-generation now has proper retry logic (max 3 retries)
   - Conflict detection properly handles room and professor overlap
   - Professor schedule depends on student schedule (via shared
     EmploiDuTemps table - professor sees slots where they teach)


================================================================
3. NEW FEATURES
================================================================

A) PROFILE EDIT (Student & Professor)
   ------------------------------------
   Added ability for students and professors to edit their
   personal information directly from their profile tab.

   Features:
   - Edit button on profile page
   - Editable fields: first name, last name, email, phone, address
   - Save and Cancel buttons
   - Success/error feedback messages
   - Multi-language support (FR/EN/AR)
   - Updates stored locally AND sent to backend

   Backend (already existed):
   - PUT /api/student/profile/{username}

   Frontend Files Modified:
   - auth.service.ts: Added updateProfile() method
   - etudiant.component.ts: Added edit state, save/cancel methods
   - etudiant.component.html: Added edit form with input fields
   - professeur.component.ts: Same changes as etudiant
   - professeur.component.html: Same changes as etudiant
   - Both now import FormsModule for ngModel binding


================================================================
4. UI/UX REDESIGN
================================================================

Theme: Light Blue Professional

The entire application has been redesigned from a dark theme
to a clean, professional light blue theme suitable for an
academic/administrative application.

Global Changes (styles.css):
- Background: Dark (#0f172a) -> Light (#f1f5f9)
- Cards: Semi-transparent dark -> White (#ffffff) with shadows
- Text: Light on dark -> Dark on light (#1e293b)
- Primary color: Indigo (#6366f1) -> Blue (#2563eb)
- Inputs: Dark glass -> White with light borders
- Badges: Dark tinted -> Light pastel colors
- Shadows: Heavy dark -> Subtle light

Component-Level Changes:
- login.component.css: Light background, white card, blue gradient
- register.component.css: Matching light theme with blue accents
- etudiant.component.css: Dark blue sidebar, light content area
- professeur.component.css: Dark blue sidebar, blue/teal welcome banner
- admin.component.css: Professional dark blue sidebar, white cards
- planning.component.css: Light grid, white cells
- chat.component.css: Light chat area, blue user messages
- notes-prof.component.css: White cards, light stat backgrounds
- gestion-filieres.component.css: Light table, white panels
- gestion-matieres.component.css: Light table, white panels
- gestion-salles.component.css: Light table, white panels

Design Principles Applied:
- Consistent dark blue sidebar (#1e3a5f) across all dashboards
- White card surfaces with subtle shadows for depth
- Blue primary actions, green success, red danger states
- Light pastel stat card backgrounds
- Professional typography with proper spacing
- Smooth transitions and hover effects


================================================================
5. CONFIGURATION NEEDED
================================================================

A) DATABASE
   The project uses PostgreSQL.
   - DB Name: chatbot_db
   - URL: jdbc:postgresql://localhost:5432/chatbot_db
   - Username: postgres
   - Password: configured in application.yml

   IMPORTANT: After updating the Matiere entity (many-to-many),
   Hibernate will auto-create the "matiere_filiere" join table.
   If you already have data, existing subjects will keep their
   legacy filiere_id. You may need to populate the join table:

   INSERT INTO matiere_filiere (matiere_id, filiere_id)
   SELECT id, filiere_id FROM matieres WHERE filiere_id IS NOT NULL;

B) BACKEND (Spring Boot)
   - Port: 8080
   - Run: mvn spring-boot:run (or via IDE)
   - Java 17 required

C) FRONTEND (Angular 19)
   - Port: 61748
   - Run: ng serve --port 61748
   - Or: npm start

D) NLP/AI SERVICE (FastAPI)
   - Port: 8000
   - This service handles chatbot AI responses
   - Configured in application.yml: nlp.service.url

E) CORS CONFIGURATION
   Both ports (4200 and 61748) are configured in:
   - SecurityConfig.java (global CORS)
   - Individual controllers (@CrossOrigin annotations)


================================================================
6. HOW TO RUN / UPDATE
================================================================

Step 1: Start PostgreSQL
   - Ensure PostgreSQL is running on port 5432
   - Database "chatbot_db" must exist

Step 2: Start Backend
   cd backend
   mvn spring-boot:run

Step 3: Start Frontend
   cd frontend
   npm install  (first time only)
   ng serve --port 61748

Step 4: Start NLP Service (if using chatbot)
   cd nlp-service  (or wherever your FastAPI is)
   uvicorn main:app --port 8000

Step 5: Access the application
   http://localhost:61748


================================================================
7. FILES MODIFIED - COMPLETE LIST
================================================================

FRONTEND:
  src/styles.css                                    (global theme)
  src/app/services/planning.service.ts              (URL fix)
  src/app/services/filiere.service.ts               (URL fix)
  src/app/services/matiere.service.ts               (URL fix)
  src/app/services/salle.service.ts                 (URL fix)
  src/app/services/admin.service.ts                 (URL fix + import)
  src/app/services/auth.service.ts                  (+ updateProfile)
  src/app/pages/etudiant/etudiant.component.ts      (profile edit + fix)
  src/app/pages/etudiant/etudiant.component.html    (profile edit UI)
  src/app/pages/etudiant/etudiant.component.css     (light theme)
  src/app/pages/professeur/professeur.component.ts  (profile edit)
  src/app/pages/professeur/professeur.component.html(profile edit UI)
  src/app/pages/professeur/professeur.component.css (light theme)
  src/app/pages/admin/admin.component.css           (light theme)
  src/app/pages/planning/planning.component.css     (light theme)
  src/app/pages/chat/chat.component.css             (light theme)
  src/app/pages/login/login.component.css           (light theme)
  src/app/pages/register/register.component.css     (light theme)
  src/app/pages/gestion-matieres/gestion-matieres.component.ts   (multi-filiere)
  src/app/pages/gestion-matieres/gestion-matieres.component.html (multi-filiere)
  src/app/pages/gestion-matieres/gestion-matieres.component.css  (light theme)
  src/app/pages/gestion-filieres/gestion-filieres.component.css  (light theme)
  src/app/pages/gestion-salles/gestion-salles.component.css      (light theme)
  src/app/components/notes-prof/notes-prof.component.css         (light theme)

BACKEND:
  src/main/java/.../controller/NoteController.java  (CORS + error handling)
  src/main/java/.../controller/MatiereController.java(many-to-many)
  src/main/java/.../model/Matiere.java              (many-to-many entity)
  src/main/java/.../repository/MatiereRepository.java(new queries)
  src/main/java/.../service/PlanningService.java    (infinite loop fix)

NEW FILES:
  frontend/README_changes.txt                       (this file)

================================================================
