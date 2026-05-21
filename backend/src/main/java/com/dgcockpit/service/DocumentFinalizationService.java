package com.dgcockpit.service;

import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.entity.UserSignatureAsset;
import com.dgcockpit.repository.PageAnnotationRepository;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.repository.UserSignatureAssetRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentFinalizationService.class);

    private final PdfDocumentRepository docRepo;
    private final PageAnnotationRepository annotRepo;
    private final UserSignatureAssetRepository assetRepo;
    private final MinioService minio;

    public DocumentFinalizationService(PdfDocumentRepository docRepo,
                                       PageAnnotationRepository annotRepo,
                                       UserSignatureAssetRepository assetRepo,
                                       MinioService minio) {
        this.docRepo = docRepo;
        this.annotRepo = annotRepo;
        this.assetRepo = assetRepo;
        this.minio = minio;
    }

    /**
     * Renders page N (0-indexed) as a PNG image at 150 DPI.
     */
    public byte[] renderPage(String documentId, int pageIndex) throws Exception {
        PdfDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        // Si le document est finalisé, on rend depuis le PDF final (avec signature intégrée)
        String bucket    = "FINALIZED".equals(doc.getStatus()) && doc.getFinalizedObjectKey() != null
                           ? "ged-final-documents" : doc.getBucket();
        String objectKey = "FINALIZED".equals(doc.getStatus()) && doc.getFinalizedObjectKey() != null
                           ? doc.getFinalizedObjectKey() : doc.getObjectKey();

        byte[] pdfBytes = minio.downloadBytes(bucket, objectKey);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            BufferedImage img = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
    }

    /**
     * Renders page N with SIGNATURE_ZONE / STAMP_ZONE annotations burned into the image.
     */
    public byte[] renderPageWithZones(String documentId, int pageIndex) throws Exception {
        PdfDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        String bucket    = "FINALIZED".equals(doc.getStatus()) && doc.getFinalizedObjectKey() != null
                           ? "ged-final-documents" : doc.getBucket();
        String objectKey = "FINALIZED".equals(doc.getStatus()) && doc.getFinalizedObjectKey() != null
                           ? doc.getFinalizedObjectKey() : doc.getObjectKey();

        byte[] pdfBytes = minio.downloadBytes(bucket, objectKey);
        BufferedImage base;
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            base = renderer.renderImageWithDPI(pageIndex, 150, ImageType.ARGB);
        }

        List<PageAnnotation> zones = annotRepo.findByPageId(documentId + "::" + pageIndex)
                .stream()
                .filter(a -> "SIGNATURE_ZONE".equals(a.getAnnotationType())
                          || "STAMP_ZONE".equals(a.getAnnotationType()))
                .collect(Collectors.toList());

        if (!zones.isEmpty()) {
            Graphics2D g = base.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            for (PageAnnotation ann : zones) {
                boolean isSig = "SIGNATURE_ZONE".equals(ann.getAnnotationType());
                int x = (int) (ann.getXPercent()      / 100.0 * base.getWidth());
                int y = (int) (ann.getYPercent()      / 100.0 * base.getHeight());
                int w = (int) (ann.getWidthPercent()  / 100.0 * base.getWidth());
                int h = (int) (ann.getHeightPercent() / 100.0 * base.getHeight());

                // Semi-transparent fill
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
                g.setColor(isSig ? new Color(59, 130, 246) : new Color(34, 197, 94));
                g.fillRect(x, y, w, h);

                // Dashed border
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                g.setColor(isSig ? new Color(37, 99, 235) : new Color(22, 163, 74));
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10.0f, new float[]{10.0f, 5.0f}, 0.0f));
                g.drawRect(x, y, w, h);

                // Label centré
                String label = isSig ? "Zone Signature DG" : "Zone Tampon";
                int fontSize = Math.max(10, Math.min(18, h / 3));
                g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                g.setStroke(new BasicStroke(1.0f));
                FontMetrics fm = g.getFontMetrics();
                int textW = fm.stringWidth(label);
                if (textW < w - 8 && h > fontSize + 4) {
                    int tx = x + (w - textW) / 2;
                    int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
                    g.setColor(Color.WHITE);
                    g.drawString(label, tx + 1, ty + 1);
                    g.setColor(isSig ? new Color(37, 99, 235) : new Color(22, 163, 74));
                    g.drawString(label, tx, ty);
                }
            }
            g.dispose();
        }

        // Convertir en RGB (PNG sans canal alpha)
        BufferedImage rgb = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D gRgb = rgb.createGraphics();
        gRgb.setColor(Color.WHITE);
        gRgb.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        gRgb.drawImage(base, 0, 0, null);
        gRgb.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(rgb, "png", out);
        return out.toByteArray();
    }

    /**
     * Merges all PageAnnotations for every page of this document into a new PDF,
     * stores it in ged-final-documents, marks doc as FINALIZED.
     */
    public PdfDocument finalize(String documentId) throws Exception {
        PdfDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        byte[] pdfBytes = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            int pageCount = pdf.getNumberOfPages();

            for (int p = 0; p < pageCount; p++) {
                String pageId = documentId + "::" + p;
                List<PageAnnotation> annotations = annotRepo.findByPageId(pageId);
                if (annotations.isEmpty()) continue;

                PDPage page = pdf.getPage(p);
                // Use CropBox — matches what PDFRenderer renders (accounts for trimming)
                PDRectangle cropBox = page.getCropBox();
                if (cropBox == null) cropBox = page.getMediaBox();
                float pageWidth  = cropBox.getWidth();
                float pageHeight = cropBox.getHeight();

                try (PDPageContentStream cs = new PDPageContentStream(
                        pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    for (PageAnnotation ann : annotations) {
                        if (ann.getSignatureAssetId() == null) continue;

                        UserSignatureAsset asset = assetRepo.findById(ann.getSignatureAssetId()).orElse(null);
                        if (asset == null) continue;

                        byte[] imgBytes = minio.downloadBytes(asset.getBucket(), asset.getObjectKey());
                        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(imgBytes));
                        if (buffered == null) continue;

                        PDImageXObject pdImg = LosslessFactory.createFromImage(pdf, buffered);

                        // Clamp coordinates to [0,100] to guard against drift from drag/draw
                        float xPct = (float) Math.max(0, Math.min(100, ann.getXPercent()));
                        float yPct = (float) Math.max(0, Math.min(100, ann.getYPercent()));
                        float wPct = (float) Math.max(1, Math.min(100 - xPct, ann.getWidthPercent()));
                        float hPct = (float) Math.max(1, Math.min(100 - yPct, ann.getHeightPercent()));

                        float annW = wPct / 100.0f * pageWidth;
                        float annH = hPct / 100.0f * pageHeight;
                        float annX = xPct / 100.0f * pageWidth;
                        // Browser Y=0 is page top; PDFBox Y=0 is page bottom → flip
                        float annY = pageHeight - (yPct / 100.0f * pageHeight) - annH;

                        log.info("Place annotation doc={} p={} type={} stored=[x={} y={} w={} h={}] clamped=[x={} y={} w={} h={}] pdf=[annX={} annY={} annW={} annH={}] pageW={} pageH={}",
                            documentId, p, ann.getAnnotationType(),
                            ann.getXPercent(), ann.getYPercent(), ann.getWidthPercent(), ann.getHeightPercent(),
                            xPct, yPct, wPct, hPct,
                            annX, annY, annW, annH, pageWidth, pageHeight);

                        cs.drawImage(pdImg, annX, annY, annW, annH);
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            byte[] finalBytes = out.toByteArray();

            String finalKey = UUID.randomUUID() + "_final_" + doc.getOriginalFileName();
            minio.uploadBytes("ged-final-documents", finalKey, finalBytes, "application/pdf");

            doc.setStatus("FINALIZED");
            doc.setFinalizedObjectKey(finalKey);
            doc.setUpdatedAt(LocalDateTime.now());
            return docRepo.save(doc);
        }
    }

    public byte[] renderPageFromStorage(String bucket, String objectKey, int pageIndex) throws Exception {
        byte[] pdfBytes = minio.downloadBytes(bucket, objectKey);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            BufferedImage img = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
    }

    public int getPageCount(String bucket, String objectKey) throws Exception {
        byte[] pdfBytes = minio.downloadBytes(bucket, objectKey);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            return pdf.getNumberOfPages();
        }
    }

    public byte[] downloadFinal(String documentId) throws Exception {
        PdfDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (doc.getFinalizedObjectKey() == null) throw new IllegalStateException("Document not finalized");
        return minio.downloadBytes("ged-final-documents", doc.getFinalizedObjectKey());
    }
}
