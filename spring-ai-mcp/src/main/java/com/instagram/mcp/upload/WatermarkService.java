package com.instagram.mcp.upload;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// Stamps a text watermark at the top-right corner of an image and writes the
// result as a sibling file prefixed with "_wm_". The original is never modified.
// Caller is responsible for deleting the returned path after use.
@Service
@Slf4j
public class WatermarkService {

    public static final String TEMP_PREFIX = "_wm_";

    private static final String TEXT    = "@naturesrawclicks";
    private static final int    PADDING = 50;
    private static final int    FONT_SIZE = 100;

    // Shadow offset in pixels — keeps text readable on any background.
    private static final int SHADOW_OFFSET = 2;

    public Path applyWatermark(Path source) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            throw new IOException("Cannot decode image: " + source);
        }

        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Font font = new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();

            int textWidth = fm.stringWidth(TEXT);
            int x = image.getWidth()  - textWidth - PADDING;
            int y = PADDING + fm.getAscent();

            // Drop shadow for contrast on any image colour.
            g.setColor(new Color(0, 0, 0, 160));
            g.drawString(TEXT, x + SHADOW_OFFSET, y + SHADOW_OFFSET);

            // White watermark text.
            g.setColor(new Color(255, 255, 255, 220));
            g.drawString(TEXT, x, y);
        } finally {
            g.dispose();
        }

        String filename  = source.getFileName().toString();
        int    dot       = filename.lastIndexOf('.');
        String ext       = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "jpg";
        String format    = "png".equals(ext) ? "PNG" : "JPEG";

        Path dest = source.getParent().resolve(TEMP_PREFIX + filename);
        ImageIO.write(image, format, dest.toFile());
        log.debug("applyWatermark: wrote {} (format={})", dest.getFileName(), format);
        return dest;
    }
}
