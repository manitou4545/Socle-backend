package com.socle.backend.service;

import com.socle.backend.model.PageLockEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Manipulation réelle des pages d'un PDF (Apache PDFBox), pour le
 * verrouillage par plages de pages : la version "publique" remplace le
 * contenu des pages verrouillées par une page d'avertissement, tandis que
 * le PDF original complet reste stocké de façon privée pour extraction
 * ultérieure des pages déverrouillées.
 */
@Service
public class PdfService {

    /** Nombre total de pages d'un PDF. */
    public int countPages(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            return doc.getNumberOfPages();
        }
    }

    /** Extrait le texte brut d'un PDF (utilisé pour donner à l'assistant IA le vrai
     *  contenu accessible : appliqué sur la version PUBLIQUE, les pages verrouillées
     *  ne contiennent que le texte de la page d'avertissement, jamais le vrai contenu. */
    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** Construit la version publique : les pages comprises dans une plage verrouillée
     *  sont remplacées par une page "Contenu verrouillé — X crédits", les autres sont
     *  copiées telles quelles. */
    public byte[] buildPublicPdf(byte[] originalBytes, List<PageLockEntity> locks) throws IOException {
        try (PDDocument source = PDDocument.load(new ByteArrayInputStream(originalBytes));
             PDDocument output = new PDDocument()) {

            int total = source.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                int pageNumber = i + 1; // 1-indexé pour correspondre à ce que voit l'étudiant
                PageLockEntity matchingLock = findLockForPage(locks, pageNumber);
                if (matchingLock != null) {
                    output.addPage(buildLockedPlaceholderPage(pageNumber, matchingLock.getPrix(), matchingLock.getTitre()));
                } else {
                    PDPage imported = output.importPage(source.getPage(i));
                    imported.setResources(source.getPage(i).getResources());
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            output.save(out);
            return out.toByteArray();
        }
    }

    /** Extrait un sous-ensemble de pages (1-indexé, inclusif) du PDF original — utilisé
     *  après vérification qu'un étudiant a bien payé pour cette plage précise. */
    public byte[] extractPages(byte[] originalBytes, int pageDebut, int pageFin) throws IOException {
        try (PDDocument source = PDDocument.load(new ByteArrayInputStream(originalBytes));
             PDDocument output = new PDDocument()) {

            int total = source.getNumberOfPages();
            int from = Math.max(1, pageDebut);
            int to = Math.min(total, pageFin);
            for (int p = from; p <= to; p++) {
                PDPage imported = output.importPage(source.getPage(p - 1));
                imported.setResources(source.getPage(p - 1).getResources());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            output.save(out);
            return out.toByteArray();
        }
    }

    private PageLockEntity findLockForPage(List<PageLockEntity> locks, int pageNumber) {
        if (locks == null) return null;
        for (PageLockEntity lock : locks) {
            if (lock.getPageDebut() != null && lock.getPageFin() != null
                    && pageNumber >= lock.getPageDebut() && pageNumber <= lock.getPageFin()) {
                return lock;
            }
        }
        return null;
    }

    private PDPage buildLockedPlaceholderPage(int pageNumber, int prix, String titre) throws IOException {
        try (PDDocument tempDoc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            tempDoc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(tempDoc, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float pageWidth = PDRectangle.A4.getWidth();
                float centerY = PDRectangle.A4.getHeight() / 2;

                cs.beginText();
                cs.setFont(font, 18);
                String title = "CONTENU VERROUILLE";
                float titleWidth = font.getStringWidth(title) / 1000 * 18;
                cs.newLineAtOffset((pageWidth - titleWidth) / 2, centerY + 35);
                cs.showText(title);
                cs.endText();

                if (titre != null && !titre.isBlank()) {
                    cs.beginText();
                    cs.setFont(font, 13);
                    float sectionTitleWidth = font.getStringWidth(titre) / 1000 * 13;
                    cs.newLineAtOffset((pageWidth - sectionTitleWidth) / 2, centerY + 10);
                    cs.showText(titre);
                    cs.endText();
                }

                cs.beginText();
                cs.setFont(fontRegular, 12);
                String subtitle = "Page " + pageNumber + " reservee a l'auteur - " + prix + " credits pour deverrouiller";
                float subtitleWidth = fontRegular.getStringWidth(subtitle) / 1000 * 12;
                cs.newLineAtOffset((pageWidth - subtitleWidth) / 2, centerY - 15);
                cs.showText(subtitle);
                cs.endText();
            }
            // La page appartient à tempDoc, qui va se fermer : on clone son dictionnaire
            // COS pour obtenir une PDPage indépendante, réutilisable dans le document appelant.
            return new PDPage((org.apache.pdfbox.cos.COSDictionary) page.getCOSObject().copy());
        }
    }
}
