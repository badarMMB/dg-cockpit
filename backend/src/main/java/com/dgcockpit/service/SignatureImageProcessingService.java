package com.dgcockpit.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
public class SignatureImageProcessingService {

    private static final int THRESHOLD = 230;

    /**
     * Removes near-white background pixels (R>230, G>230, B>230) → transparent.
     * Preserves colored ink (blue signatures, red stamps, etc.).
     * Crops empty margins and returns a transparent PNG.
     */
    public byte[] removeBackground(byte[] imageBytes) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (src == null) throw new IllegalArgumentException("Cannot decode image");

        BufferedImage rgba = toRgba(src);
        applyThreshold(rgba);

        BufferedImage cropped = cropTransparentMargins(rgba);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(cropped, "png", out);
        return out.toByteArray();
    }

    private BufferedImage toRgba(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        result.createGraphics().drawImage(src, 0, 0, null);
        return result;
    }

    private void applyThreshold(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (r > THRESHOLD && g > THRESHOLD && b > THRESHOLD) {
                    img.setRGB(x, y, 0x00FFFFFF); // fully transparent
                }
            }
        }
    }

    private BufferedImage cropTransparentMargins(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int minX = w, minY = h, maxX = 0, maxY = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
                if (alpha > 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (minX > maxX || minY > maxY) return img; // fully transparent

        // add 4px padding
        int padX = minX > 4 ? minX - 4 : 0;
        int padY = minY > 4 ? minY - 4 : 0;
        int padMaxX = maxX + 4 < w ? maxX + 4 : w - 1;
        int padMaxY = maxY + 4 < h ? maxY + 4 : h - 1;

        return img.getSubimage(padX, padY, padMaxX - padX + 1, padMaxY - padY + 1);
    }
}
