package edu.keiser.deadlock.ui;

import edu.keiser.deadlock.core.Mode;
import edu.keiser.deadlock.philosophers.PhilosopherState;
import edu.keiser.deadlock.philosophers.PhilosophersSimulation.Snapshot;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.keiser.deadlock.philosophers.PhilosophersSimulation.FREE;
import static edu.keiser.deadlock.philosophers.PhilosophersSimulation.N;

public class TablePanel extends JPanel {

    private static final double LEGEND_HEIGHT = 64;

    private Snapshot snapshot;

    public TablePanel() {
        setBackground(Theme.BACKGROUND);
        setPreferredSize(new Dimension(640, 600));
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawLegend(g2);
        TableRenderer.paint(g2, 0, LEGEND_HEIGHT, getWidth(), getHeight() - LEGEND_HEIGHT, view());

        if (snapshot != null) {
            drawMeals(g2, 16, getHeight() - 5 * 22 - 52);
        }
        g2.dispose();
    }

    private TableRenderer.View view() {
        if (snapshot == null) {
            PhilosopherState[] idle = new PhilosopherState[N];
            Arrays.fill(idle, PhilosopherState.THINKING);
            int[] free = new int[N];
            Arrays.fill(free, FREE);
            return new TableRenderer.View(idle, free, List.of(), false, -1, null);
        }
        List<int[]> waits = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            int holder = snapshot.blockedBy(i);
            if (holder != FREE) {
                waits.add(new int[]{i, holder});
            }
        }
        return new TableRenderer.View(snapshot.states(), snapshot.forkOwner(), waits,
                snapshot.deadlock(), -1, null);
    }

    private void drawLegend(Graphics2D g2) {
        Object[][] items = {
                {Theme.THINKING, "Thinking"},
                {Theme.WAITING, "Hungry / waiting"},
                {Theme.WORKING, "Eating"},
                {Theme.STUCK, "Stuck forever"},
        };
        Font font = Theme.font(Font.PLAIN, 14f);
        FontMetrics fm = g2.getFontMetrics(font);
        double x = 16;
        double y = 26;
        g2.setFont(font);
        for (Object[] item : items) {
            g2.setColor((Color) item[0]);
            g2.fill(new Ellipse2D.Double(x, y - 11, 13, 13));
            g2.setColor(Theme.TEXT);
            g2.drawString((String) item[1], (float) (x + 19), (float) y);
            x += 19 + fm.stringWidth((String) item[1]) + 22;
        }
        y += 26;
        g2.setColor(Theme.WAITING);
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{7f, 6f}, 0f));
        g2.draw(new Line2D.Double(16, y - 5, 50, y - 5));
        g2.fillPolygon(new int[]{50, 58, 50}, new int[]{(int) y - 10, (int) y - 5, (int) y}, 3);
        g2.setColor(Theme.TEXT);
        g2.drawString("P1 → P2  means  \"P1 is waiting for a fork that P2 is holding\"", 68f, (float) y);
    }

    private void drawMeals(Graphics2D g2, double x, double y) {
        int[] meals = snapshot.meals();
        g2.setFont(Theme.font(Font.BOLD, 15f));
        g2.setColor(Theme.TEXT);
        g2.drawString("Meals eaten", (float) x, (float) y);

        int max = 10;
        for (int m : meals) {
            max = Math.max(max, m);
        }
        double barMax = 150;
        Font font = Theme.font(Font.BOLD, 13f);
        for (int i = 0; i < N; i++) {
            double rowY = y + 10 + i * 22;
            g2.setFont(font);
            g2.setColor(Theme.TEXT);
            g2.drawString("P" + (i + 1), (float) x, (float) (rowY + 13));
            double w = Math.max(3, barMax * meals[i] / max);
            g2.setColor(Theme.WORKING);
            g2.fill(new RoundRectangle2D.Double(x + 28, rowY + 2, w, 14, 5, 5));
            g2.setColor(Theme.TEXT);
            g2.drawString(String.valueOf(meals[i]), (float) (x + 34 + w), (float) (rowY + 13));
        }

        String caption = "";
        Color color = Theme.TEXT_MUTED;
        if (snapshot.deadlock()) {
            caption = "The bars will never grow again.";
            color = Theme.DANGER_TEXT;
        } else if (snapshot.mode() == Mode.SAFE) {
            caption = "All bars keep growing: nobody starves.";
            color = Theme.SUCCESS_TEXT;
        }
        g2.setFont(Theme.font(Font.BOLD, 13f));
        g2.setColor(color);
        g2.drawString(caption, (float) x, (float) (y + 10 + N * 22 + 14));
    }
}