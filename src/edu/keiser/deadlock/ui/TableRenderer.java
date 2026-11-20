package edu.keiser.deadlock.ui;

import edu.keiser.deadlock.philosophers.PhilosopherState;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;

import static edu.keiser.deadlock.philosophers.PhilosophersSimulation.FREE;
import static edu.keiser.deadlock.philosophers.PhilosophersSimulation.N;

public final class TableRenderer {

    private TableRenderer() {
    }

    public record View(PhilosopherState[] states, int[] forkOwner, List<int[]> waits,
                       boolean deadlock, int ring, boolean[] highlightForks) {
    }

    private static final Color TABLE = new Color(0xEFE7D8);
    private static final Color TABLE_EDGE = new Color(0xC9B99A);
    private static final Color PLATE_RING = new Color(0xE4DCCB);
    private static final Color SAUCE = new Color(0xF6DFA8);
    private static final Color NOODLE = new Color(0xC98A2B);
    private static final Color SKIN = new Color(0xE8C4A0);
    private static final Color HIGHLIGHT = new Color(0x185FA5);

    public static void paint(Graphics2D g, double x, double y, double w, double h, View v) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double s = Math.min(w / 400.0, h / 345.0);
        double cx = x + w / 2;
        double cy = y + h / 2 + 7.5 * s;
        double seatR = 126 * s;
        double tableR = 74 * s;

        drawForksOnTableCount(g, x, y, s, v);
        drawTable(g, cx, cy, tableR, s, v);
        drawWaitArrows(g, cx, cy, seatR, s, v);
        drawFreeForks(g, cx, cy, tableR, s, v);
        drawPeople(g, cx, cy, seatR, s, v);
    }

    private static void drawForksOnTableCount(Graphics2D g, double x, double y, double s, View v) {
        int free = 0;
        for (int owner : v.forkOwner()) {
            if (owner == FREE) {
                free++;
            }
        }
        g.setFont(Theme.font(Font.BOLD, (float) (13 * s)));
        g.setColor(v.deadlock() ? Theme.DANGER_TEXT : Theme.TEXT_MUTED);
        g.drawString("Forks on the table: " + free, (float) (x + 6 * s), (float) (y + 18 * s));
    }

    private static void drawTable(Graphics2D g, double cx, double cy, double tableR, double s, View v) {
        g.setColor(TABLE);
        g.fill(circle(cx, cy, tableR));
        g.setColor(TABLE_EDGE);
        g.setStroke(new BasicStroke((float) (2 * s)));
        g.draw(circle(cx, cy, tableR));

        g.setColor(Color.WHITE);
        g.fill(circle(cx, cy, 17 * s));
        g.setColor(TABLE_EDGE);
        g.setStroke(new BasicStroke((float) (1.5 * s)));
        g.draw(circle(cx, cy, 17 * s));
        g.setColor(SAUCE);
        g.fill(circle(cx, cy, 12 * s));
        drawNoodles(g, cx, cy, 12 * s, s);

        for (int k = 0; k < N; k++) {
            Point2D.Double p = point(cx, cy, angle(k), tableR * 0.66);
            g.setColor(Color.WHITE);
            g.fill(circle(p.x, p.y, 12 * s));
            g.setColor(TABLE_EDGE);
            g.setStroke(new BasicStroke((float) (1.5 * s)));
            g.draw(circle(p.x, p.y, 12 * s));
            g.setColor(PLATE_RING);
            g.setStroke(new BasicStroke((float) s));
            g.draw(circle(p.x, p.y, 8 * s));
            if (v.states()[k] == PhilosopherState.EATING && !v.deadlock()) {
                drawNoodles(g, p.x, p.y, 8 * s, s);
            }
        }
    }

    private static void drawWaitArrows(Graphics2D g, double cx, double cy, double seatR, double s, View v) {
        Color color = v.deadlock() ? Theme.STUCK : Theme.WAITING;
        Stroke stroke = v.deadlock()
                ? new BasicStroke((float) (2.5 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                : new BasicStroke((float) (2.5 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{(float) (6 * s), (float) (5 * s)}, 0f);

        for (int[] wait : v.waits()) {
            Point2D.Double from = point(cx, cy, angle(wait[0]), seatR);
            Point2D.Double to = point(cx, cy, angle(wait[1]), seatR);
            Point2D.Double control = point(cx, cy, midAngle(angle(wait[0]), angle(wait[1])), seatR * 0.72);
            Point2D.Double start = towards(from, control, 30 * s);
            Point2D.Double end = towards(to, control, 32 * s);

            g.setColor(color);
            g.setStroke(stroke);
            g.draw(new QuadCurve2D.Double(start.x, start.y, control.x, control.y, end.x, end.y));
            drawArrowHead(g, control, end, 9 * s);
        }
    }

    private static void drawFreeForks(Graphics2D g, double cx, double cy, double tableR, double s, View v) {
        for (int f = 0; f < N; f++) {
            if (v.forkOwner()[f] != FREE) {
                continue;
            }
            boolean hi = highlighted(v, f);
            double a = angle(f) - 36;
            Point2D.Double p = point(cx, cy, a, tableR * 0.7);

            AffineTransform saved = g.getTransform();
            g.translate(p.x, p.y);
            g.rotate(Math.toRadians(a + 90));
            drawFork(g, s, hi ? HIGHLIGHT : Theme.FORK, hi ? 2.6 : 2);
            g.setTransform(saved);

            Point2D.Double label = point(cx, cy, a, tableR * 0.7 - 24 * s);
            drawCentered(g, "F" + (f + 1), label.x, label.y,
                    Theme.font(Font.BOLD, (float) (11 * s)), hi ? HIGHLIGHT : Theme.TEXT_MUTED);
        }
    }

    private static void drawPeople(Graphics2D g, double cx, double cy, double seatR, double s, View v) {
        for (int k = 0; k < N; k++) {
            Point2D.Double p = point(cx, cy, angle(k), seatR);
            double rot = Math.toRadians(angle(k) + 90);
            Color body = v.deadlock() ? Theme.STUCK : colorOf(v.states()[k]);

            AffineTransform saved = g.getTransform();
            g.translate(p.x, p.y);
            g.rotate(rot);

            for (int f = 0; f < N; f++) {
                if (v.forkOwner()[f] == k) {
                    int side = (f == k) ? -1 : 1;
                    AffineTransform beforeFork = g.getTransform();
                    g.translate(side * 30 * s, -2 * s);
                    drawFork(g, s, Theme.FORK, 2.2);
                    g.setTransform(beforeFork);
                }
            }

            Shape shoulders = new Ellipse2D.Double(-27 * s, -15 * s, 54 * s, 30 * s);
            g.setColor(body);
            g.fill(shoulders);
            if (v.ring() == k) {
                g.setColor(HIGHLIGHT);
                g.setStroke(new BasicStroke((float) (3 * s)));
                g.draw(shoulders);
            }
            g.setColor(SKIN);
            g.fill(circle(0, 3 * s, 12 * s));
            g.setColor(new Color(0, 0, 0, 38));
            g.setStroke(new BasicStroke((float) s));
            g.draw(circle(0, 3 * s, 12 * s));
            g.setTransform(saved);

            for (int f = 0; f < N; f++) {
                if (v.forkOwner()[f] == k) {
                    int side = (f == k) ? -1 : 1;
                    Point2D.Double l = rotate(p, side * 30 * s, -26 * s, rot);
                    drawCentered(g, "F" + (f + 1), l.x, l.y, Theme.font(Font.BOLD, (float) (11 * s)), Theme.TEXT_MUTED);
                }
            }

            double side = Math.abs(Math.cos(Math.toRadians(angle(k))));
            Point2D.Double label = point(cx, cy, angle(k), seatR + 30 * s + 20 * s * side);
            drawCentered(g, "P" + (k + 1), label.x, label.y - 7 * s,
                    Theme.font(Font.BOLD, (float) (13 * s)), Theme.TEXT);
            drawCentered(g, stateText(v, k), label.x, label.y + 8 * s,
                    Theme.font(Font.PLAIN, (float) (11 * s)), stateTextColor(v, k));
        }
    }

    private static void drawFork(Graphics2D g, double s, Color color, double width) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) (width * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(0, -11 * s, 0, 5 * s));
        g.draw(new Line2D.Double(-4 * s, 5 * s, 4 * s, 5 * s));
        for (int t = -1; t <= 1; t++) {
            g.draw(new Line2D.Double(4 * t * s, 5 * s, 4 * t * s, 13 * s));
        }
    }

    private static void drawNoodles(Graphics2D g, double x, double y, double r, double s) {
        g.setColor(NOODLE);
        g.setStroke(new BasicStroke((float) (2 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double a = new Path2D.Double();
        a.moveTo(x - 0.6 * r, y);
        a.quadTo(x - 0.3 * r, y - 0.5 * r, x, y);
        a.quadTo(x + 0.3 * r, y + 0.5 * r, x + 0.6 * r, y);
        g.draw(a);
        Path2D.Double b = new Path2D.Double();
        b.moveTo(x - 0.5 * r, y + 0.35 * r);
        b.quadTo(x - 0.25 * r, y - 0.05 * r, x, y + 0.35 * r);
        b.quadTo(x + 0.25 * r, y + 0.75 * r, x + 0.5 * r, y + 0.35 * r);
        g.draw(b);
    }

    private static String stateText(View v, int k) {
        if (v.deadlock()) {
            return "stuck";
        }
        return switch (v.states()[k]) {
            case THINKING -> "thinking";
            case HUNGRY -> "hungry";
            case EATING -> "eating";
        };
    }

    private static Color stateTextColor(View v, int k) {
        if (v.deadlock()) {
            return Theme.DANGER_TEXT;
        }
        return switch (v.states()[k]) {
            case THINKING -> Theme.TEXT_MUTED;
            case HUNGRY -> Theme.WARNING_TEXT;
            case EATING -> Theme.SUCCESS_TEXT;
        };
    }

    private static Color colorOf(PhilosopherState state) {
        return switch (state) {
            case THINKING -> Theme.THINKING;
            case HUNGRY -> Theme.WAITING;
            case EATING -> Theme.WORKING;
        };
    }

    private static boolean highlighted(View v, int fork) {
        return v.highlightForks() != null && v.highlightForks()[fork];
    }

    private static double angle(int philosopher) {
        return -90 + philosopher * 360.0 / N;
    }

    private static Point2D.Double point(double cx, double cy, double angleDeg, double r) {
        double a = Math.toRadians(angleDeg);
        return new Point2D.Double(cx + r * Math.cos(a), cy + r * Math.sin(a));
    }

    private static Point2D.Double rotate(Point2D.Double origin, double lx, double ly, double rot) {
        return new Point2D.Double(origin.x + lx * Math.cos(rot) - ly * Math.sin(rot),
                origin.y + lx * Math.sin(rot) + ly * Math.cos(rot));
    }

    private static double midAngle(double a, double b) {
        double diff = ((b - a + 540) % 360) - 180;
        return a + diff / 2;
    }

    private static Point2D.Double towards(Point2D.Double from, Point2D.Double to, double d) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double len = Math.hypot(dx, dy);
        return new Point2D.Double(from.x + dx / len * d, from.y + dy / len * d);
    }

    private static Ellipse2D.Double circle(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
    }

    private static void drawArrowHead(Graphics2D g, Point2D.Double from, Point2D.Double tip, double size) {
        double angle = Math.atan2(tip.y - from.y, tip.x - from.x);
        Path2D.Double head = new Path2D.Double();
        head.moveTo(tip.x, tip.y);
        head.lineTo(tip.x - size * Math.cos(angle - 0.5), tip.y - size * Math.sin(angle - 0.5));
        head.lineTo(tip.x - size * Math.cos(angle + 0.5), tip.y - size * Math.sin(angle + 0.5));
        head.closePath();
        g.fill(head);
    }

    static void drawCentered(Graphics2D g, String text, double x, double y, Font font, Color color) {
        FontMetrics fm = g.getFontMetrics(font);
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0),
                (float) (y + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

}
