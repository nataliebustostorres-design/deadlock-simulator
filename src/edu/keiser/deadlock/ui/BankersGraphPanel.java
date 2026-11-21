package edu.keiser.deadlock.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean resource-allocation graph shared by both Banker's Algorithm sections.
 * Circles are processes, rounded boxes are resource types. Blue arrows show
 * allocated units, orange arrows show a request, and dashed amber arrows show
 * what a process still needs to finish.
 *
 * <p>Processes and resources sit in two aligned columns with room between
 * nodes. Arrow endpoints are staggered and labels are placed in the first free
 * position along each curve so they do not pile up on the arrows or nodes.</p>
 */
public class BankersGraphPanel extends JPanel {

    private static final Color BLUE = new Color(0x185FA5);
    private static final Color ORANGE = new Color(0xD97706);
    private static final Color AMBER = new Color(0xB77916);
    private static final Color GREEN = new Color(0x3B6D11);
    private static final Color PALE_BLUE = new Color(0xE7F0FA);

    private View view = View.empty();

    public record View(int[][] allocation, int[][] need, int[] available,
                       int[] total, int[] request, int requestProcess,
                       int currentProcess, boolean[] finished) {

        public static View empty() {
            return new View(new int[0][0], new int[0][0], new int[0], new int[0],
                    null, -1, -1, new boolean[0]);
        }

        public int processes() {
            return allocation == null ? 0 : allocation.length;
        }

        public int resources() {
            return available == null ? 0 : available.length;
        }
    }

    public BankersGraphPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(480, 600));
    }

    public void setView(View view) {
        this.view = view;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        if (w < 100 || h < 100) {
            g2.dispose();
            return;
        }

        drawLegend(g2, 16, 22, w - 32);

        int n = view.processes();
        int r = view.resources();
        if (n == 0 || r == 0) {
            center(g2, "Load data to see the graph.", w / 2.0, h / 2.0 + 10);
            g2.dispose();
            return;
        }

        double top = 58;
        double bottom = h - 22;
        double span = Math.max(60, bottom - top);

        double px = Math.max(44, w * 0.22);
        double rx = Math.min(w - 44, w * 0.78);
        if (rx - px < 150) {
            px = 38;
            rx = w - 38;
        }

        double processGap = span / Math.max(1, n);
        double resourceGap = span / Math.max(1, r);
        double processRadius = clamp(7, 19, processGap * 0.40);
        boolean compact = resourceGap < 34;
        double resourceRadius = compact
                ? clamp(7, 11, resourceGap * 0.40)
                : clamp(10, 18, resourceGap * 0.34);
        double boxW = resourceRadius * 2.6;
        double boxH = resourceRadius * 1.9;

        double[] py = positions(n, top, span);
        double[] ry = positions(r, top, span);

        Font headerFont = Theme.font(Font.BOLD, 11f);
        center(g2, "Processes", px, 46, headerFont, Theme.TEXT_MUTED);
        center(g2, "Resources", rx, 46, headerFont, Theme.TEXT_MUTED);

        List<Rectangle2D> obstacles = new ArrayList<>();
        for (double y : py) {
            double pad = processRadius + 4;
            obstacles.add(new Rectangle2D.Double(px - pad, y - pad, pad * 2, pad * 2));
        }
        for (double y : ry) {
            obstacles.add(new Rectangle2D.Double(rx - boxW / 2 - 4, y - boxH / 2 - 6,
                    boxW + 8, boxH + (compact ? 12 : 26)));
        }

        LabelPlacer labels = new LabelPlacer(g2);
        drawArrows(g2, labels, px, py, processRadius, rx, ry, boxW, resourceRadius, n, r);
        drawResource(g2, rx, ry, boxW, boxH, resourceRadius, r, compact);
        drawProcesses(g2, px, py, processRadius, n);
        labels.drawAll(obstacles);

        g2.dispose();
    }

    /** Evenly spaced positions, leaving half a slot at each end of the column. */
    private static double[] positions(int count, double top, double span) {
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = top + span * (i + 0.5) / count;
        }
        return result;
    }

    private static double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    /** Spreads connections along a node edge instead of stacking them at its centre. */
    private static double offset(int index, int count, double max) {
        if (count <= 1) {
            return 0;
        }
        return (index - (count - 1) / 2.0) * (max / ((count - 1) / 2.0));
    }

    private void drawArrows(Graphics2D g, LabelPlacer labels,
                            double px, double[] py, double processRadius,
                            double rx, double[] ry, double boxW, double resourceRadius,
                            int n, int r) {
        boolean showAllNeed = n * r <= 20;
        int[][] allocation = view.allocation();
        int[][] need = view.need();
        int[] request = view.request();

        double processX = px + processRadius + 3;
        double resourceX = rx - boxW / 2 - 3;
        double processOffset = processRadius * 0.60;
        double resourceOffset = resourceRadius * 0.60;
        double midP = (n - 1) / 2.0;
        double midR = (r - 1) / 2.0;

        for (int p = 0; p < n; p++) {
            for (int j = 0; j < r; j++) {
                // A stable lane per pair keeps parallel connections apart.
                int lane = ((p * r + j) % 9) - 4;

                // Inverted pairs would cross in the middle, so bow them around it.
                int detour = (p - midP) * (j - midR) < 0
                        ? (p < midP ? -1 : 1) : 0;

                int allocated = cell(allocation, p, j);
                if (allocated > 0) {
                    double y1 = ry[j] + offset(p, n, resourceOffset) + 5;
                    double y2 = py[p] + offset(j, r, processOffset) - 5;
                    arrow(g, labels, resourceX, y1, processX, y2,
                            -(16 + lane * 9 + detour * 24),
                            BLUE, false, String.valueOf(allocated), true);
                }

                int missing = cell(need, p, j);
                if (missing > 0 && (showAllNeed || p == view.currentProcess())) {
                    double y1 = py[p] + offset(j, r, processOffset) + 5;
                    double y2 = ry[j] + offset(p, n, resourceOffset) - 5;
                    arrow(g, labels, processX, y1, resourceX, y2,
                            16 + lane * 9 + detour * 24,
                            AMBER, true, "need " + missing, true);
                }

                int asked = request != null && j < request.length ? request[j] : 0;
                if (asked > 0 && p == view.requestProcess()) {
                    double y1 = py[p] + offset(j, r, processOffset) + 8;
                    double y2 = ry[j] + offset(p, n, resourceOffset) - 8;
                    arrow(g, labels, processX, y1, resourceX, y2,
                            30 + lane * 9 + detour * 24,
                            ORANGE, false, "ask " + asked, true);
                }
            }
        }
    }

    private void drawResource(Graphics2D g, double cx, double[] ys,
                              double boxW, double boxH, double radius,
                              int count, boolean compact) {
        Font nameFont = Theme.font(Font.BOLD, (float) Math.max(9, radius * 0.62));
        Font infoFont = Theme.font(Font.PLAIN, (float) Math.max(8, radius * 0.44));

        for (int j = 0; j < count; j++) {
            double x = cx;
            double y = ys[j];

            RoundRectangle2D box = new RoundRectangle2D.Double(
                    x - boxW / 2, y - boxH / 2, boxW, boxH, 4, 4);
            g.setColor(Color.WHITE);
            g.fill(box);
            g.setColor(Theme.TEXT_MUTED);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(box);

            center(g, "R" + j, x, y - boxH * 0.16, nameFont, Theme.TEXT);

            int allocated = 0;
            for (int[] row : view.allocation()) {
                allocated += vector(row, j);
            }
            int free = vector(view.available(), j);
            int total = vector(view.total(), j);

            center(g, free + " free / " + total, x, y + boxH * 0.16,
                    infoFont, Theme.TEXT_MUTED);
            if (!compact) {
                drawInstances(g, x, y + boxH / 2 + 7, allocated, free, Math.min(8, boxW / 5));
            }
        }
    }

    private static void drawInstances(Graphics2D g, double cx, double cy,
                                      int allocated, int free, double gap) {
        int shown = Math.min(8, allocated + free);
        double start = cx - (shown - 1) * gap / 2;
        double dot = Math.max(2.5, gap * 0.28);

        for (int i = 0; i < shown; i++) {
            double x = start + i * gap;
            g.setStroke(new BasicStroke(1f));
            if (i < Math.min(allocated, shown)) {
                g.setColor(BLUE);
                g.fillOval((int) (x - dot), (int) (cy - dot), (int) (dot * 2), (int) (dot * 2));
            } else {
                g.setColor(Color.WHITE);
                g.fillOval((int) (x - dot), (int) (cy - dot), (int) (dot * 2), (int) (dot * 2));
                g.setColor(Theme.TEXT_MUTED);
                g.drawOval((int) (x - dot), (int) (cy - dot), (int) (dot * 2), (int) (dot * 2));
            }
        }
    }

    private void drawProcesses(Graphics2D g, double cx, double[] ys,
                               double radius, int count) {
        Font font = Theme.font(Font.BOLD, (float) Math.max(9, radius * 0.85));
        for (int p = 0; p < count; p++) {
            double x = cx;
            double y = ys[p];

            boolean current = p == view.currentProcess();
            boolean done = view.finished() != null
                    && p < view.finished().length && view.finished()[p];

            g.setColor(current ? PALE_BLUE : done ? new Color(0xE7F3DF) : Color.WHITE);
            g.fillOval((int) (x - radius), (int) (y - radius),
                    (int) (radius * 2), (int) (radius * 2));
            g.setColor(current ? BLUE : done ? GREEN : Theme.TEXT_MUTED);
            g.setStroke(new BasicStroke(current ? 2.4f : 1.5f));
            g.drawOval((int) (x - radius), (int) (y - radius),
                    (int) (radius * 2), (int) (radius * 2));

            center(g, "P" + p, x, y, font, current ? BLUE : Theme.TEXT);
        }
    }

    private static void drawLegend(Graphics2D g, double x, double y, double w) {
        Font font = Theme.font(Font.PLAIN, 11f);
        FontMetrics fm = g.getFontMetrics(font);
        g.setFont(font);

        double px = x;
        px = legendArrow(g, px, y, BLUE, false, "allocated", fm);
        px = legendArrow(g, px, y, ORANGE, false, "requested", fm);
        legendArrow(g, px, y, AMBER, true, "still needs", fm);
    }

    private static double legendArrow(Graphics2D g, double x, double y, Color color,
                                      boolean dashed, String text, FontMetrics fm) {
        g.setColor(color);
        g.setStroke(dashed
                ? new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{5f, 4f}, 0f)
                : new BasicStroke(2f));
        g.drawLine((int) x, (int) y, (int) (x + 18), (int) y);

        Path2D head = new Path2D.Double();
        head.moveTo(x + 22, y);
        head.lineTo(x + 15, y - 4);
        head.lineTo(x + 15, y + 4);
        head.closePath();
        g.fill(head);

        g.setColor(Theme.TEXT_MUTED);
        g.drawString(text, (float) (x + 26), (float) (y + 4));
        return x + 26 + fm.stringWidth(text) + 16;
    }

    private static void arrow(Graphics2D g, LabelPlacer labels, double x1, double y1,
                              double x2, double y2, double bend, Color color,
                              boolean dashed, String label, boolean showLabel) {
        double cx = (x1 + x2) / 2;
        double cy = (y1 + y2) / 2 + bend;

        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, dashed ? new float[]{5f, 4f} : null, 0f));
        g.setColor(color);
        g.draw(new QuadCurve2D.Double(x1, y1, cx, cy, x2, y2));

        double angle = Math.atan2(y2 - cy, x2 - cx);
        Path2D head = new Path2D.Double();
        double size = 6;
        head.moveTo(x2, y2);
        head.lineTo(x2 - size * Math.cos(angle - 0.5), y2 - size * Math.sin(angle - 0.5));
        head.lineTo(x2 - size * Math.cos(angle + 0.5), y2 - size * Math.sin(angle + 0.5));
        head.closePath();
        g.fill(head);

        if (showLabel && label != null) {
            List<Rectangle2D> line = new ArrayList<>();
            for (int i = 1; i <= 19; i++) {
                Point2D point = pointOnQuad(x1, y1, cx, cy, x2, y2, i / 20.0);
                line.add(new Rectangle2D.Double(point.getX() - 4, point.getY() - 4, 8, 8));
            }
            Rectangle2D headBox = new Rectangle2D.Double(x2 - 8, y2 - 8, 16, 16);
            labels.add(label, x1, y1, cx, cy, x2, y2, color, line, headBox);
        }
    }

    private static Point2D pointOnQuad(double x1, double y1, double cx, double cy,
                                       double x2, double y2, double t) {
        double u = 1 - t;
        return new Point2D.Double(
                u * u * x1 + 2 * u * t * cx + t * t * x2,
                u * u * y1 + 2 * u * t * cy + t * t * y2);
    }

    private static void label(Graphics2D g, String text, double x, double y, Color color) {
        Font font = Theme.font(Font.PLAIN, 10f);
        FontMetrics fm = g.getFontMetrics(font);
        int width = fm.stringWidth(text) + 8;
        int height = 15;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(x - width / 2, y - height / 2,
                width, height, 5, 5));
        center(g, text, x, y, font, color);
    }

    private static void center(Graphics2D g, String text, double x, double y) {
        center(g, text, x, y, Theme.font(Font.PLAIN, 14f), Theme.TEXT_MUTED);
    }

    private static void center(Graphics2D g, String text, double x, double y,
                               Font font, Color color) {
        FontMetrics fm = g.getFontMetrics(font);
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0),
                (float) (y + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    private static int cell(int[][] data, int row, int column) {
        if (data == null || row < 0 || row >= data.length
                || data[row] == null || column < 0 || column >= data[row].length) {
            return 0;
        }
        return data[row][column];
    }

    private static int vector(int[] data, int index) {
        return data != null && index >= 0 && index < data.length ? data[index] : 0;
    }

    /** Collects arrow labels, then places each one in the first free slot. */
    private record LabelTask(String text, double x1, double y1, double cx, double cy,
                             double x2, double y2, Color color,
                             List<Rectangle2D> line, Rectangle2D head) {
    }

    private static final class LabelPlacer {

        private static final Font FONT = Theme.font(Font.PLAIN, 10f);
        private static final double[] T = {0.5, 0.42, 0.58, 0.34, 0.66, 0.26, 0.74};

        private final Graphics2D g;
        private final List<LabelTask> tasks = new ArrayList<>();

        LabelPlacer(Graphics2D g) {
            this.g = g;
        }

        void add(String text, double x1, double y1, double cx, double cy,
                 double x2, double y2, Color color,
                 List<Rectangle2D> line, Rectangle2D head) {
            tasks.add(new LabelTask(text, x1, y1, cx, cy, x2, y2, color, line, head));
        }

        void drawAll(List<Rectangle2D> obstacles) {
            FontMetrics fm = g.getFontMetrics(FONT);

            for (LabelTask task : tasks) {
                List<Rectangle2D> used = new ArrayList<>(obstacles);
                used.add(task.head());
                for (LabelTask other : tasks) {
                    if (other != task) {
                        used.addAll(other.line());
                        used.add(other.head());
                    }
                }

                double width = fm.stringWidth(task.text()) + 8;
                double height = 15;
                for (double t : T) {
                    Point2D point = pointOnQuad(task.x1(), task.y1(), task.cx(), task.cy(),
                            task.x2(), task.y2(), t);
                    Rectangle2D box = new Rectangle2D.Double(
                            point.getX() - width / 2, point.getY() - height / 2,
                            width, height);
                    if (!intersects(box, used)) {
                        label(g, task.text(), point.getX(), point.getY(), task.color());
                        break;
                    }
                }
            }
        }

        private static boolean intersects(Rectangle2D box, List<Rectangle2D> used) {
            for (Rectangle2D other : used) {
                if (box.intersects(other)) {
                    return true;
                }
            }
            return false;
        }
    }
}
