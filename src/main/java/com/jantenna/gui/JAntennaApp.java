package com.jantenna.gui;

import javax.swing.*;

public final class JAntennaApp {

    private JAntennaApp() { }

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to default L&F
            }

            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
