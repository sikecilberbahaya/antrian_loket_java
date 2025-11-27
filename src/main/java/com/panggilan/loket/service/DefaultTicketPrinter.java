package com.panggilan.loket.service;

import com.panggilan.loket.config.TicketPrintProperties;
import com.panggilan.loket.model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class DefaultTicketPrinter implements TicketPrinter {

    private static final Logger log = LoggerFactory.getLogger(DefaultTicketPrinter.class);
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("id", "ID"));
    private static final double CM_TO_POINTS = 72d / 2.54d;
    private static final String REMINDER_MESSAGE = "Simpan nomor ini hingga anda memasuki ruangan poli";
    private static final String CARD_TITLE = "Nomor Antrian";
    private static final String SUPPORTING_MESSAGE = "Harap menunggu panggilan petugas.";

    private final TicketPrintProperties properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ticket-print-worker");
        thread.setDaemon(true);
        return thread;
    });

    public DefaultTicketPrinter(TicketPrintProperties properties) {
        this.properties = properties;
    }

    @Override
    public void printTicket(Ticket ticket) {
        if (!properties.isEnabled()) {
            return;
        }
        if (ticket == null) {
            return;
        }
        executor.execute(() -> {
            try {
                doPrint(ticket);
            } catch (Exception ex) {
                log.error("Gagal mencetak tiket {}", ticket.getNumber(), ex);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void doPrint(Ticket ticket) {
        if (GraphicsEnvironment.isHeadless()) {
            log.error("Lingkungan Java berjalan dalam mode headless, cetak tiket {} dibatalkan. Pastikan -Djava.awt.headless=false.", ticket.getNumber());
            return;
        }
        PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService == null) {
            log.warn("Tidak ada printer default yang terdeteksi. Cetak tiket {} dilewati.", ticket.getNumber());
            return;
        }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Tiket " + ticket.getNumber());
        try {
            job.setPrintService(printService);
        } catch (PrinterException ex) {
            log.error("Gagal mengikat printer default untuk tiket {}", ticket.getNumber(), ex);
            return;
        }
        PageFormat pageFormat = configurePageFormat(job);
        job.setPrintable(new TicketPrintable(ticket), pageFormat);
        try {
            job.print();
        } catch (PrinterException ex) {
            log.error("Gagal mencetak tiket {}", ticket.getNumber(), ex);
        }
    }

    private PageFormat configurePageFormat(PrinterJob job) {
        PageFormat format = job.defaultPage();
        Paper paper = format.getPaper();
        double width = cmToPoints(7.8);
        double height = cmToPoints(13.0);
        paper.setSize(width, height);
        paper.setImageableArea(0, 0, width, height);
        format.setOrientation(PageFormat.PORTRAIT);
        format.setPaper(paper);
        return format;
    }

    private double cmToPoints(double valueInCm) {
        return valueInCm * CM_TO_POINTS;
    }

    private final class TicketPrintable implements Printable {

        private final Ticket ticket;

        private TicketPrintable(Ticket ticket) {
            this.ticket = ticket;
        }

        @Override
        public int print(java.awt.Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(Color.BLACK);
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            double width = pageFormat.getImageableWidth();
            double height = pageFormat.getImageableHeight();

        Font headerFont = new Font("SansSerif", Font.BOLD, properties.getHeaderFontSize());
        int footerSize = Math.max(properties.getFooterFontSize() - 2, 8);
        Font footerFont = new Font("SansSerif", Font.PLAIN, footerSize);
        Font sectionFont = new Font("SansSerif", Font.PLAIN,
            Math.max(properties.getFooterFontSize() + 2, 12));
        Font dateFont = new Font("SansSerif", Font.PLAIN, Math.max(footerSize - 1, 8));

        String footerText = buildFooterText(ticket);
        String dateText = buildDateLine(ticket);
        g2.setFont(footerFont);
        int footerHeight = measureMultilineHeight(g2.getFontMetrics(footerFont), footerText);
        int footerPadding = footerHeight == 0 ? 0 : 30;

        int yTop = 18;
        yTop = drawCenteredLines(g2, headerFont,
            safeValue(properties.getInstitutionName(), "RS C"), width, yTop);
        yTop += 12;

        yTop = drawSectionTitle(g2, sectionFont, width, yTop);
        yTop += 4;
        if (dateText != null && !dateText.isBlank()) {
            yTop = drawCenteredLines(g2, dateFont, dateText, width, yTop);
            yTop += 8;
        }
        drawSeparator(g2, width, yTop);
        yTop += 20;

        int availableHeight = (int) Math.round(height - footerHeight - footerPadding - yTop);
        drawTicketBody(g2, ticket, width, yTop, Math.max(availableHeight, 160));

        drawFooter(g2, footerFont, footerText, width, height);

            return PAGE_EXISTS;
        }

        private int drawCenteredLines(Graphics2D g2, Font font, String text, double width, int startY) {
            if (text == null || text.isBlank()) {
                return startY;
            }
            g2.setFont(font);
            FontMetrics metrics = g2.getFontMetrics(font);
            List<String> lines = Arrays.asList(text.split("\\R"));
            int y = startY;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    y += metrics.getHeight();
                    continue;
                }
                int lineWidth = metrics.stringWidth(line);
                int x = (int) Math.max((width - lineWidth) / 2, 0);
                g2.drawString(line, x, y + metrics.getAscent());
                y += metrics.getHeight();
            }
            return y;
        }

        private int drawCenteredLinesInBox(Graphics2D g2, Font font, String text, int boxLeft, int boxWidth, int startY) {
            if (text == null || text.isBlank()) {
                return startY;
            }
            g2.setFont(font);
            FontMetrics metrics = g2.getFontMetrics(font);
            List<String> lines = Arrays.asList(text.split("\\R"));
            int y = startY;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    y += metrics.getHeight();
                    continue;
                }
                int lineWidth = metrics.stringWidth(line);
                int x = boxLeft + Math.max((boxWidth - lineWidth) / 2, 0);
                g2.drawString(line, x, y + metrics.getAscent());
                y += metrics.getHeight();
            }
            return y;
        }

        private void drawFooter(Graphics2D g2, Font font, String text, double width, double height) {
            if (text == null || text.isBlank()) {
                return;
            }
            g2.setFont(font);
            FontMetrics metrics = g2.getFontMetrics(font);
            List<String> lines = Arrays.asList(text.split("\\R"));
            int totalHeight = lines.size() * metrics.getHeight();
            int bottomMargin = 18;
            int startY = (int) Math.max(height - totalHeight - bottomMargin, 0);
            int y = startY;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    y += metrics.getHeight();
                    continue;
                }
                int lineWidth = metrics.stringWidth(line);
                int x = (int) Math.max((width - lineWidth) / 2, 0);
                g2.drawString(line, x, y + metrics.getAscent());
                y += metrics.getHeight();
            }
        }

        private String safeValue(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private String buildFooterText(Ticket ticket) {
            String address = safeValue(properties.getAddress(), "Alamat Instansi").trim();
            String wrappedAddress = wrapText(address, 32).trim();
            return wrappedAddress;
        }

        private String buildDateLine(Ticket ticket) {
            LocalDate date = ticket == null || ticket.getDisplayDate() == null
                    ? LocalDate.now()
                    : ticket.getDisplayDate();
            try {
                return DATE_FORMATTER.format(date);
            } catch (Exception ex) {
                return date.toString();
            }
        }

        private int drawSectionTitle(Graphics2D g2, Font font, double width, int startY) {
            return drawCenteredLines(g2, font, CARD_TITLE, width, startY);
        }

        private void drawSeparator(Graphics2D g2, double width, int centerY) {
            int margin = (int) Math.max(width * 0.12, 20);
            int lineY = centerY;
            g2.drawLine(margin, lineY, (int) width - margin, lineY);
        }

        private void drawTicketBody(Graphics2D g2, Ticket ticket, double width, int top, int bodyHeight) {
            int adjustedBodyHeight = Math.max(bodyHeight, 140);
            int pageWidth = (int) Math.round(width);
            int cardLeft = 12;
            int cardWidth = pageWidth - (cardLeft * 2);
            if (cardWidth < 160) {
                cardLeft = Math.max((pageWidth - 160) / 2, 8);
                cardWidth = pageWidth - (cardLeft * 2);
            }
            if (cardWidth < 120) {
                cardLeft = Math.max((pageWidth - 120) / 2, 4);
                cardWidth = pageWidth - (cardLeft * 2);
            }
            cardWidth = Math.max(cardWidth, Math.min(pageWidth - 20, pageWidth));
            g2.drawRoundRect(cardLeft, top, cardWidth, adjustedBodyHeight, 18, 18);

            int contentTop = top + 32;
            int contentHeight = adjustedBodyHeight - 64;
            if (contentHeight < 90) {
                contentHeight = Math.max(adjustedBodyHeight - 48, 80);
            }
            drawTicketNumberBlock(g2, ticket, cardLeft, cardWidth, contentTop, contentHeight);
        }

        private void drawTicketNumberBlock(Graphics2D g2, Ticket ticket, int boxLeft, int boxWidth, int top, int blockHeight) {
            String number = ticket == null ? null : ticket.getNumber();
            if (number == null || number.isBlank()) {
                return;
            }

            Font numberFont = new Font("SansSerif", Font.BOLD, properties.getTicketFontSize());
            Font reminderFont = new Font("SansSerif", Font.PLAIN, Math.max(properties.getFooterFontSize(), 11));
            Font supportingFont = reminderFont.deriveFont(Font.ITALIC, Math.max(reminderFont.getSize() - 1f, 10f));

            String wrappedReminder = wrapText(REMINDER_MESSAGE, 28);
            String supportingText = SUPPORTING_MESSAGE;

            FontMetrics numberMetrics = g2.getFontMetrics(numberFont);
            FontMetrics reminderMetrics = g2.getFontMetrics(reminderFont);
            FontMetrics supportingMetrics = g2.getFontMetrics(supportingFont);

            int reminderHeight = measureMultilineHeight(reminderMetrics, wrappedReminder);
            int supportingHeight = supportingText.isBlank() ? 0 : supportingMetrics.getHeight();
            int spacingAfterNumber = 14;
            int spacingBeforeSupporting = supportingHeight == 0 ? 0 : 10;
            int requiredHeight = numberMetrics.getHeight() + spacingAfterNumber + reminderHeight + spacingBeforeSupporting + supportingHeight;
            int verticalOffset = Math.max((blockHeight - requiredHeight) / 2, 0);

            int baseline = top + verticalOffset + numberMetrics.getAscent();
            drawCenteredString(g2, numberFont, number, boxLeft, boxWidth, baseline);

            int currentTop = baseline + spacingAfterNumber;
            currentTop = drawCenteredLinesInBox(g2, reminderFont, wrappedReminder, boxLeft, boxWidth, currentTop);
            if (!supportingText.isBlank()) {
                currentTop += spacingBeforeSupporting;
                drawCenteredLinesInBox(g2, supportingFont, supportingText, boxLeft, boxWidth, currentTop);
            }
        }

        private void drawCenteredString(Graphics2D g2, Font font, String text, int boxLeft, int boxWidth, int baselineY) {
            if (text == null || text.isBlank()) {
                return;
            }
            g2.setFont(font);
            FontMetrics metrics = g2.getFontMetrics(font);
            int lineWidth = metrics.stringWidth(text);
            int x = boxLeft + Math.max((boxWidth - lineWidth) / 2, 0);
            g2.drawString(text, x, baselineY);
        }

        private int measureMultilineHeight(FontMetrics metrics, String text) {
            if (metrics == null || text == null || text.isBlank()) {
                return 0;
            }
            int lines = countLines(text);
            return lines * metrics.getHeight();
        }

        private int countLines(String text) {
            if (text == null || text.isBlank()) {
                return 0;
            }
            String[] parts = text.split("\\r?\\n");
            int count = 0;
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        private String wrapText(String text, int maxCharactersPerLine) {
            if (text == null || text.isBlank() || maxCharactersPerLine <= 0) {
                return text == null ? "" : text;
            }
            StringBuilder builder = new StringBuilder();
            int lineLength = 0;
            for (String word : text.split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                int prospective = lineLength == 0 ? word.length() : lineLength + 1 + word.length();
                if (prospective > maxCharactersPerLine) {
                    if (builder.length() > 0) {
                        builder.append(System.lineSeparator());
                    }
                    builder.append(word);
                    lineLength = word.length();
                } else {
                    if (lineLength > 0) {
                        builder.append(' ');
                    }
                    builder.append(word);
                    lineLength = prospective;
                }
            }
            return builder.toString();
        }
    }
}
