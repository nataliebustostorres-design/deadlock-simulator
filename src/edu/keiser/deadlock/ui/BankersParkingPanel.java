
package edu.keiser.deadlock.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;

public class BankersParkingPanel extends JPanel implements Scrollable {

    private static final Color BLUE = new Color(0x2878B8);
    private static final Color AMBER = new Color(0xD99125);
    private static final Color GREEN = new Color(0x398653);

    private static final Color TEXT = new Color(0x263445);
    private static final Color MUTED = new Color(0x687585);
    private static final Color BORDER = new Color(0xDCE3EA);

    private static final int ANIMATION_DURATION = 650;

    private static final int BASE_CAR_W = 44;
    private static final int BASE_CAR_H = 64;

    private static final int LEFT = 18;
    private static final int TOP = 54;
    private static final int BOTTOM = 40;
    private static final int GAP_X = 14;
    private static final int GAP_Y = 14;
    private static final int MIN_SPACE_W = 140;
    private static final int MIN_SPACE_H = 150;
    private static final double MAX_CAR_SCALE = 0.70;

    private int[][] allocation;
    private int[][] need;
    private int[] work;
    private boolean[] finished;
    private boolean[] previousFinished;

    private int currentProcess = -1;
    private int animatingProcess = -1;

    private long animationStart;

    private Timer animationTimer;

    public BankersParkingPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(820, 520));
        setMinimumSize(new Dimension(460, 360));
    }

    // SCROLLING

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // Let the viewport choose the width so the lot can reflow its columns.
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 20;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 100;
    }

    // DATA

    public void setParkingState(
            int[][] allocation,
            int[][] need,
            int[] work,
            boolean[] finished,
            int currentProcess) {

        if (allocation == null || need == null || work == null) {
            return;
        }

        boolean[] newFinished = finished == null
                ? new boolean[allocation.length]
                : finished.clone();

        // Find the process that has just completed.
        int newlyFinished = -1;

        if (previousFinished != null) {

            for (int p = 0; p < newFinished.length; p++) {

                boolean wasFinished =
                        p < previousFinished.length
                                && previousFinished[p];

                if (newFinished[p] && !wasFinished) {
                    newlyFinished = p;
                    break;
                }
            }
        }

        this.allocation = copyMatrix(allocation);
        this.need = copyMatrix(need);
        this.work = work.clone();
        this.finished = newFinished;
        this.currentProcess = currentProcess;

        previousFinished = newFinished.clone();

        if (newlyFinished >= 0) {
            animateDeparture(newlyFinished);
        }

        repaint();
    }

    private int[][] copyMatrix(int[][] matrix) {

        int[][] copy = new int[matrix.length][];

        for (int i = 0; i < matrix.length; i++) {
            copy[i] = matrix[i].clone();
        }

        return copy;
    }

    // ANIMATION

    private void animateDeparture(int process) {

        if (animationTimer != null) {
            animationTimer.stop();
        }

        animatingProcess = process;
        animationStart = System.currentTimeMillis();

        animationTimer = new Timer(16, e -> {

            long elapsed =
                    System.currentTimeMillis() - animationStart;

            if (elapsed >= ANIMATION_DURATION) {

                animatingProcess = -1;

                animationTimer.stop();
                animationTimer = null;
            }

            repaint();
        });

        animationTimer.start();
    }

    private float animationProgress() {

        if (animatingProcess < 0) {
            return 1f;
        }

        long elapsed =
                System.currentTimeMillis() - animationStart;

        return Math.min(
                1f,
                elapsed / (float) ANIMATION_DURATION);
    }

    // PAINT

    @Override
    protected void paintComponent(Graphics graphics) {

        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (allocation == null) {
            drawEmptyMessage(g);
            g.dispose();
            return;
        }

        drawHeader(g);
        drawParkingSpaces(g);
        drawLegend(g);

        g.dispose();
    }

    // HEADER

    private void drawHeader(Graphics2D g) {

        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(TEXT);
        g.drawString("Process Parking Lot", 18, 23);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(MUTED);

        g.drawString(
                "Each car represents a process.",
                18, 41);

        // Current Work
        String workText = "Available / Work: " + vector(work);

        Font font = new Font("SansSerif", Font.BOLD, 12);
        g.setFont(font);

        FontMetrics fm = g.getFontMetrics();

        int width = fm.stringWidth(workText) + 22;
        int x = getWidth() - width - 16;

        g.setColor(new Color(0xEAF2FB));

        g.fillRoundRect(x, 7, width, 25, 9, 9);

        g.setColor(BLUE);

        g.drawString(
                workText,
                x + 11,
                24);
    }

    // PARKING SPACES

    private void drawParkingSpaces(Graphics2D g) {

        int count = allocation.length;

        if (count == 0) {
            return;
        }

        int columns = columnCount();
        int rows = (int) Math.ceil(count / (double) columns);

        int availableWidth =
                getWidth() - LEFT * 2 - GAP_X * (columns - 1);

        int availableHeight =
                getHeight() - TOP - BOTTOM - GAP_Y * (rows - 1);

        int cardWidth = Math.max(MIN_SPACE_W, availableWidth / columns);
        int cardHeight = Math.max(MIN_SPACE_H, availableHeight / rows);

        for (int p = 0; p < count; p++) {

            int col = p % columns;
            int row = p / columns;

            int x = LEFT + col * (cardWidth + GAP_X);
            int y = TOP + row * (cardHeight + GAP_Y);

            boolean isFinished =
                    p < finished.length && finished[p];

            boolean selected = p == currentProcess;

            drawParkingSpace(
                    g, x, y,
                    cardWidth, cardHeight,
                    p, isFinished, selected);
        }
    }

    //Reflows the lot from four spaces on a wide screen to one on a narrow one.
    private int columnCount() {
        int usable = Math.max(MIN_SPACE_W, getWidth() - LEFT * 2);
        return Math.max(1, Math.min(4, usable / MIN_SPACE_W));
    }

    private void drawParkingSpace(
            Graphics2D g,
            int x,
            int y,
            int width,
            int height,
            int process,
            boolean isFinished,
            boolean selected) {

        Color border = selected
                ? AMBER
                : isFinished
                ? GREEN
                : BORDER;

        // Card background
        g.setColor(Color.WHITE);

        g.fillRoundRect(
                x, y, width, height,
                14, 14);

        g.setStroke(new BasicStroke(selected ? 2.2f : 1.2f));

        g.setColor(border);

        g.drawRoundRect(
                x, y, width, height,
                14, 14);

        // Process name
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(TEXT);

        g.drawString(
                "P" + process,
                x + 10,
                y + 19);

        // Status
        String status = isFinished
                ? "DONE"
                : selected
                ? "CHECKING"
                : "WAITING";

        Color statusColor = isFinished
                ? GREEN
                : selected
                ? AMBER
                : MUTED;

        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        g.setColor(statusColor);

        FontMetrics fm = g.getFontMetrics();

        g.drawString(
                status,
                x + width - fm.stringWidth(status) - 9,
                y + 19);


        int resourceY = y + height - 50;

        int carAreaX = x + 10;
        int carAreaY = y + 28;
        int carAreaW = width - 20;
        int carAreaH = Math.max(0, (resourceY - 10) - carAreaY);

        double scale = Math.min(MAX_CAR_SCALE,
                Math.min(carAreaW / (double) BASE_CAR_W,
                        carAreaH / (double) BASE_CAR_H));
        scale = Math.max(0.25, scale);

        int carW = (int) Math.round(BASE_CAR_W * scale);
        int carH = (int) Math.round(BASE_CAR_H * scale);
        int carX = carAreaX + (carAreaW - carW) / 2;
        int carY = carAreaY + (carAreaH - carH) / 2;

        int pad = Math.max(4, (int) Math.round(8 * scale));

        g.setColor(new Color(0xF5F7FA));

        g.fillRoundRect(
                carX - pad,
                carY - pad / 2,
                carW + pad * 2,
                carH + pad,
                12, 12);

        if (isFinished) {

            drawReleased(g, carX, carY, carW, carH);

        } else {

            drawCar(g, carX, carY, scale, selected ? AMBER : BLUE);
        }

        // Animated departure uses the same uniform scale and bounds.
        if (animatingProcess == process && isFinished) {

            float t = animationProgress();

            float eased = t * t * (3f - 2f * t);

            int travel = (int) (eased * (carAreaH + 40));

            Graphics2D animation = (Graphics2D) g.create();

            animation.setComposite(
                    AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER,
                            Math.max(0f, 1f - t)));

            drawCar(animation, carX, carY - travel, scale, GREEN);

            animation.dispose();
        }

        // Resource information
        drawResourceLine(
                g,
                "Holds",
                allocation[process],
                x + 8,
                resourceY,
                width - 16,
                BLUE);

        drawResourceLine(
                g,
                "Need",
                need[process],
                x + 8,
                resourceY + 14,
                width - 16,
                AMBER);
    }

    // RESOURCE BADGES

    private void drawResourceLine(
            Graphics2D g,
            String label,
            int[] values,
            int x,
            int y,
            int width,
            Color color) {

        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        g.setColor(MUTED);

        g.drawString(label, x, y);

        Font labelFont = new Font("SansSerif", Font.BOLD, 9);

        FontMetrics labelMetrics = g.getFontMetrics(labelFont);

        int labelWidth = labelMetrics.stringWidth(label) + 8;

        if (values == null || values.length == 0) {
            return;
        }

        Font badgeFont = new Font("SansSerif", Font.BOLD, 8);

        FontMetrics badgeMetrics = g.getFontMetrics(badgeFont);

        int widestValue = 0;

        for (int i = 0; i < values.length; i++) {
            widestValue = Math.max(
                    widestValue,
                    badgeMetrics.stringWidth("R" + i + ":" + values[i]));
        }

        int availableWidth = width - labelWidth;

        int gap = 3;

        int badgeWidth = Math.max(
                widestValue + 8,
                (availableWidth - gap * (values.length - 1))
                        / values.length);

        int currentX = x + labelWidth;

        for (int i = 0; i < values.length; i++) {

            if (currentX + badgeWidth > x + width) {
                break;
            }

            g.setColor(new Color(
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue(),
                    24));

            g.fillRoundRect(
                    currentX, y - 9,
                    badgeWidth, 12,
                    5, 5);

            g.setFont(new Font("SansSerif", Font.BOLD, 8));
            g.setColor(color);

            String value = "R" + i + ":" + values[i];

            FontMetrics fm = g.getFontMetrics();

            int textX = currentX
                    + (badgeWidth - fm.stringWidth(value)) / 2;

            g.drawString(value, textX, y);

            currentX += badgeWidth + gap;
        }
    }

    // CAR DRAWING

    private void drawCar(
            Graphics2D g,
            int x,
            int y,
            double scale,
            Color bodyColor) {

        Graphics2D car = (Graphics2D) g.create();

        car.translate(x, y);

        // A single scale factor that keeps every detail in its original proportion.
        car.scale(scale, scale);

        // Shadow
        car.setColor(new Color(0, 0, 0, 30));

        car.fillRoundRect(
                5, 5, 46, 72,
                22, 22);

        // Wheels
        car.setColor(new Color(0x20252B));

        car.fillRoundRect(0, 17, 9, 20, 5, 5);
        car.fillRoundRect(45, 17, 9, 20, 5, 5);

        car.fillRoundRect(0, 48, 9, 20, 5, 5);
        car.fillRoundRect(45, 48, 9, 20, 5, 5);

        // Main body
        car.setColor(bodyColor.darker());

        car.fillRoundRect(
                6, 1, 42, 76,
                19, 19);

        car.setColor(bodyColor);

        car.fillRoundRect(
                8, 2, 38, 73,
                17, 17);

        // Front windshield
        car.setColor(new Color(0x263E52));

        car.fillRoundRect(
                14, 12, 26, 17,
                7, 7);

        // Rear windshield
        car.fillRoundRect(
                14, 50, 26, 15,
                7, 7);

        // Roof
        car.setColor(new Color(0xDCE7EF));

        car.fillRoundRect(
                15, 32, 24, 15,
                5, 5);

        // Roof detail
        car.setColor(new Color(0xA8BAC8));

        car.drawLine(27, 33, 27, 46);

        // Side mirrors
        car.setColor(bodyColor.darker());

        car.fillRoundRect(3, 30, 8, 9, 4, 4);
        car.fillRoundRect(43, 30, 8, 9, 4, 4);

        // Headlights
        car.setColor(new Color(0xFFF2C2));

        car.fillRoundRect(12, 3, 10, 4, 3, 3);
        car.fillRoundRect(32, 3, 10, 4, 3, 3);

        // Rear lights
        car.setColor(new Color(0xE65B57));

        car.fillRoundRect(12, 70, 10, 4, 3, 3);
        car.fillRoundRect(32, 70, 10, 4, 3, 3);

        // Outline
        car.setStroke(new BasicStroke(1f));
        car.setColor(bodyColor.darker().darker());

        car.drawRoundRect(
                7, 1, 40, 75,
                18, 18);

        car.dispose();
    }

    // RELEASED PROCESS

    private void drawReleased(
            Graphics2D g,
            int x,
            int y,
            int carW,
            int carH) {

        int centerX = x + carW / 2;
        int centerY = y + carH / 2 - 8;

        int radius = Math.max(10,
                Math.min(18, (int) (Math.min(carW, carH) * 0.28)));

        g.setColor(new Color(0xE5F2E9));

        g.fillOval(
                centerX - radius,
                centerY - radius,
                radius * 2,
                radius * 2);

        g.setColor(GREEN);

        g.setStroke(new BasicStroke(
                2.4f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

        // Check mark
        int check = Math.max(5, radius / 2);

        g.drawLine(
                centerX - check, centerY,
                centerX - check / 3, centerY + check / 2);

        g.drawLine(
                centerX - check / 3, centerY + check / 2,
                centerX + check, centerY - check);

        int titleSize = Math.max(8, Math.min(11, carW / 5));

        int titleY = Math.min(centerY + radius + 12, y + carH - titleSize - 4);

        g.setFont(new Font("SansSerif", Font.BOLD, titleSize));

        drawCentered(
                g, "RESOURCES RETURNED",
                x, titleY, carW);

        int subY = titleY + titleSize + 2;

        if (subY + 9 <= y + carH) {
            g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(8, titleSize - 1)));
            g.setColor(MUTED);
            drawCentered(g, "Process completed", x, subY, carW);
        }
    }

    // LEGEND

    private void drawLegend(Graphics2D g) {

        int y = getHeight() - 12;

        Font font = new Font("SansSerif", Font.PLAIN, 10);

        g.setFont(font);

        FontMetrics fm = g.getFontMetrics();

        String[] labels = {"Allocated", "Checking", "Completed"};

        Color[] colors = {BLUE, AMBER, GREEN};

        int total = 0;

        for (String label : labels) {
            total += 14 + fm.stringWidth(label);
        }

        total += 26 * (labels.length - 1);

        int x = Math.max(LEFT, (getWidth() - total) / 2);

        for (int i = 0; i < labels.length; i++) {
            drawLegendItem(g, x, y, colors[i], labels[i]);
            x += 14 + fm.stringWidth(labels[i]) + 26;
        }
    }

    private void drawLegendItem(
            Graphics2D g,
            int x,
            int y,
            Color color,
            String label) {

        g.setColor(color);

        g.fillRoundRect(
                x, y - 9,
                9, 9,
                3, 3);

        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(MUTED);

        g.drawString(label, x + 14, y);
    }
    // EMPTY STATE
    private void drawEmptyMessage(Graphics2D g) {

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(TEXT);

        drawCentered(
                g,
                "Your parking lot is empty.",
                0,
                getHeight() / 2 - 8,
                getWidth());

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(MUTED);

        drawCentered(
                g,
                "Click Load Demo to see processes and resources.",
                0,
                getHeight() / 2 + 15,
                getWidth());
    }

    // HELPERS
    private void drawCentered(
            Graphics2D g,
            String text,
            int x,
            int y,
            int width) {

        FontMetrics fm = g.getFontMetrics();

        g.drawString(
                text,
                x + (width - fm.stringWidth(text)) / 2,
                y);
    }

    private String vector(int[] values) {

        if (values == null) {
            return "—";
        }

        StringBuilder result = new StringBuilder("[");

        for (int i = 0; i < values.length; i++) {

            if (i > 0) {
                result.append(", ");
            }

            result.append("R")
                    .append(i)
                    .append(": ")
                    .append(values[i]);
        }

        return result.append("]").toString();
    }

    public void shutdown() {

        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }

        animatingProcess = -1;
    }
}
