//package contest.mobicom_contest.contract.service;
//
//import contest.mobicom_contest.contract.dto.TextBlock;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import javax.imageio.ImageIO;
//import java.awt.*;
//import java.awt.font.FontRenderContext;
//import java.awt.font.LineBreakMeasurer;
//import java.awt.font.TextAttribute;
//import java.awt.font.TextLayout;
//import java.awt.image.BufferedImage;
//import java.io.IOException;
//import java.text.AttributedString;
//import java.util.*;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//public class TranslationImageService {
//
//    private static final int MAX_Y_DIFFERENCE = 20;
//
//    public BufferedImage createSimpleTranslatedImage(String translatedText) throws Exception {
//        int width = 1200;
//        int height = 1600;
//        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//        Graphics2D g2d = image.createGraphics();
//
//        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
//
//       g2d.setColor(Color.WHITE);
//        g2d.fillRect(0, 0, width, height);
//
//        g2d.setFont(new Font("Arial", Font.PLAIN, 24));
//        drawFormattedText(g2d, translatedText, 80, 200, width - 160);
//
//        g2d.dispose();
//        return image;
//    }
//
//    private void drawFormattedText(Graphics2D g2d, String text, int x, int y, int maxWidth) {
//        String[] paragraphs = text.split("\n\n");
//        int currentY = y;
//
//        Font normalFont = new Font("Arial", Font.PLAIN, 24);
//        Font boldFont = new Font("Arial", Font.BOLD, 24);
//        Font headerFont = new Font("Arial", Font.BOLD, 26);
//
//        for (String paragraph : paragraphs) {
//            boolean isNumberedSection = paragraph.matches("^\\d+\\..*");
//
//            if (isNumberedSection) {
//                g2d.setFont(headerFont);
//            } else {
//                g2d.setFont(normalFont);
//            }
//
//            AttributedString attributedText = new AttributedString(paragraph);
//            attributedText.addAttribute(TextAttribute.FONT, g2d.getFont());
//
//            FontRenderContext frc = g2d.getFontRenderContext();
//            LineBreakMeasurer measurer = new LineBreakMeasurer(attributedText.getIterator(), frc);
//
//            int paragraphStart = currentY;
//            measurer.setPosition(0);
//
//            while (measurer.getPosition() < paragraph.length()) {
//                TextLayout layout = measurer.nextLayout(maxWidth);
//                currentY += layout.getAscent();
//                layout.draw(g2d, x, currentY);
//                currentY += layout.getDescent() + layout.getLeading();
//            }
//
//            currentY += 20;
//        }
//    }
//}
