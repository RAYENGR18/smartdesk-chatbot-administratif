package com.chatbot.backend.service;

import com.chatbot.backend.model.DocumentRequest;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.DocumentRepository;
import com.chatbot.backend.repository.UserRepository;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final com.chatbot.backend.repository.NoteRepository noteRepository;

    private final String UPLOAD_DIR = "documents/";

    // ── FPST Brand Colors ───────────────────────────────────────────────────
    private static final DeviceRgb FPST_BLUE    = new DeviceRgb(0,  64, 140);
    private static final DeviceRgb FPST_LIGHT   = new DeviceRgb(235, 242, 255);
    private static final DeviceRgb TEXT_DARK    = new DeviceRgb(30,  30,  30);
    private static final DeviceRgb TEXT_MUTED   = new DeviceRgb(100, 100, 100);
    private static final DeviceRgb BORDER_LIGHT = new DeviceRgb(200, 210, 230);

    // ── Institution Info ────────────────────────────────────────────────────
    private static final String INST_NAME    = "FACULTÉ PRIVÉE DES SCIENCES ET TECHNOLOGIES";
    private static final String INST_SHORT   = "FPST";
    private static final String INST_ADDRESS = "Zone Industrielle, Rue des Sciences, Tunis 1000 – Tunisie";
    private static final String INST_CONTACT = "Tél : +216 71 000 000  |  Fax : +216 71 000 001";
    private static final String INST_EMAIL   = "secretariat@fpst.tn  |  www.fpst.tn";

    // =======================================================================
    // PUBLIC API
    // =======================================================================
    public DocumentRequest generateDocument(
            String documentType,
            String fullName,
            String additionalInfo,
            String format,
            String username
    ) throws Exception {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        new File(UPLOAD_DIR).mkdirs();

        String fileName = documentType + "_" + username + "_" + System.currentTimeMillis();
        String filePath = format.equals("PDF")
                ? generatePDF(fileName, documentType, fullName, additionalInfo, user)
                : generateWord(fileName, documentType, fullName, additionalInfo, user);

        DocumentRequest doc = new DocumentRequest();
        doc.setDocumentType(documentType);
        doc.setFullName(fullName);
        doc.setAdditionalInfo(additionalInfo);
        doc.setFormat(format);
        doc.setStatus("GENERE");
        doc.setFilePath(filePath);
        doc.setUser(user);

        return documentRepository.save(doc);
    }

    public List<DocumentRequest> getUserDocuments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return documentRepository.findByUserId(user.getId());
    }

    // =======================================================================
    // PDF GENERATION
    // =======================================================================
    private String generatePDF(
            String fileName,
            String documentType,
            String fullName,
            String additionalInfo,
            User user
    ) throws Exception {

        String filePath = UPLOAD_DIR + fileName + ".pdf";

        PdfWriter   writer  = new PdfWriter(filePath);
        PdfDocument pdfDoc  = new PdfDocument(writer);
        Document    document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(36, 50, 36, 50);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        String date   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String refNum = genRef(documentType);
        Map<String, String> info = parseInfo(additionalInfo);

        // ── 1. INSTITUTION HEADER ──────────────────────────────────────────
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{22, 78}))
                .setWidth(UnitValue.createPercentValue(100));

        // Logo / Acronym cell
        headerTable.addCell(new Cell()
                .add(new Paragraph(INST_SHORT)
                        .setFont(bold).setFontSize(30)
                        .setFontColor(FPST_BLUE)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph("Faculté Privée")
                        .setFont(regular).setFontSize(7)
                        .setFontColor(TEXT_MUTED)
                        .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(FPST_LIGHT)
                .setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(10));

        // Institution info cell
        headerTable.addCell(new Cell()
                .add(new Paragraph(INST_NAME)
                        .setFont(bold).setFontSize(13)
                        .setFontColor(FPST_BLUE))
                .add(new Paragraph(INST_ADDRESS)
                        .setFont(regular).setFontSize(9)
                        .setFontColor(TEXT_MUTED))
                .add(new Paragraph(INST_CONTACT)
                        .setFont(regular).setFontSize(9)
                        .setFontColor(TEXT_MUTED))
                .add(new Paragraph(INST_EMAIL)
                        .setFont(regular).setFontSize(9)
                        .setFontColor(TEXT_MUTED))
                .setBorder(Border.NO_BORDER)
                .setPaddingLeft(14)
                .setVerticalAlignment(VerticalAlignment.MIDDLE));

        document.add(headerTable);

        // ── 2. BLUE DIVIDER ───────────────────────────────────────────────
        document.add(new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(4).setMarginBottom(8)
                .addCell(new Cell().setHeight(4)
                        .setBackgroundColor(FPST_BLUE)
                        .setBorder(Border.NO_BORDER)));

        // ── 3. REF + DATE LINE ────────────────────────────────────────────
        Table refTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(14);
        refTable.addCell(new Cell()
                .add(new Paragraph("Réf : " + refNum)
                        .setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED))
                .setBorder(Border.NO_BORDER));
        refTable.addCell(new Cell()
                .add(new Paragraph("Tunis, le " + date)
                        .setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER));
        document.add(refTable);

        // ── 4. TITLE BAR ──────────────────────────────────────────────────
        document.add(new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(22)
                .addCell(new Cell()
                        .add(new Paragraph(getDocumentTitle(documentType))
                                .setFont(bold).setFontSize(15)
                                .setFontColor(ColorConstants.WHITE)
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBackgroundColor(FPST_BLUE)
                        .setPadding(12)
                        .setBorder(Border.NO_BORDER)));

        // ── 5. MAIN CONTENT ───────────────────────────────────────────────
        String content = generateContent(documentType, fullName, info, user, date);
        document.add(new Paragraph(content)
                .setFont(regular).setFontSize(12)
                .setFontColor(TEXT_DARK)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .setFirstLineIndent(30)
                .setMultipliedLeading(1.7f)
                .setMarginBottom(30));

        // ── 6. SIGNATURE BLOCK ────────────────────────────────────────────
        Table sigTable = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(20);

        sigTable.addCell(new Cell()
                .add(new Paragraph("Fait à Tunis, le " + date)
                        .setFont(regular).setFontSize(11))
                .add(new Paragraph("(Document généré via FASTO – Plateforme numérique FPST)")
                        .setFont(regular).setFontSize(8)
                        .setFontColor(TEXT_MUTED))
                .setBorder(Border.NO_BORDER));

        sigTable.addCell(new Cell()
                .add(new Paragraph(getSignatoryTitle(documentType))
                        .setFont(bold).setFontSize(11)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph("\n\n\n_________________________")
                        .setFont(regular).setFontSize(11)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph("Signature et Cachet Officiel")
                        .setFont(regular).setFontSize(9)
                        .setFontColor(TEXT_MUTED)
                        .setTextAlignment(TextAlignment.CENTER))
                .setBorder(Border.NO_BORDER));

        document.add(sigTable);

        // ── 7. LEGAL FOOTER ───────────────────────────────────────────────
        document.add(new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(18)
                .addCell(new Cell()
                        .add(new Paragraph(
                                "FPST – Ce document est établi sous la responsabilité de l'établissement. " +
                                "Il ne peut être utilisé qu'aux fins pour lesquelles il a été délivré. " +
                                "Toute falsification constitue une infraction passible de poursuites.")
                                .setFont(regular).setFontSize(7.5f)
                                .setFontColor(TEXT_MUTED)
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBackgroundColor(FPST_LIGHT)
                        .setPadding(6)
                        .setBorder(Border.NO_BORDER)));

        document.close();
        return filePath;
    }

    // =======================================================================
    // WORD GENERATION
    // =======================================================================
    private String generateWord(
            String fileName,
            String documentType,
            String fullName,
            String additionalInfo,
            User user
    ) throws Exception {

        String filePath = UPLOAD_DIR + fileName + ".docx";
        XWPFDocument document = new XWPFDocument();

        String date   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String refNum = genRef(documentType);
        Map<String, String> info = parseInfo(additionalInfo);

        // Helper: create a paragraph with alignment and font settings
        // Institution name
        addWordParagraph(document, INST_NAME, 14, true, "00408C", ParagraphAlignment.CENTER);
        addWordParagraph(document, INST_ADDRESS + "  |  " + INST_CONTACT, 9, false, "666666", ParagraphAlignment.CENTER);
        addWordParagraph(document, INST_EMAIL, 9, false, "666666", ParagraphAlignment.CENTER);
        addWordParagraph(document, "━".repeat(90), 7, false, "00408C", ParagraphAlignment.CENTER);

        // Ref + date
        addWordParagraph(document,
                "Réf : " + refNum + "                                        Tunis, le " + date,
                9, false, "888888", ParagraphAlignment.LEFT);

        // Empty line
        document.createParagraph();

        // Title
        addWordParagraph(document, getDocumentTitle(documentType), 18, true, "00408C", ParagraphAlignment.CENTER);
        addWordParagraph(document, "─".repeat(50), 10, false, "00408C", ParagraphAlignment.CENTER);

        // Empty line
        document.createParagraph();

        // Content
        String content = generateContent(documentType, fullName, info, user, date);
        XWPFParagraph contentPara = document.createParagraph();
        contentPara.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun contentRun = contentPara.createRun();
        contentRun.setText(content);
        contentRun.setFontSize(12);
        contentRun.setFontFamily("Arial");

        // Spacers
        for (int i = 0; i < 3; i++) document.createParagraph();

        // Date line
        addWordParagraph(document, "Fait à Tunis, le " + date, 11, false, "000000", ParagraphAlignment.LEFT);
        document.createParagraph();

        // Signatory
        addWordParagraph(document, getSignatoryTitle(documentType), 11, true, "000000", ParagraphAlignment.RIGHT);
        XWPFParagraph sigPara = document.createParagraph();
        sigPara.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun sigRun = sigPara.createRun();
        sigRun.setText("Signature et Cachet Officiel");
        sigRun.setFontSize(9);
        sigRun.setColor("888888");
        sigRun.addBreak();
        sigRun.setText("_________________________");
        sigRun.setColor("333333");

        // Footer
        document.createParagraph();
        addWordParagraph(document,
                "FPST – Ce document est établi sous la responsabilité de l'établissement.",
                8, false, "999999", ParagraphAlignment.CENTER);

        FileOutputStream out = new FileOutputStream(filePath);
        document.write(out);
        out.close();
        document.close();

        return filePath;
    }

    /** Helper to add a styled paragraph to a Word document */
    private void addWordParagraph(XWPFDocument doc, String text, int fontSize,
                                   boolean bold, String color, ParagraphAlignment align) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(align);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setColor(color);
        run.setFontFamily("Arial");
    }

    // =======================================================================
    // DOCUMENT METADATA
    // =======================================================================
    private String getDocumentTitle(String documentType) {
        return switch (documentType) {
            case "attestation_travail"                         -> "ATTESTATION DE TRAVAIL";
            case "attestation_scolarite", "certificat_scolarite" -> "ATTESTATION DE SCOLARITÉ";
            case "attestation_residence"                        -> "ATTESTATION DE RÉSIDENCE";
            case "attestation_presence"                         -> "ATTESTATION DE PRÉSENCE";
            case "attestation_stage"                            -> "ATTESTATION DE STAGE";
            case "attestation_salaire"                          -> "ATTESTATION DE SALAIRE";
            case "demande_releve_notes"                         -> "RELEVÉ DE NOTES";
            case "lettre_motivation"                            -> "LETTRE DE MOTIVATION";
            case "lettre_demande"                               -> "LETTRE DE DEMANDE";
            case "lettre_reclamation"                           -> "LETTRE DE RÉCLAMATION";
            case "note_service"                                 -> "NOTE DE SERVICE";
            case "declaration_honneur"                          -> "DÉCLARATION SUR L'HONNEUR";
            case "copie_conforme"                               -> "ATTESTATION – COPIE CONFORME";
            case "demande_conge", "demande_conge_academique"    -> "DEMANDE DE CONGÉ";
            case "cv"                                           -> "CURRICULUM VITAE";
            case "contrat_travail"                              -> "CONTRAT DE TRAVAIL";
            case "fiche_paie"                                   -> "FICHE DE PAIE";
            default                                             -> "ATTESTATION";
        };
    }

    private String getSignatoryTitle(String documentType) {
        return switch (documentType) {
            case "attestation_travail", "attestation_salaire",
                 "note_service", "contrat_travail", "fiche_paie"       -> "Le Directeur Général";
            case "attestation_scolarite", "certificat_scolarite",
                 "attestation_presence", "demande_releve_notes"         -> "Le Directeur des Études";
            case "attestation_stage"                                    -> "Le Responsable des Stages";
            case "lettre_motivation", "lettre_demande",
                 "lettre_reclamation", "declaration_honneur",
                 "demande_conge", "demande_conge_academique"            -> "Le/La Soussigné(e)";
            default                                                     -> "Le Directeur";
        };
    }

    // =======================================================================
    // CONTENT GENERATION — rich templates per document type
    // =======================================================================
    private String generateContent(
            String documentType,
            String fullName,
            Map<String, String> info,
            User user,
            String date
    ) {
        // Merge info-map values with User profile (user profile takes priority when info field is empty)
        String cin         = coalesce(get(info, "cin"), user.getCin());
        String email       = coalesce(get(info, "email"), user.getEmail());
        String phone       = coalesce(get(info, "telephone", "téléphone"), user.getPhone());
        String address     = coalesce(get(info, "adresse"), user.getAddress());
        String studentId   = coalesce(user.getStudentId(), "");
        String fieldOfStudy = coalesce(user.getFieldOfStudy(), get(info, "filiere", "filière/niveau"));
        String level       = coalesce(user.getAcademicLevel(), "");
        String acYear      = coalesce(get(info, "annee universitaire", "année universitaire"), "2025/2026");
        boolean isProf     = user.getRole() == User.Role.PROFESSEUR;
        String idLabel     = isProf ? "N° Matricule" : "N° Étudiant";

        return switch (documentType) {

            // ── ATTESTATION DE TRAVAIL ────────────────────────────────────
            case "attestation_travail" -> {
                String company   = get(info, "entreprise");
                String post      = get(info, "poste");
                String startDate = get(info, "date debut");
                String salary    = get(info, "salaire");
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), le Directeur Général de la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("certifie par la présente que :\n\n");
                sb.append("Monsieur / Madame  ").append(fullName.toUpperCase());
                if (!cin.isEmpty())       sb.append(", titulaire de la CIN n° ").append(cin);
                sb.append(",\nest employé(e) au sein de notre établissement");
                if (!post.isEmpty())      sb.append(" en qualité de ").append(post);
                if (!company.isEmpty())   sb.append(", au département / service : ").append(company);
                if (!startDate.isEmpty()) sb.append(", depuis le ").append(startDate);
                sb.append(".\n\n");
                if (!salary.isEmpty())
                    sb.append("Son salaire mensuel brut est de ").append(salary).append(" dinars tunisiens (DT).\n\n");
                sb.append("La présente attestation est délivrée à l'intéressé(e) sur sa demande expresse, ");
                sb.append("pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── ATTESTATION DE SCOLARITÉ ──────────────────────────────────
            case "attestation_scolarite", "certificat_scolarite" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), le Directeur des Études de la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("certifie par la présente que :\n\n");
                sb.append("Monsieur / Madame  ").append(fullName.toUpperCase());
                if (!cin.isEmpty()) sb.append(", titulaire de la CIN n° ").append(cin);
                sb.append(",\nest régulièrement inscrit(e) dans notre établissement ");
                sb.append("pour l'année universitaire ").append(acYear);
                if (!fieldOfStudy.isEmpty()) sb.append(",\nen filière : ").append(fieldOfStudy);
                if (!level.isEmpty())        sb.append(" – ").append(level);
                if (!studentId.isEmpty())    sb.append("\n(N° Étudiant : ").append(studentId).append(")");
                sb.append(".\n\n");
                sb.append("Cette attestation est délivrée à l'intéressé(e) sur sa demande, ");
                sb.append("pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── ATTESTATION DE PRÉSENCE ───────────────────────────────────
            case "attestation_presence" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), le Directeur des Études de la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("certifie que :\n\n");
                sb.append("Monsieur / Madame  ").append(fullName.toUpperCase());
                if (!cin.isEmpty()) sb.append(" (CIN : ").append(cin).append(")");
                sb.append(",\nétudiant(e) en ").append(fieldOfStudy.isEmpty() ? "notre établissement" : fieldOfStudy);
                if (!level.isEmpty()) sb.append(" – ").append(level);
                sb.append(",\na bien été présent(e) de façon régulière aux cours dispensés ");
                sb.append("durant l'année universitaire ").append(acYear).append(".\n\n");
                sb.append("Cette attestation est délivrée à l'intéressé(e) sur sa demande, ");
                sb.append("pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── ATTESTATION DE STAGE ──────────────────────────────────────
            case "attestation_stage" -> {
                String company  = coalesce(get(info, "entreprise de stage"), get(info, "entreprise"));
                String duration = get(info, "duree", "durée");
                String dept     = get(info, "departement", "département");
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), le Responsable des Stages de la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("atteste par la présente que :\n\n");
                sb.append("Monsieur / Madame  ").append(fullName.toUpperCase());
                if (!cin.isEmpty()) sb.append(" (CIN : ").append(cin).append(")");
                sb.append(",\nétudiant(e) en ").append(fieldOfStudy.isEmpty() ? "notre établissement" : fieldOfStudy);
                if (!level.isEmpty()) sb.append(" – ").append(level);
                sb.append(",\na effectué un stage");
                if (!company.isEmpty())  sb.append(" au sein de : ").append(company);
                if (!dept.isEmpty())     sb.append(", service / département : ").append(dept);
                if (!duration.isEmpty()) sb.append(", d'une durée de ").append(duration);
                sb.append(",\ndans le cadre de sa formation académique pour l'année ").append(acYear).append(".\n\n");
                sb.append("Cette attestation est établie en foi de quoi et pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── ATTESTATION DE RÉSIDENCE ──────────────────────────────────
            case "attestation_residence" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), certifie par la présente que :\n\n");
                sb.append("Monsieur / Madame  ").append(fullName.toUpperCase());
                if (!cin.isEmpty()) sb.append(", titulaire de la CIN n° ").append(cin);
                sb.append(",\nréside à l'adresse suivante :\n\n");
                sb.append("        ").append(address.isEmpty() ? "[Adresse non renseignée]" : address).append("\n\n");
                sb.append("Cette attestation est délivrée à l'intéressé(e) sur sa demande, ");
                sb.append("pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── ATTESTATION DE SALAIRE ────────────────────────────────────
            case "attestation_salaire" -> {
                String post      = get(info, "poste");
                String salary    = get(info, "salaire");
                String startDate = get(info, "date debut");
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), le Directeur Général de la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("certifie que :\n\n");
                sb.append("Monsieur / Madame  ").append(fullName.toUpperCase());
                if (!cin.isEmpty())       sb.append(" (CIN : ").append(cin).append(")");
                if (!post.isEmpty())      sb.append(",\noccupant le poste de : ").append(post);
                if (!startDate.isEmpty()) sb.append(", depuis le ").append(startDate);
                sb.append(",\nperçoit actuellement un salaire mensuel brut de : ");
                sb.append(salary.isEmpty() ? "___________" : salary).append(" dinars tunisiens (DT).\n\n");
                sb.append("La présente attestation est délivrée à l'intéressé(e) sur sa demande, ");
                sb.append("pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── RELEVÉ DE NOTES ───────────────────────────────────────────
            case "demande_releve_notes" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("RELEVÉ DE NOTES OFFICIEL\n");
                sb.append("Année universitaire : ").append(acYear).append("\n\n");
                sb.append("Nom & Prénom    : ").append(fullName.toUpperCase()).append("\n");
                if (!studentId.isEmpty()) sb.append(idLabel).append("        : ").append(studentId).append("\n");
                if (!fieldOfStudy.isEmpty()) sb.append("Filière          : ").append(fieldOfStudy);
                if (!level.isEmpty()) sb.append(" – ").append(level);
                sb.append("\n");
                if (!cin.isEmpty()) sb.append("CIN              : ").append(cin).append("\n");
                if (!email.isEmpty()) sb.append("Email            : ").append(email).append("\n");
                sb.append("\n").append("─".repeat(60)).append("\n");
                sb.append(String.format("%-35s %-12s %-8s\n", "MATIÈRE", "ÉVALUATION", "NOTE/20"));
                sb.append("─".repeat(60)).append("\n");
                java.util.List<com.chatbot.backend.model.Note> notes = noteRepository.findByEtudiantId(user.getId());
                if (notes.isEmpty()) {
                    sb.append("Aucune note enregistrée pour cet étudiant.\n");
                } else {
                    double total = 0;
                    for (com.chatbot.backend.model.Note n : notes) {
                        String nom = n.getMatiere() != null ? n.getMatiere().getNom() : "—";
                        if (nom.length() > 33) nom = nom.substring(0, 30) + "...";
                        sb.append(String.format("%-35s %-12s %.2f\n", nom, n.getTypeEvaluation(), n.getValeur()));
                        total += n.getValeur();
                    }
                    sb.append("─".repeat(60)).append("\n");
                    sb.append(String.format("%-35s %-12s %.2f\n", "MOYENNE GÉNÉRALE", "", total / notes.size()));
                }
                sb.append("─".repeat(60)).append("\n\n");
                sb.append("Ce document est délivré à l'intéressé(e) sur sa demande, pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── LETTRE DE MOTIVATION ──────────────────────────────────────
            case "lettre_motivation" -> {
                String recipient = get(info, "destinataire");
                String object    = get(info, "objet");
                StringBuilder sb = new StringBuilder();
                sb.append(fullName.toUpperCase()).append("\n");
                if (!email.isEmpty())   sb.append("Email : ").append(email).append("\n");
                if (!phone.isEmpty())   sb.append("Tél   : ").append(phone).append("\n");
                if (!address.isEmpty()) sb.append("Adresse : ").append(address).append("\n");
                sb.append("\n");
                sb.append(recipient.isEmpty() ? "Madame, Monsieur," : "À l'attention de : " + recipient).append("\n");
                sb.append("Faculté Privée des Sciences et Technologies – FPST\n\n");
                if (!object.isEmpty()) sb.append("Objet : ").append(object).append("\n\n");
                sb.append("Madame, Monsieur,\n\n");
                sb.append("Actuellement ");
                sb.append(fieldOfStudy.isEmpty() ? "membre de la FPST" : (level.isEmpty() ? "étudiant(e) en " : "en " + level + " de ") + fieldOfStudy);
                sb.append(" à la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("je me permets de vous adresser ma candidature ");
                sb.append(object.isEmpty() ? "pour le poste / la mission proposé(e)" : "pour : " + object).append(".\n\n");
                sb.append("Animé(e) par un fort intérêt pour ce domaine et fort(e) de mes expériences académiques et pratiques, ");
                sb.append("je suis convaincu(e) de pouvoir apporter une contribution significative à votre organisation.\n\n");
                sb.append("Je reste disponible pour tout entretien à votre convenance et vous transmets ci-joint mon dossier de candidature.\n\n");
                sb.append("Veuillez agréer, Madame, Monsieur, l'expression de mes respectueuses salutations.\n\n");
                sb.append(fullName);
                yield sb.toString();
            }

            // ── LETTRE DE DEMANDE ─────────────────────────────────────────
            case "lettre_demande" -> {
                String recipient = get(info, "destinataire");
                String object    = get(info, "objet");
                String extra     = get(info, "informations supplementaires");
                StringBuilder sb = new StringBuilder();
                sb.append(fullName.toUpperCase()).append("\n");
                if (!studentId.isEmpty()) sb.append(idLabel).append(" : ").append(studentId).append("\n");
                if (!email.isEmpty())     sb.append("Email : ").append(email).append("\n");
                if (!phone.isEmpty())     sb.append("Tél   : ").append(phone).append("\n");
                sb.append("\nÀ : ").append(recipient.isEmpty() ? "Monsieur/Madame le(la) Directeur(rice)" : recipient).append("\n");
                sb.append("Faculté Privée des Sciences et Technologies – FPST\n\n");
                if (!object.isEmpty()) sb.append("Objet : ").append(object).append("\n\n");
                sb.append("Monsieur / Madame,\n\n");
                sb.append("Je soussigné(e) ").append(fullName);
                if (!studentId.isEmpty())    sb.append(", ").append(idLabel).append(" : ").append(studentId);
                if (!fieldOfStudy.isEmpty()) sb.append(isProf ? ", enseignant(e) en " : ", inscrit(e) en ").append(fieldOfStudy);
                if (!level.isEmpty())        sb.append(" – ").append(level);
                sb.append(",\nj'ai l'honneur de solliciter auprès de vous ");
                sb.append(object.isEmpty() ? "la demande ci-décrite" : object).append(".\n\n");
                if (!extra.isEmpty()) sb.append("Détails supplémentaires : ").append(extra).append("\n\n");
                sb.append("Vous trouverez ci-joint les pièces justificatives nécessaires. ");
                sb.append("Dans l'attente d'une suite favorable, je vous prie d'agréer, ");
                sb.append("Monsieur / Madame, l'expression de mes respectueuses salutations.\n\n");
                sb.append(fullName);
                yield sb.toString();
            }

            // ── LETTRE DE RÉCLAMATION ─────────────────────────────────────
            case "lettre_reclamation" -> {
                String complaint = coalesce(get(info, "description probleme", "description du probleme"), get(info, "informations supplementaires"));
                StringBuilder sb = new StringBuilder();
                sb.append(fullName.toUpperCase()).append("\n");
                if (!studentId.isEmpty()) sb.append(idLabel).append(" : ").append(studentId).append("\n");
                if (!email.isEmpty())     sb.append("Email : ").append(email).append("\n");
                sb.append("\nÀ Monsieur/Madame le(la) Directeur(rice)\n");
                sb.append("Faculté Privée des Sciences et Technologies – FPST\n\n");
                sb.append("Objet : Réclamation\n\n");
                sb.append("Monsieur / Madame,\n\n");
                sb.append("Je soussigné(e) ").append(fullName).append(", me permets de porter à votre connaissance ");
                sb.append("le problème suivant que j'ai rencontré :\n\n");
                sb.append(complaint.isEmpty() ? "[Décrivez votre problème ici avec précision]" : complaint).append("\n\n");
                sb.append("Je vous saurais gré de bien vouloir traiter cette réclamation dans les meilleurs délais ");
                sb.append("et de m'informer des mesures prises.\n\n");
                sb.append("Veuillez agréer, Monsieur / Madame, l'expression de mes respectueuses salutations.\n\n");
                sb.append(fullName);
                yield sb.toString();
            }

            // ── DÉCLARATION SUR L'HONNEUR ─────────────────────────────────
            case "declaration_honneur" -> {
                String declaration = get(info, "informations supplementaires");
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e),\n\n");
                sb.append("Nom & Prénom    : ").append(fullName.toUpperCase()).append("\n");
                if (!cin.isEmpty())     sb.append("CIN              : ").append(cin).append("\n");
                if (!address.isEmpty()) sb.append("Adresse          : ").append(address).append("\n");
                if (!email.isEmpty())   sb.append("Email            : ").append(email).append("\n\n");
                sb.append("déclare sur l'honneur :\n\n");
                sb.append(declaration.isEmpty()
                        ? "–  Que toutes les informations fournies dans mon dossier sont exactes, sincères et complètes.\n"
                          + "–  Que je n'ai fait l'objet d'aucune sanction académique ou disciplinaire.\n"
                        : "–  " + declaration.replace("\n", "\n–  ")).append("\n\n");
                sb.append("Je certifie l'exactitude des informations ci-dessus sous ma responsabilité ");
                sb.append("et m'engage à en apporter la preuve sur simple demande.\n\n");
                sb.append("Fait à Tunis, le ").append(date).append(", pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── NOTE DE SERVICE ───────────────────────────────────────────
            case "note_service" -> {
                String subject = get(info, "objet");
                String dest    = get(info, "destinataire");
                String body    = get(info, "informations supplementaires");
                String nsRef   = genRef("NS");
                StringBuilder sb = new StringBuilder();
                sb.append("NOTE DE SERVICE N° ").append(nsRef).append("\n\n");
                sb.append("DE      : Direction Générale – FPST\n");
                sb.append("À       : ").append(dest.isEmpty() ? "Tout le Personnel / Tous les Étudiants" : dest).append("\n");
                sb.append("Objet   : ").append(subject.isEmpty() ? "[Objet de la note]" : subject).append("\n");
                sb.append("Date    : Tunis, le ").append(date).append("\n");
                sb.append("━".repeat(60)).append("\n\n");
                sb.append(body.isEmpty()
                        ? "Par la présente, il est porté à la connaissance de toutes les parties concernées "
                          + "les informations et directives suivantes : [Insérer le contenu de la note ici]."
                        : body).append("\n\n");
                sb.append("Cette note prend effet à compter de la date de sa signature et annule ");
                sb.append("toute disposition antérieure contraire.");
                yield sb.toString();
            }

            // ── DEMANDE DE CONGÉ ──────────────────────────────────────────
            case "demande_conge", "demande_conge_academique" -> {
                String leaveType  = get(info, "type conge", "type congé");
                String leaveStart = get(info, "du");
                String leaveEnd   = get(info, "au");
                String reason     = coalesce(get(info, "motif"), get(info, "informations supplementaires"));
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e),\n\n");
                sb.append("Nom & Prénom    : ").append(fullName.toUpperCase()).append("\n");
                if (!studentId.isEmpty())    sb.append(idLabel).append("     : ").append(studentId).append("\n");
                if (!fieldOfStudy.isEmpty()) sb.append(isProf ? "Département      : " : "Filière          : ").append(fieldOfStudy).append("\n");
                if (!level.isEmpty())        sb.append("Niveau           : ").append(level).append("\n");
                if (!email.isEmpty())        sb.append("Email            : ").append(email).append("\n\n");
                sb.append("j'ai l'honneur de solliciter un congé");
                if (!leaveType.isEmpty())  sb.append(" de type : ").append(leaveType);
                if (!leaveStart.isEmpty()) sb.append(",\ndu ").append(leaveStart);
                if (!leaveEnd.isEmpty())   sb.append(" au ").append(leaveEnd);
                sb.append(".\n\n");
                if (!reason.isEmpty()) sb.append("Motif : ").append(reason).append("\n\n");
                sb.append("Dans l'attente d'une décision favorable, je vous prie d'agréer, ");
                sb.append("Monsieur / Madame, l'expression de mes respectueuses salutations.\n\n");
                sb.append(fullName);
                yield sb.toString();
            }

            // ── CURRICULUM VITAE ──────────────────────────────────────────
            case "cv" -> {
                String post   = coalesce(get(info, "poste recherche", "poste recherché"), get(info, "poste"));
                String school = get(info, "formation");
                String skills = get(info, "informations supplementaires");
                String bYear  = user.getAcademicYear() != null ? user.getAcademicYear() : acYear;
                StringBuilder sb = new StringBuilder();
                sb.append("══════════════════════════════════════════════════════\n");
                sb.append("  ").append(fullName.toUpperCase()).append("\n");
                if (!post.isEmpty()) sb.append("  ").append(post).append("\n");
                sb.append("══════════════════════════════════════════════════════\n\n");
                sb.append("COORDONNÉES\n");
                sb.append("────────────────────────────────\n");
                if (!email.isEmpty())   sb.append("Email    : ").append(email).append("\n");
                if (!phone.isEmpty())   sb.append("Tél      : ").append(phone).append("\n");
                if (!address.isEmpty()) sb.append("Adresse  : ").append(address).append("\n");
                if (!cin.isEmpty())     sb.append("CIN      : ").append(cin).append("\n");
                sb.append("\nFORMATION\n");
                sb.append("────────────────────────────────\n");
                if (!fieldOfStudy.isEmpty()) {
                    sb.append("• ").append(level.isEmpty() ? fieldOfStudy : fieldOfStudy + " – " + level);
                    sb.append("\n  Faculté Privée des Sciences et Technologies (FPST) – ").append(bYear).append("\n");
                }
                if (!school.isEmpty()) sb.append("• ").append(school).append("\n");
                if (!studentId.isEmpty()) sb.append("  N° Étudiant : ").append(studentId).append("\n");
                sb.append("\nCOMPÉTENCES\n");
                sb.append("────────────────────────────────\n");
                sb.append(skills.isEmpty() ? "• [À compléter par le candidat]\n" : skills.replace("\n", "\n• ")).append("\n");
                sb.append("\nLANGUES\n");
                sb.append("────────────────────────────────\n");
                sb.append("• Arabe (langue maternelle)\n• Français (courant)\n• Anglais (intermédiaire)\n");
                yield sb.toString();
            }

            // ── COPIE CONFORME ────────────────────────────────────────────
            case "copie_conforme" -> {
                String originalDoc = coalesce(get(info, "type de document", "type document"), get(info, "informations supplementaires", "objet"));
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), le Directeur de la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("certifie que la présente est une copie conforme à l'original du document suivant :\n\n");
                sb.append("Document original : ").append(originalDoc.isEmpty() ? "[Type de document non précisé]" : originalDoc).append("\n");
                sb.append("Appartenant à : Monsieur / Madame ").append(fullName.toUpperCase()).append("\n");
                if (!cin.isEmpty()) sb.append("CIN : ").append(cin).append("\n");
                sb.append("\n");
                sb.append("La présente copie a été certifiée conforme à l'original en notre présence.\n\n");
                sb.append("Cette attestation est délivrée pour servir et valoir ce que de droit.");
                yield sb.toString();
            }

            // ── DEFAULT / GENERIC ─────────────────────────────────────────
            default -> {
                String extra = get(info, "informations supplementaires");
                StringBuilder sb = new StringBuilder();
                sb.append("Je soussigné(e), la Direction de la Faculté Privée des Sciences et Technologies (FPST), ");
                sb.append("certifie que :\n\n");
                sb.append("Monsieur / Madame  ").append(fullName.toUpperCase());
                if (!cin.isEmpty()) sb.append(" (CIN : ").append(cin).append(")");
                sb.append("\n");
                if (!extra.isEmpty()) sb.append(extra).append("\n\n");
                sb.append("Cette attestation est délivrée à l'intéressé(e) pour servir et valoir ce que de droit.");
                yield sb.toString();
            }
        };
    }

    // =======================================================================
    // UTILITIES
    // =======================================================================

    /** Parse "Key : Value\nKey2 : Value2" strings into a normalized map */
    private Map<String, String> parseInfo(String info) {
        Map<String, String> map = new LinkedHashMap<>();
        if (info == null || info.isEmpty()) return map;
        for (String line : info.split("\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String key = normalizeKey(line.substring(0, idx).trim());
                String val = line.substring(idx + 1).trim();
                if (!val.isEmpty()) map.put(key, val);
            }
        }
        return map;
    }

    private String normalizeKey(String s) {
        return s.toLowerCase()
                .replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
                .replace("à", "a").replace("â", "a").replace("ä", "a")
                .replace("ô", "o").replace("ö", "o")
                .replace("û", "u").replace("ü", "u").replace("ù", "u")
                .replace("î", "i").replace("ï", "i")
                .replace("ç", "c")
                .replaceAll("[^a-z0-9 ]", "").trim();
    }

    /** Look up one of several alternate keys, return first non-empty value */
    private String get(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String val = map.get(normalizeKey(key));
            if (val != null && !val.isEmpty()) return val;
        }
        return "";
    }

    /** Return first non-null, non-empty string */
    private String coalesce(String... values) {
        for (String v : values) if (v != null && !v.isEmpty()) return v;
        return "";
    }

    /** Generate a reference number like FPST-ATT-2026-4312 */
    private String genRef(String docType) {
        String prefix = docType.length() >= 3
                ? docType.substring(0, 3).toUpperCase().replace("_", "")
                : docType.toUpperCase();
        return "FPST-" + prefix + "-" + LocalDate.now().getYear() + "-" + (1000 + (int)(Math.random() * 8999));
    }
}
