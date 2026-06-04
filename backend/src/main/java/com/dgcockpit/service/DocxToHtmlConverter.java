package com.dgcockpit.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.zwobble.mammoth.DocumentConverter;
import org.zwobble.mammoth.Result;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Convertit un fichier .docx (Word) en HTML semantique propre.
 * <ol>
 *     <li>Mammoth gere le squelette : paragraphes, titres, listes, tableaux, gras/italique, images Base64.</li>
 *     <li>POI lit en parallele les alignements de paragraphes (que Mammoth jette) et on les reinjecte
 *         comme classes Quill ({@code ql-align-center}, {@code ql-align-right}, {@code ql-align-justify}).</li>
 *     <li>JSoup applique une whitelist anti-XSS sur le HTML final.</li>
 * </ol>
 */
@Service
public class DocxToHtmlConverter {

    private final DocumentConverter mammoth = new DocumentConverter();
    private static final Safelist SAFELIST = buildSafelist();

    private static Safelist buildSafelist() {
        return new Safelist()
                .addTags(
                        "p", "br", "div", "span",
                        "h1", "h2", "h3", "h4", "h5", "h6",
                        "ul", "ol", "li",
                        "table", "thead", "tbody", "tr", "th", "td",
                        "strong", "em", "b", "i", "u",
                        "img", "a"
                )
                .addAttributes("img", "src", "alt", "width", "height")
                .addAttributes("a", "href", "title")
                // class autorise pour Quill (alignement) et tableaux
                .addAttributes("p", "class")
                .addAttributes("div", "class")
                .addAttributes("span", "class")
                .addAttributes("h1", "class").addAttributes("h2", "class")
                .addAttributes("h3", "class").addAttributes("h4", "class")
                .addAttributes("h5", "class").addAttributes("h6", "class")
                .addAttributes("li", "class")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "data", "http", "https");
    }

    public String convert(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();

        // --- 1. Mammoth : HTML semantique sans formatting Microsoft ---
        Result<String> result;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            result = mammoth.convertToHtml(in);
        }
        String rawHtml = result.getValue();

        // --- 2. POI : recupere l'alignement de chaque paragraphe dans l'ordre ---
        List<String> alignClasses = extractAlignClasses(bytes);

        // --- 3. Injection des classes Quill sur les <p> correspondants ---
        Document doc = Jsoup.parseBodyFragment(rawHtml);
        Elements paragraphs = doc.body().select("p");
        for (int i = 0; i < paragraphs.size() && i < alignClasses.size(); i++) {
            String cls = alignClasses.get(i);
            if (cls != null) paragraphs.get(i).addClass(cls);
        }

        // --- 4. Sanitisation finale ---
        return Jsoup.clean(doc.body().html(), SAFELIST);
    }

    /**
     * Retourne pour chaque paragraphe Word la classe Quill correspondante,
     * ou {@code null} si l'alignement est par defaut (gauche).
     * L'ordre suit strictement {@link XWPFDocument#getParagraphs()} qui matche
     * l'ordre des {@code <p>} produits par Mammoth.
     */
    private List<String> extractAlignClasses(byte[] docxBytes) {
        List<String> classes = new ArrayList<>();
        try (ByteArrayInputStream in = new ByteArrayInputStream(docxBytes);
             XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                classes.add(alignmentToQuillClass(p.getAlignment()));
            }
        } catch (Exception e) {
            // si POI echoue, on renvoie HTML brut sans alignement
            return List.of();
        }
        return classes;
    }

    private static String alignmentToQuillClass(ParagraphAlignment align) {
        if (align == null) return null;
        return switch (align) {
            case CENTER -> "ql-align-center";
            case RIGHT, END -> "ql-align-right";
            case BOTH, DISTRIBUTE -> "ql-align-justify";
            default -> null; // LEFT, START -> defaut Quill
        };
    }
}
