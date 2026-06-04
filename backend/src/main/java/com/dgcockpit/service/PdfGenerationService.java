package com.dgcockpit.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

@Service
public class PdfGenerationService {

    public byte[] generatePdfFromHtml(String html, String css) throws Exception {
        String rawHtml = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>" +
                "<style>" + (css != null ? css : "") + "</style></head><body>" + html + "</body></html>";

        // openhtmltopdf 1.1.31 parse en XHTML strict — Jsoup serialise en mode xml
        // pour auto-fermer les void elements (<br/>, <img/>…) et eviter SAXParseException.
        Document jsoupDoc = Jsoup.parse(rawHtml);
        jsoupDoc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false);
        String xhtml = jsoupDoc.outerHtml();

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, null);
            loadFont(builder);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private void loadFont(PdfRendererBuilder builder) {
        try {
            URL fontUrl = getClass().getClassLoader().getResource("fonts/DejaVuSans.ttf");
            if (fontUrl == null) return;
            // FSSupplier<InputStream> est un @FunctionalInterface — le lambda est infere sans import.
            builder.useFont(() -> {
                try { return fontUrl.openStream(); } catch (Exception e) { return InputStream.nullInputStream(); }
            }, "DejaVu Sans");
        } catch (Exception ignored) {}
    }
}
