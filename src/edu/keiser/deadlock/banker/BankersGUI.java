package edu.keiser.deadlock.banker;

import com.formdev.flatlaf.FlatLightLaf;
import edu.keiser.deadlock.ui.BankersGraphPanel;
import edu.keiser.deadlock.ui.BankersLiveScreen;
import edu.keiser.deadlock.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;

/**
 * Banker's Algorithm screen with two complementary tabs:
 * <ul>
 *   <li><b>Watch it Live</b> — a self-contained demo that starts only when the
 *       student clicks its <b>Load Demo</b> button.</li>
 *   <li><b>Tables &amp; Safety Check</b> — the editable Maximum, Allocation,
 *       Available and Need matrices, all controls, and the results. This tab
 *       works only with data the student enters.</li>
 * </ul>
 * The tabs keep separate data on purpose and share the same graph design. All
 * calculations still come from {@link BankersAlgorithm}; this class never
 * reimplements the algorithm.
 */
public class BankersGUI extends JPanel {

    private static final Color ACCENT = new Color(0x185FA5);
    private static final Color GOOD_BG = new Color(0xE7F3DF);
    private static final Color BAD_BG = new Color(0xFCEBEB);
    private static final Color IDLE_BG = new Color(0xEFEDE7);
    private static final Color CURRENT_BG = new Color(0xE7F0FA);

    private static final int DEFAULT_PROCESSES = 5;
    private static final int DEFAULT_RESOURCES = 3;

    private final JSpinner processSpinner =
            new JSpinner(new SpinnerNumberModel(DEFAULT_PROCESSES, 1, 20, 1));
    private final JSpinner resourceSpinner =
            new JSpinner(new SpinnerNumberModel(DEFAULT_RESOURCES, 1, 15, 1));
    private final JSpinner requestProcessSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 4, 1));

    private final JTable maxTable;
    private final JTable allocationTable;
    private final JTable availableTable;
    private final JTable needTable;

    private final JTextField[] requestFieldsHolder = new JTextField[0];
    private JPanel requestFieldsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    private JTextField[] requestFields = requestFieldsHolder;

    private final BankersGraphPanel graph = new BankersGraphPanel();
    private final JLabel statusLabel = new JLabel();
    private final JLabel explanation = new JLabel();
    private final JButton nextButton = new JButton("Next Step");
    private final JButton advancedToggle = new JButton("Advanced  \u25be");
    private final JPanel advancedPanel = new JPanel(new BorderLayout(0, 8));

    private final JPanel tablesPanel;
    private final BankersLiveScreen live;

    private BankersAlgorithm algorithm;

    private int[][] maxData;
    private int[][] allocationData;
    private int[][] needData;
    private int[] availableData;

    private int[] sequence;
    private int step = -1;
    private boolean checked;
    private boolean safe;

    private int[] lastRequest;
    private int lastRequestProcess = -1;
    private boolean requestChangedData;
    private RequestSnapshot preRequestSnapshot;

    /** Non-request view state, saved so Clear can put the safety check back. */
    private record RequestSnapshot(String status, Color foreground, Color background,
                                   String explanation, boolean checked, boolean safe,
                                   int[] sequence, int step) {
    }

    public BankersGUI() {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        setBorder(new EmptyBorder(10, 14, 10, 14));

        maxTable = makeTable();
        allocationTable = makeTable();
        availableTable = makeTable();
        needTable = makeTable();
        needTable.setEnabled(false);

        tablesPanel = new JPanel(new BorderLayout(0, 8));
        tablesPanel.setBackground(Theme.BACKGROUND);
        tablesPanel.add(buildToolbar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                matricesPanel(), graphCard());
        split.setResizeWeight(0.62);
        split.setDividerLocation(0.62);
        split.setBorder(null);
        tablesPanel.add(split, BorderLayout.CENTER);
        tablesPanel.add(buildBottom(), BorderLayout.SOUTH);

        live = new BankersLiveScreen();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.font(Font.BOLD, 14f));
        tabs.addTab("Watch it Live", live);
        tabs.addTab("Tables & Safety Check", tablesPanel);
        add(tabs, BorderLayout.CENTER);

        processSpinner.addChangeListener(e -> generateTables());
        resourceSpinner.addChangeListener(e -> generateTables());

        generateTables();
        clearResults();
    }

    /* ----------------------------- construction ---------------------------- */

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setOpaque(false);

        bar.add(button("Generate Tables", this::generateTables));
        bar.add(separator());
        bar.add(numberField("Processes", processSpinner));
        bar.add(numberField("Resources", resourceSpinner));
        bar.add(separator());
        bar.add(button("Find Need", this::findNeed));
        bar.add(button("Run Algorithm", this::runAlgorithm));

        nextButton.setFont(Theme.font(Font.BOLD, 14f));
        nextButton.setForeground(ACCENT);
        nextButton.setFocusPainted(false);
        nextButton.addActionListener(e -> nextStep());
        bar.add(nextButton);
        bar.add(button("Reset", this::reset));

        bar.add(separator());
        advancedToggle.setFont(Theme.font(Font.PLAIN, 13f));
        advancedToggle.setForeground(Theme.TEXT_MUTED);
        advancedToggle.setFocusPainted(false);
        advancedToggle.addActionListener(e -> toggleAdvanced());
        bar.add(advancedToggle);
        return bar;
    }

    private JComponent matricesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        panel.add(matrixCard("Maximum",
                "Most resources this process may ever use.", maxTable), gbc);

        gbc.gridx = 1;
        panel.add(matrixCard("Allocated",
                "Resources this process is using now.", allocationTable), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(matrixCard("Available",
                "Resources free right now.", availableTable), gbc);

        gbc.gridx = 1;
        panel.add(matrixCard("Still Needs",
                "Resources this process still needs to finish.", needTable), gbc);

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    private JComponent graphCard() {
        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Shape shape = new RoundRectangle2D.Double(0.5, 0.5,
                        getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(Color.WHITE);
                g2.fill(shape);
                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Resource Allocation Graph");
        title.setFont(Theme.font(Font.BOLD, 14f));
        title.setForeground(Theme.TEXT);
        card.add(title, BorderLayout.NORTH);

        graph.setOpaque(false);
        card.add(graph, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildBottom() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        statusLabel.setFont(Theme.font(Font.BOLD, 14f));
        statusLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
        statusLabel.setOpaque(true);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        statusRow.setOpaque(false);
        statusRow.add(statusLabel);
        panel.add(statusRow, BorderLayout.NORTH);

        explanation.setFont(Theme.font(Font.PLAIN, 13f));
        explanation.setForeground(Theme.TEXT);
        JPanel explainCard = new JPanel(new BorderLayout());
        explainCard.setBackground(Theme.CARD);
        explainCard.setBorder(new EmptyBorder(10, 12, 10, 12));
        explainCard.add(explanation, BorderLayout.CENTER);
        panel.add(explainCard, BorderLayout.CENTER);

        advancedPanel.setOpaque(false);
        advancedPanel.setVisible(false);
        advancedPanel.add(buildRequestControls(), BorderLayout.CENTER);
        panel.add(advancedPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildRequestControls() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Shape shape = new RoundRectangle2D.Double(0.5, 0.5,
                        getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(Theme.CARD);
                g2.fill(shape);
                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);
                g2.dispose();
            }
        };
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel note = new JLabel(
                "<html>A request is granted only if the system stays safe.</html>");
        note.setFont(Theme.font(Font.PLAIN, 12f));
        note.setForeground(Theme.TEXT_MUTED);
        wrapper.add(note, BorderLayout.NORTH);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.add(smallLabel("Process"));
        requestProcessSpinner.setPreferredSize(new Dimension(70, 28));
        row.add(requestProcessSpinner);
        row.add(requestFieldsPanel);
        row.add(button("Submit Request", this::submitRequest));
        row.add(button("Clear", this::clearRequest));
        wrapper.add(row, BorderLayout.CENTER);
        return wrapper;
    }

    /* ------------------------------- actions ------------------------------- */

    private void generateTables() {
        rebuildTables((Integer) processSpinner.getValue(),
                (Integer) resourceSpinner.getValue(), false);
    }

    /** Rebuilds every matrix model so no old value can survive a reset. */
    private void rebuildTables(int processes, int resources, boolean blank) {
        maxTable.setModel(resourceModel(processes, resources, blank));
        allocationTable.setModel(resourceModel(processes, resources, blank));
        availableTable.setModel(availableModel(resources, blank));
        needTable.setModel(resourceModel(processes, resources, blank));

        buildRequestFields(processes, resources);
        algorithm = null;
        clearResults();
    }

    /* Original action names, kept so every existing feature stays reachable. */
    private void findNeed() {
        calculateNeed();
    }

    private void runAlgorithm() {
        runSafetyCheck();
    }

    private void requestResources() {
        submitRequest();
    }

    /**
     * Returns the tables section to its starting state: blank matrices, no
     * request, no result, and an empty graph. The live tab keeps its own demo
     * data and is intentionally not affected.
     */
    public void reset() {
        commitTableEdits();

        // Restore the default size first; the spinner listeners rebuild the
        // models as a side effect. The explicit rebuild below guarantees a
        // blank state even when the spinners did not change.
        processSpinner.setValue(DEFAULT_PROCESSES);
        resourceSpinner.setValue(DEFAULT_RESOURCES);

        int processes = (Integer) processSpinner.getValue();
        int resources = (Integer) resourceSpinner.getValue();
        // Reset restores the same zero-filled tables shown at startup.
        rebuildTables(processes, resources, false);
        requestProcessSpinner.setValue(0);
    }

    /** Stops the live demo timer when the main window closes. */
    public void shutDown() {
        live.shutdown();
    }

    private void buildRequestFields(int processes, int resources) {
        requestProcessSpinner.setModel(
                new SpinnerNumberModel(0, 0, Math.max(0, processes - 1), 1));

        requestFieldsPanel.removeAll();
        requestFields = new JTextField[resources];
        for (int j = 0; j < resources; j++) {
            JPanel field = new JPanel();
            field.setOpaque(false);
            field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));

            JLabel label = new JLabel("R" + j);
            label.setFont(Theme.font(Font.PLAIN, 11f));
            label.setForeground(Theme.TEXT_MUTED);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);

            JTextField input = new JTextField("0", 3);
            input.setFont(Theme.font(Font.PLAIN, 13f));
            input.setMaximumSize(new Dimension(60, 28));
            input.setHorizontalAlignment(JTextField.CENTER);

            requestFields[j] = input;
            field.add(label);
            field.add(Box.createVerticalStrut(3));
            field.add(input);
            requestFieldsPanel.add(field);
        }
        requestFieldsPanel.revalidate();
        requestFieldsPanel.repaint();
    }

    private void calculateNeed() {
        try {
            commitTableEdits();
            BankersAlgorithm current = readAlgorithmFromTables();
            syncFrom(current);
            updateNeedTable();
            checked = false;
            safe = false;
            sequence = null;
            step = -1;
            lastRequest = null;
            lastRequestProcess = -1;
            requestChangedData = false;
            preRequestSnapshot = null;

            setStatus("NEEDS FOUND", Theme.TEXT, IDLE_BG);
            String example = needData.length > 0 ? labels(needData[0]) : "\u2014";
            explain("Need = Maximum \u2212 Allocated. For P0: " + example + ".");
            publish();
        } catch (Exception ex) {
            clearResults();
            showError(ex.getMessage());
        }
    }

    private void runSafetyCheck() {
        try {
            commitTableEdits();
            BankersAlgorithm current = readAlgorithmFromTables();
            syncFrom(current);
            updateNeedTable();

            checked = true;
            safe = current.isSafe();
            sequence = safe ? current.getSafeSequence() : null;
            step = -1;
            lastRequest = null;
            lastRequestProcess = -1;
            requestChangedData = false;
            preRequestSnapshot = null;

            showSafetyResult();
            publish();
        } catch (Exception ex) {
            clearResults();
            showError(ex.getMessage());
        }
    }

    private void nextStep() {
        if (!checked || !safe || sequence == null || sequence.length == 0) {
            explain("Run the algorithm first to find a safe order.");
            publish();
            return;
        }
        if (step >= sequence.length - 1) {
            showFinalResult();
            publish();
            return;
        }

        step++;
        int p = sequence[step];
        int[] before = workBefore(step);
        int[] after = workBefore(step + 1);

        String stepText = "<b>Step " + (step + 1) + " of " + sequence.length + ": P" + p
                + " can finish.</b> Needs " + labels(needData[p]) + "; Work has "
                + labels(before) + ". It returns " + labels(allocationData[p])
                + ", so Work becomes " + labels(after) + ".";

        if (step == sequence.length - 1) {
            setStatus("SAFE STATE", Theme.SUCCESS_TEXT, GOOD_BG);
            explain(stepText + " <b>All processes can finish: "
                    + sequenceText(sequence) + ".</b>");
        } else {
            explain(stepText);
        }
        publish();
    }

    private void showFinalResult() {
        setStatus("SAFE STATE", Theme.SUCCESS_TEXT, GOOD_BG);
        explain("<b>All processes can finish: " + sequenceText(sequence)
                + ".</b> The system is safe. This was only a check, so Available is unchanged.");
    }

    /** Commits any in-progress cell edit so the action reads what the user typed. */
    private void commitTableEdits() {
        JTable[] tables = {maxTable, allocationTable, availableTable, needTable};
        for (JTable table : tables) {
            if (table.isEditing()) {
                TableCellEditor editor = table.getCellEditor();
                if (editor == null || !editor.stopCellEditing()) {
                    // A rejected edit must not leave an old value in the model
                    // while the editor still displays the new one.
                    if (editor != null) {
                        editor.cancelCellEditing();
                    }
                }
            }
        }
    }

    private void submitRequest() {
        if (preRequestSnapshot == null) {
            preRequestSnapshot = captureRequestSnapshot();
        }
        try {
            commitTableEdits();
            BankersAlgorithm current = readAlgorithmFromTables();
            int process = (Integer) requestProcessSpinner.getValue();

            int[] request = new int[requestFields.length];
            for (int j = 0; j < request.length; j++) {
                request[j] = readInteger(requestFields[j].getText());
            }
            lastRequest = request;
            lastRequestProcess = process;

            int[][] need = current.getNeedMatrix();
            int[] available = current.getAvailableResources();
            for (int j = 0; j < request.length; j++) {
                if (request[j] > need[process][j]) {
                    deny("P" + process + " asked for R" + j + ": " + request[j]
                            + ", but only needs " + need[process][j] + ". Nothing was given.", current);
                    return;
                }
                if (request[j] > available[j]) {
                    deny("P" + process + " asked for R" + j + ": " + request[j]
                            + ", but only " + available[j] + " is free. Nothing was given.", current);
                    return;
                }
            }

            boolean granted = current.requestResources(process, request);
            syncFrom(current);
            updateNeedTable();
            checked = true;
            safe = granted;
            sequence = granted ? current.getSafeSequence() : null;
            step = -1;

            if (granted) {
                populateTables(current.getMaxMatrix(), current.getAllocationMatrix(),
                        current.getAvailableResources());
                requestChangedData = true;
                setStatus("REQUEST GRANTED", Theme.SUCCESS_TEXT, GOOD_BG);
                explain("P" + process + " gets " + labels(request)
                        + ". The system stays safe: " + sequenceText(sequence) + ".");
            } else {
                deny("P" + process + "'s request leaves no safe order. "
                        + "Nothing was given.", current);
                return;
            }
            publish();
        } catch (Exception ex) {
            clearResults();
            showError(ex.getMessage());
        }
    }

    private void deny(String reason, BankersAlgorithm current) {
        syncFrom(current);
        updateNeedTable();
        checked = false;
        safe = false;
        sequence = null;
        step = -1;
        setStatus("REQUEST DENIED", Theme.DANGER_TEXT, BAD_BG);
        explain(reason);
        publish();
    }

    /**
     * Clears only the resource request: its inputs, result message, and graph
     * arrows. Tables, safety-check results, and the rest of the interface stay
     * exactly as they are.
     */
    private void clearRequest() {
        for (JTextField field : requestFields) {
            field.setText("");
        }
        requestProcessSpinner.setValue(0);

        if (preRequestSnapshot != null) {
            if (requestChangedData) {
                // A granted request changed the data, so show the safety result
                // for the current tables rather than the old pre-request result.
                RequestSnapshot saved = preRequestSnapshot;
                preRequestSnapshot = null;
                showCurrentSafetyResult(saved.step());
            } else {
                restoreRequestSnapshot();
            }
        }

        lastRequest = null;
        lastRequestProcess = -1;
        requestChangedData = false;
        publish();
    }

    private void toggleAdvanced() {
        boolean show = !advancedPanel.isVisible();
        advancedPanel.setVisible(show);
        advancedToggle.setText(show ? "Advanced  \u25b4" : "Advanced  \u25be");
        revalidate();
        repaint();
    }

    private void clearResults() {
        // Drop every cached snapshot as well, otherwise the graph would keep
        // drawing the previous run after the tables have been cleared.
        algorithm = null;
        maxData = null;
        allocationData = null;
        needData = null;
        availableData = null;

        checked = false;
        safe = false;
        sequence = null;
        step = -1;
        lastRequest = null;
        lastRequestProcess = -1;
        requestChangedData = false;
        preRequestSnapshot = null;
        setStatus("", Theme.TEXT_MUTED, IDLE_BG);
        explain("Enter your numbers, then choose <b>Find Need</b> and "
                + "<b>Run Algorithm</b>.");
        publish();
    }

    private RequestSnapshot captureRequestSnapshot() {
        return new RequestSnapshot(statusLabel.getText(), statusLabel.getForeground(),
                statusLabel.getBackground(), explanation.getText(),
                checked, safe, sequence == null ? null : sequence.clone(), step);
    }

    private void restoreRequestSnapshot() {
        RequestSnapshot saved = preRequestSnapshot;
        statusLabel.setText(saved.status());
        statusLabel.setForeground(saved.foreground());
        statusLabel.setBackground(saved.background());
        statusLabel.setVisible(saved.status() != null && !saved.status().isEmpty());
        explanation.setText(saved.explanation());
        checked = saved.checked();
        safe = saved.safe();
        sequence = saved.sequence() == null ? null : saved.sequence().clone();
        step = saved.step();
        preRequestSnapshot = null;
    }

    /** Redisplays safety for the current tables after a request changed them. */
    private void showCurrentSafetyResult(int previousStep) {
        try {
            commitTableEdits();
            BankersAlgorithm current = readAlgorithmFromTables();
            checked = true;
            safe = current.isSafe();
            sequence = safe ? current.getSafeSequence() : null;
            step = previousStep >= 0 && sequence != null
                    ? Math.min(previousStep, sequence.length - 1) : -1;
            showSafetyResult();
        } catch (Exception ex) {
            clearResults();
            showError(ex.getMessage());
        }
    }

    private void showSafetyResult() {
        if (safe && sequence != null) {
            setStatus("SAFE STATE", Theme.SUCCESS_TEXT, GOOD_BG);
            explain("<b>Safe.</b> Finish order: " + sequenceText(sequence)
                    + ". Each process gets what it needs and returns what it holds.");
        } else {
            setStatus("UNSAFE STATE", Theme.DANGER_TEXT, BAD_BG);
            // Show the values that were actually read so an unsafe result can
            // be checked against the tables instead of looking like a dead run.
            explain("<b>Unsafe.</b> No order lets every process finish. "
                    + "Work starts at " + labels(availableData) + "; "
                    + needSummary(needData)
                    + ". A deadlock may happen later, but not now.");
        }
    }

    /* ------------------------------- helpers ------------------------------- */

    private int[] workBefore(int count) {
        int[] work = availableData == null ? new int[0] : availableData.clone();
        if (sequence == null || allocationData == null) {
            return work;
        }
        for (int i = 0; i < count && i < sequence.length; i++) {
            for (int j = 0; j < work.length; j++) {
                work[j] += allocationData[sequence[i]][j];
            }
        }
        return work;
    }

    private void syncFrom(BankersAlgorithm current) {
        maxData = current.getMaxMatrix();
        allocationData = current.getAllocationMatrix();
        needData = current.getNeedMatrix();
        availableData = current.getAvailableResources();
    }

    private void populateTables(int[][] max, int[][] allocation, int[] available) {
        for (int i = 0; i < max.length; i++) {
            for (int j = 0; j < available.length; j++) {
                maxTable.setValueAt(max[i][j], i, j + 1);
                allocationTable.setValueAt(allocation[i][j], i, j + 1);
            }
        }
        for (int j = 0; j < available.length; j++) {
            availableTable.setValueAt(available[j], 0, j);
        }
    }

    private void updateNeedTable() {
        if (needData == null) {
            return;
        }
        for (int i = 0; i < needData.length; i++) {
            for (int j = 0; j < needData[i].length; j++) {
                needTable.setValueAt(needData[i][j], i, j + 1);
            }
        }
    }

    private BankersAlgorithm readAlgorithmFromTables() {
        int processes = (Integer) processSpinner.getValue();
        int resources = (Integer) resourceSpinner.getValue();

        int[][] max = readMatrix(maxTable, processes, resources);
        int[][] allocation = readMatrix(allocationTable, processes, resources);
        int[] available = readAvailable(resources);

        BankersAlgorithm current = new BankersAlgorithm(processes, resources);
        current.setData(max, allocation, available);
        algorithm = current;
        return current;
    }

    private int[][] readMatrix(JTable table, int processes, int resources) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        int[][] matrix = new int[processes][resources];
        for (int i = 0; i < processes; i++) {
            for (int j = 0; j < resources; j++) {
                matrix[i][j] = readInteger(table.getValueAt(i, j + 1));
            }
        }
        return matrix;
    }

    private int[] readAvailable(int resources) {
        if (availableTable.isEditing()) {
            availableTable.getCellEditor().stopCellEditing();
        }
        int[] result = new int[resources];
        for (int j = 0; j < resources; j++) {
            result[j] = readInteger(availableTable.getValueAt(0, j));
        }
        return result;
    }

    private int readInteger(Object value) {
        // Reset leaves cells visually empty. Treat those as zero so a student
        // can fill in only the values that matter instead of every cell.
        if (value == null || value.toString().trim().isEmpty()) {
            return 0;
        }
        try {
            int number = Integer.parseInt(value.toString().trim());
            if (number < 0) {
                throw new IllegalArgumentException("Resource values cannot be negative.");
            }
            return number;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Use whole numbers only in the tables.");
        }
    }

    private void publish() {
        int n = needData == null ? 0 : needData.length;
        int r = availableData == null ? 0 : availableData.length;

        boolean[] finished = new boolean[n];
        int current = -1;
        if (checked && safe && sequence != null && step >= 0 && step < sequence.length) {
            for (int i = 0; i <= step; i++) {
                finished[sequence[i]] = true;
            }
            current = sequence[step];
        }

        int[] total = new int[r];
        if (allocationData != null) {
            for (int[] row : allocationData) {
                for (int j = 0; j < r && row != null && j < row.length; j++) {
                    total[j] += row[j];
                }
            }
        }
        if (availableData != null) {
            for (int j = 0; j < r; j++) {
                total[j] += availableData[j];
            }
        }

        BankersGraphPanel.View view = new BankersGraphPanel.View(
                allocationData == null ? new int[0][0] : allocationData,
                needData == null ? new int[0][0] : needData,
                availableData == null ? new int[0] : availableData,
                total,
                lastRequest, lastRequestProcess, current, finished);

        // This graph belongs to the tables tab. The live tab runs its own demo.
        graph.setView(view);

        nextButton.setEnabled(checked && safe && sequence != null
                && step < sequence.length - 1);

        maxTable.repaint();
        allocationTable.repaint();
        needTable.repaint();
    }

    private void setStatus(String text, Color foreground, Color background) {
        statusLabel.setText(text);
        statusLabel.setForeground(foreground);
        statusLabel.setBackground(background);
        statusLabel.setVisible(text != null && !text.isEmpty());
    }

    private void explain(String html) {
        explanation.setText("<html><div style='width:1040px'>" + html + "</div></html>");
    }

    private static String labels(int[] values) {
        if (values == null || values.length == 0) {
            return "\u2014";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append("R").append(i).append(": ").append(values[i]);
        }
        return text.toString();
    }

    private static String needSummary(int[][] need) {
        if (need == null || need.length == 0) {
            return "Need is \u2014";
        }
        StringBuilder text = new StringBuilder("Need is ");
        for (int i = 0; i < need.length; i++) {
            if (i > 0) {
                text.append("; ");
            }
            text.append("P").append(i).append(" ").append(labels(need[i]));
        }
        return text.toString();
    }

    private static String sequenceText(int[] sequence) {
        if (sequence == null || sequence.length == 0) {
            return "\u2014";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < sequence.length; i++) {
            if (i > 0) {
                text.append(" \u2192 ");
            }
            text.append("P").append(sequence[i]);
        }
        return text.toString();
    }

    /* ----------------------------- small widgets --------------------------- */

    private JTable makeTable() {
        JTable table = new JTable();
        table.setRowHeight(26);
        table.setFont(Theme.font(Font.PLAIN, 13f));
        table.setForeground(Theme.TEXT);
        table.setGridColor(Theme.CARD_BORDER);
        table.setSelectionBackground(CURRENT_BG);
        table.setSelectionForeground(Theme.TEXT);
        table.setDefaultRenderer(Object.class, new ValueRenderer());
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    private JPanel matrixCard(String title, String hint, JTable table) {
        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Shape shape = new RoundRectangle2D.Double(0.5, 0.5,
                        getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(Color.WHITE);
                g2.fill(shape);
                g2.setColor(Theme.CARD_BORDER);
                g2.draw(shape);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(Font.BOLD, 14f));
        titleLabel.setForeground(Theme.TEXT);
        JLabel hintLabel = new JLabel(hint);
        hintLabel.setFont(Theme.font(Font.PLAIN, 11f));
        hintLabel.setForeground(Theme.TEXT_MUTED);
        head.add(titleLabel);
        head.add(Box.createVerticalStrut(2));
        head.add(hintLabel);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(Theme.CARD_BORDER));
        scroll.getViewport().setBackground(Color.WHITE);

        card.add(head, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private static DefaultTableModel resourceModel(int processes, int resources) {
        return resourceModel(processes, resources, false);
    }

    private static DefaultTableModel resourceModel(int processes, int resources,
                                                   boolean blank) {
        String[] columns = new String[resources + 1];
        columns[0] = "Process";
        for (int j = 0; j < resources; j++) {
            columns[j + 1] = "R" + j;
        }
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0;
            }
        };
        Object value = blank ? "" : 0;
        for (int i = 0; i < processes; i++) {
            Object[] row = new Object[resources + 1];
            row[0] = "P" + i;
            Arrays.fill(row, 1, row.length, value);
            model.addRow(row);
        }
        return model;
    }

    private static DefaultTableModel availableModel(int resources) {
        return availableModel(resources, false);
    }

    private static DefaultTableModel availableModel(int resources, boolean blank) {
        String[] columns = new String[resources];
        for (int j = 0; j < resources; j++) {
            columns[j] = "R" + j;
        }
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        Object[] row = new Object[resources];
        Arrays.fill(row, blank ? "" : 0);
        model.addRow(row);
        return model;
    }

    private class ValueRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!selected && checked && safe && sequence != null && step >= 0
                    && step < sequence.length && row == sequence[step]
                    && table != availableTable) {
                setBackground(CURRENT_BG);
                setForeground(ACCENT);
            } else if (!selected) {
                setBackground(Color.WHITE);
                setForeground(Theme.TEXT);
            }
            return this;
        }
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(Theme.font(Font.BOLD, 12f));
            setBackground(Theme.TABLE);
            setForeground(Theme.TEXT);
            return this;
        }
    }

    private static JComponent separator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        return separator;
    }

    private static JPanel numberField(String text, JSpinner spinner) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 11f));
        label.setForeground(Theme.TEXT_MUTED);
        spinner.setPreferredSize(new Dimension(72, 28));
        panel.add(label);
        panel.add(spinner);
        return panel;
    }

    private static JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 12f));
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(Theme.font(Font.PLAIN, 13f));
        button.setForeground(Theme.TEXT);
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Input Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("TextComponent.arc", 12);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Banker's Algorithm Simulator");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new BankersGUI());
            frame.setSize(1280, 900);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
