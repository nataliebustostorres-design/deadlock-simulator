package edu.keiser.deadlock.ui;

import javax.swing.*;
import java.awt.*;

public class PhilosophersTab extends JPanel {

    private final PhilosophersScreen live =  new PhilosophersScreen();

    public PhilosophersTab() {
        super(new BorderLayout());
        JTabbedPane sub = new JTabbedPane();
        sub.setFont(Theme.font(Font.PLAIN, 14f));
        sub.addTab("Learn it step by step", new WalkthroughScreen());
        sub.addTab("Watch it live", live);
        add(sub, BorderLayout.CENTER);
    }

    public void shutDown() {
        live.shutdown();
    }
}
