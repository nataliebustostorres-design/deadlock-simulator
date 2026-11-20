package edu.keiser.deadlock;

import com.formdev.flatlaf.FlatLightLaf;
import edu.keiser.deadlock.banker.BankersGUI;
import edu.keiser.deadlock.ui.PhilosophersTab;
import edu.keiser.deadlock.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Main {

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("TabbedPane.tabHeight", 44);

        SwingUtilities.invokeLater(Main::createWindow);
    }

    private static void createWindow() {
        PhilosophersTab philosophers = new PhilosophersTab();
        BankersGUI banker = new BankersGUI();

        JTabbedPane topics = new JTabbedPane();
        topics.setFont(Theme.font(Font.BOLD, 16f));
        topics.addTab("Dining Philosophers  ·  Prevention", philosophers);
        topics.addTab("Banker's Algorithm  ·  Avoidance", banker);

        JFrame frame = new JFrame("Deadlock: prevention and avoidance");
        frame.setContentPane(topics);
        frame.setSize(1280, 900);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                philosophers.shutDown();
            }
        });
        frame.setVisible(true);
    }
}