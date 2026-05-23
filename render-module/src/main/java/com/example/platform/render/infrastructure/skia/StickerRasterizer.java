package com.example.platform.render.infrastructure.skia;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rasterizes sticker assets to PNG (Java2D; Skia-native can replace in dedicated Worker).
 */
@Component
public class StickerRasterizer {

    private static final Logger log = LoggerFactory.getLogger(StickerRasterizer.class);

    public Path rasterizeToPng(Path sourceImage, Path outputPng, int targetWidth, int targetHeight) throws IOException {
        Files.createDirectories(outputPng.getParent());
        BufferedImage src = ImageIO.read(sourceImage.toFile());
        if (src == null) {
            throw new IOException("Unreadable sticker image: " + sourceImage);
        }
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(src.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        ImageIO.write(canvas, "png", outputPng.toFile());
        log.debug("Rasterized sticker {} -> {}", sourceImage, outputPng);
        return outputPng;
    }
}
