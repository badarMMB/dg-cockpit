package com.dgcockpit.controller;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dgcockpit.service.PdfGenerationService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin(origins = "*")
public class PdfGenerationController {

    private final PdfGenerationService pdfService;

    /** Safelist anti-SSRF : autorise les balises usuelles + images uniquement en data: URI. */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .preserveRelativeLinks(false)
            // protocoles autorises pour <img src>
            .removeProtocols("img", "src", "http", "https", "ftp")
            .addProtocols("img", "src", "data")
            .addTags("br")
            .addAttributes(":all", "class");

    public PdfGenerationController(PdfGenerationService pdfService) {
        this.pdfService = pdfService;
    }

    @PostMapping(value = "/generate", produces = "application/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> generate(@RequestBody Map<String, String> body, HttpServletRequest request) throws Exception {
        String html = body.get("html");
        if (html == null) return ResponseEntity.badRequest().build();
        String normalizedHtml = normalizeHtml(html);
        String safeHtml = Jsoup.clean(normalizedHtml, SAFELIST);
        String css = body.getOrDefault("css", "");
        if (css.isBlank()) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("pdf-template.css")) {
                if (is != null) css = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        byte[] pdf = pdfService.generatePdfFromHtml(safeHtml, css);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document.pdf\"")
                .body(pdf);
    }

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> preview(@RequestBody Map<String, String> body) throws Exception {
        String html = body.get("html");
        if (html == null) return ResponseEntity.badRequest().build();
        String safeHtml = Jsoup.clean(normalizeHtml(html), SAFELIST);

        String css = "";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("pdf-template.css")) {
            if (is != null) css = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String fullHtml = "<!doctype html><html><head><meta charset=\"utf-8\"/><style>" + css + "</style></head><body><div class=\"a4-page\">" + safeHtml + "</div></body></html>";
        return ResponseEntity.ok(Map.of("html", fullHtml));
    }

    /**
     * Supprime uniquement les <br> qui coupent un mot en milieu de mot.
     * Ces coupures proviennent des soft line breaks Word calibres pour la largeur
     * de la page Word originale ; ils cassent les mots differemment dans notre PDF.
     *
     * Heuristique : un <br> est "milieu de mot" si :
     *   - le noeud texte precedent se termine par une lettre (pas d'espace)
     *   - ET le noeud texte suivant commence par une lettre minuscule
     * Un <br> entre deux mots ou lignes intentionnels est conserve (l'en-tete centree,
     * le bloc adresse, etc.) car ils commencent par une majuscule ou le texte precedent
     * a un espace final.
     */
    private String normalizeHtml(String html) {
        org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(html);

        for (Element block : doc.select("p, li, td, th, h1, h2, h3, h4, h5, h6")) {
            for (Element br : block.select("br")) {
                Node prev = br.previousSibling();
                Node next = br.nextSibling();

                if (!(prev instanceof TextNode) || !(next instanceof TextNode)) continue;

                String prevWhole = ((TextNode) prev).getWholeText();
                String nextWhole = ((TextNode) next).getWholeText();

                if (prevWhole.isEmpty() || nextWhole.isEmpty()) continue;

                char lastChar  = prevWhole.charAt(prevWhole.length() - 1);
                char firstChar = nextWhole.charAt(0);

                // Coupure de mot : pas d'espace avant <br> ET fragment suivant en minuscule.
                // Ex : "opérati" + <br> + "ons" → rejoint en "opérations"
                // Conserve : "GÉNÉRALE " + <br> + "DES" (espace avant) ou "DES" uppercase.
                boolean isMidWord = Character.isLetter(lastChar) && Character.isLowerCase(firstChar);

                if (isMidWord) {
                    // Fusionner les deux TextNodes en supprimant le <br>
                    TextNode merged = new TextNode(prevWhole + nextWhole);
                    prev.remove();
                    br.replaceWith(merged);
                    next.remove();
                }
            }
        }

        return doc.body().html();
    }
}
