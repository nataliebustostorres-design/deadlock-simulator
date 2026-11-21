package edu.keiser.deadlock.ui;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.awt.*;

/**
 * GraphStream-backed resource allocation graph for the "Watch it Live" tab.
 *ve
 * <p>The viewer is a {@link ViewPanel}, which is a Swing {@code JPanel}, so it
 * drops straight into the existing Swing layout. Auto layout is disabled and
 * node positions are assigned manually to keep a stable, classroom-friendly
 * layout: processes on the left, resources on the right, and the Work copy
 * underneath.</p>
 *
 * <p>Kept in its own class so the rest of the UI can fall back to the plain
 * {@link BankersGraphPanel} if GraphStream cannot be initialised.</p>
 */
public final class GraphStreamLiveGraph {

    private static final String STYLE =
            "graph { fill-color: #FFFFFF; }"
                    + "node { text-color: #2C2C2A; text-size: 11px; text-style: bold; text-alignment: center; }"
                    + "node.process { shape: circle; size: 30px; fill-color: #FFFFFF; "
                    + "stroke-color: #888780; stroke-width: 2px; }"
                    + "node.current { shape: circle; size: 34px; fill-color: #E7F0FA; "
                    + "stroke-color: #185FA5; stroke-width: 4px; }"
                    + "node.finished { shape: circle; size: 30px; fill-color: #E7F3DF; "
                    + "stroke-color: #3B6D11; stroke-width: 3px; }"
                    + "node.resource { shape: box; size: 58px, 36px; fill-color: #F7F6F2; "
                    + "stroke-color: #6B6A65; stroke-width: 2px; }"
                    + "node.work { shape: box; size: 110px, 30px; fill-color: #E7F0FA; "
                    + "stroke-color: #185FA5; stroke-width: 2px; }"
                    + "edge.need { stroke-color: #B77916; stroke-width: 2px; stroke-mode: dashes; "
                    + "arrow-shape: arrow; arrow-size: 10px, 5px; text-color: #B77916; "
                    + "text-size: 9px; z-index: 1; }"
                    + "edge.release { stroke-color: #3B6D11; stroke-width: 3px; stroke-mode: dashes; "
                    + "arrow-shape: arrow; arrow-size: 12px, 6px; text-color: #3B6D11; "
                    + "text-size: 10px; text-style: bold; z-index: 2; }"
                    + "edge.alloc { stroke-color: #185FA5; stroke-width: 3px; "
                    + "arrow-shape: arrow; arrow-size: 12px, 6px; text-color: #185FA5; "
                    + "text-size: 10px; text-style: bold; z-index: 3; }"
                    + "edge.request { stroke-color: #D97706; stroke-width: 3px; "
                    + "arrow-shape: arrow; arrow-size: 12px, 6px; text-color: #D97706; "
                    + "text-size: 10px; text-style: bold; z-index: 4; }";

    static {
        // Select the Swing viewer backend before any GraphStream graph is made.
        System.setProperty("org.graphstream.ui", "swing");
    }

    private final Graph graph;
    private final Viewer viewer;
    private final ViewPanel panel;

    public GraphStreamLiveGraph() {
        // MultiGraph (not SingleGraph) allows a need arrow and a real request
        // arrow between the same process and resource.
        graph = new MultiGraph("bankers-live");
        graph.setAttribute("ui.stylesheet", STYLE);
        viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
        viewer.disableAutoLayout();
        panel = (ViewPanel) viewer.addDefaultView(false);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    public JComponent component() {
        return panel;
    }

    /** Rebuild the graph from the shared Banker's Algorithm state. */
    public void update(BankersGraphPanel.View view) {
        graph.clear();

        int n = view.processes();
        int r = view.resources();
        if (n == 0 || r == 0) {
            panel.repaint();
            return;
        }

        int[][] allocation = safeMatrix(view.allocation(), n, r);
        int[][] need = safeMatrix(view.need(), n, r);
        int[] available = safeVector(view.available(), r);
        int[] total = safeVector(view.total(), r);
        boolean[] finished = view.finished();
        double small = (n > 10 || r > 10) ? 20 : 30;

        double[] processY = positions(n);
        double[] resourceY = positions(r);

        for (int p = 0; p < n; p++) {
            Node node = graph.addNode("P" + p);
            node.setAttribute("xy", -22, processY[p]);
            String kind = "process";
            if (p == view.currentProcess()) {
                kind = "current";
            } else if (finished != null && p < finished.length && finished[p]) {
                kind = "finished";
            }
            node.setAttribute("ui.class", kind);
            node.setAttribute("ui.label", "P" + p);
            if (small < 30) {
                node.setAttribute("ui.style", "size: " + (int) small + "px;");
            }
        }

        for (int j = 0; j < r; j++) {
            Node node = graph.addNode("R" + j);
            node.setAttribute("xy", 22, resourceY[j]);
            node.setAttribute("ui.class", "resource");
            node.setAttribute("ui.label", "R" + j + "\n" + available[j] + " free / " + total[j]);
            if (r > 10) {
                node.setAttribute("ui.style", "size: 40px, 24px; text-size: 9px;");
            }
        }

        Node work = graph.addNode("WORK");
        work.setAttribute("xy", 0, -46);
        work.setAttribute("ui.class", "work");
        work.setAttribute("ui.label", "Work (grows)");

        // Need arrows first, allocations last: z-index keeps allocations on top.
        boolean showAllNeed = n * r <= 24;
        for (int p = 0; p < n; p++) {
            for (int j = 0; j < r; j++) {
                int amount = need[p][j];
                if (amount > 0 && (showAllNeed || p == view.currentProcess())) {
                    edge("need-" + p + "-" + j, "P" + p, "R" + j,
                            "need", "needs " + amount);
                }
            }
        }

        // The process chosen in this step returns its allocation to Work.
        int current = view.currentProcess();
        if (current >= 0 && current < n) {
            for (int j = 0; j < r; j++) {
                int amount = allocation[current][j];
                if (amount > 0) {
                    edge("release-" + current + "-" + j, "P" + current, "R" + j,
                            "release", "release " + amount);
                }
            }
        }

        for (int p = 0; p < n; p++) {
            for (int j = 0; j < r; j++) {
                int amount = allocation[p][j];
                if (amount > 0) {
                    edge("alloc-" + j + "-" + p, "R" + j, "P" + p,
                            "alloc", String.valueOf(amount));
                }
            }
        }

        int[] request = view.request();
        int requester = view.requestProcess();
        if (request != null && requester >= 0 && requester < n) {
            for (int j = 0; j < r && j < request.length; j++) {
                if (request[j] > 0) {
                    edge("request-" + requester + "-" + j, "P" + requester, "R" + j,
                            "request", "ask " + request[j]);
                }
            }
        }

        panel.getCamera().setAutoFitView(true);
        panel.repaint();
    }

    public void shutdown() {
        try {
            viewer.close();
        } catch (RuntimeException ignored) {
            // GraphStream can throw while tearing down its renderer during exit.
        }
    }

    private void edge(String id, String from, String to, String kind, String label) {
        Edge edge = graph.addEdge(id, from, to, true);
        edge.setAttribute("ui.class", kind);
        edge.setAttribute("ui.label", label);
    }

    private static double[] positions(int count) {
        if (count <= 1) {
            return new double[]{0};
        }
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = 30 - (60.0 * i / (count - 1));
        }
        return result;
    }

    private static int[][] safeMatrix(int[][] data, int rows, int columns) {
        int[][] result = new int[rows][columns];
        if (data == null) {
            return result;
        }
        for (int i = 0; i < rows; i++) {
            if (i < data.length) {
                for (int j = 0; j < columns; j++) {
                    if (j < data[i].length) {
                        result[i][j] = data[i][j];
                    }
                }
            }
        }
        return result;
    }

    private static int[] safeVector(int[] data, int length) {
        int[] result = new int[length];
        if (data == null) {
            return result;
        }
        for (int i = 0; i < length && i < data.length; i++) {
            result[i] = data[i];
        }
        return result;
    }
}
