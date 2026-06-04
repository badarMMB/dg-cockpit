package com.dgcockpit.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Remplace le marqueur texte [SIGN_DG] dans un .docx par l'image de signature.
 *
 * L'utilisateur tape [SIGN_DG] à l'endroit souhaité dans Collabora.
 * Lors de la signature finale, ce service cherche le marqueur dans tous les
 * paragraphes, le supprime et insère l'image de signature à sa place.
 */
@Service
public class DocxSignatureService {

    public static final String MARKER = "[SIGN_DG]";

    /**
     * Remplace le premier occurrence de [SIGN_DG] dans le .docx par l'image.
     *
     * @param docxBytes  contenu du .docx
     * @param imageBytes image PNG/JPEG de la signature
     * @param imageExt   "png" ou "jpeg"
     * @return .docx modifié, ou docxBytes inchangé si le marqueur est absent
     */
    public byte[] injectSignature(byte[] docxBytes, byte[] imageBytes, String imageExt)
            throws Exception {

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {

            boolean replaced = replaceInBody(doc.getParagraphs(), doc, imageBytes, imageExt);

            // Chercher aussi dans les tableaux si pas trouvé dans le corps principal
            if (!replaced) {
                outer:
                for (XWPFTable table : doc.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            if (replaceInBody(cell.getParagraphs(), doc, imageBytes, imageExt)) {
                                break outer;
                            }
                        }
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** Retourne true si le marqueur a été trouvé et remplacé dans la liste de paragraphes. */
    private boolean replaceInBody(List<XWPFParagraph> paragraphs, XWPFDocument doc,
                                   byte[] imageBytes, String imageExt) throws Exception {

        for (XWPFParagraph para : paragraphs) {
            String full = para.getText();
            if (full == null || !full.contains(MARKER)) continue;

            // Effacer tous les runs du paragraphe
            int runCount = para.getRuns().size();
            for (int i = runCount - 1; i >= 0; i--) {
                para.removeRun(i);
            }

            // Insérer l'image de signature dans un nouveau run
            XWPFRun run = para.createRun();
            int pictureType = imageExt.equalsIgnoreCase("jpeg") || imageExt.equalsIgnoreCase("jpg")
                ? XWPFDocument.PICTURE_TYPE_JPEG
                : XWPFDocument.PICTURE_TYPE_PNG;

            // Largeur : 4 cm, hauteur proportionnelle à 2 cm
            run.addPicture(
                new ByteArrayInputStream(imageBytes),
                pictureType,
                "signature." + imageExt,
                Units.toEMU(4 * 360000L / 914400.0 * 914400),   // 4 cm en EMU
                Units.toEMU(2 * 360000L / 914400.0 * 914400)    // 2 cm en EMU
            );

            return true;
        }
        return false;
    }

    /** Indique si le document .docx contient le marqueur [SIGN_DG]. */
    public boolean hasMarker(byte[] docxBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            return doc.getParagraphs().stream()
                .anyMatch(p -> p.getText() != null && p.getText().contains(MARKER));
        } catch (Exception e) {
            return false;
        }
    }
}
