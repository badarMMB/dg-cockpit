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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
