package com.jantenna.gui;

import com.jantenna.BakerVersion;
import com.jantenna.GainTable;
import com.jantenna.GainTableCodec;
import com.jantenna.gui.bake.BakePanel;
import com.jantenna.gui.viewer.PatternViewerPanel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("serial")
public class MainFrame extends JFrame {

    private final PatternViewerPanel viewerPanel;
    private final BakePanel          bakePanel;
    private final JCheckBoxMenuItem  polarCartesianToggle;
    private final JSplitPane         mainSplit;

    public MainFrame() {
        super("JAntenna " + BakerVersion.JANTENNA_VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        viewerPanel = new PatternViewerPanel();
        bakePanel   = new BakePanel(viewerPanel);

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bakePanel, viewerPanel);
        mainSplit.setResizeWeight(0.4);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        mainSplit.setDividerLocation((int)(screen.width * 0.4));

        add(mainSplit, BorderLayout.CENTER);

        polarCartesianToggle = new JCheckBoxMenuItem("Toggle Polar/Cartesian", true);
        polarCartesianToggle.addActionListener(e ->
                viewerPanel.setPolarMode(polarCartesianToggle.isSelected()));

        setJMenuBar(buildMenuBar());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        pack();
    }

    // -------------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(buildFileMenu());
        menuBar.add(buildViewMenu());
        menuBar.add(buildHelpMenu());
        return menuBar;
    }

    private JMenu buildFileMenu() {
        JMenu menu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open .gtable");
        openItem.addActionListener(e -> openGtableFile());
        menu.add(openItem);

        JMenuItem saveCsvItem = new JMenuItem("Save as CSV…");
        saveCsvItem.addActionListener(e -> saveAsCsv());
        menu.add(saveCsvItem);

        menu.addSeparator();

        JMenu recentMenu = new JMenu("Recent Files");
        recentMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                recentMenu.removeAll();
                List<String> recents = AppPreferences.getRecentFiles();
                if (recents.isEmpty()) {
                    JMenuItem none = new JMenuItem("(none)");
                    none.setEnabled(false);
                    recentMenu.add(none);
                } else {
                    for (String path : recents) {
                        JMenuItem item = new JMenuItem(path);
                        item.addActionListener(ae -> viewerPanel.openFile(Path.of(path)));
                        recentMenu.add(item);
                    }
                }
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e)   {}
        });
        menu.add(recentMenu);

        menu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        menu.add(exitItem);

        return menu;
    }

    private JMenu buildViewMenu() {
        JMenu menu = new JMenu("View");
        menu.add(polarCartesianToggle);
        return menu;
    }

    private JMenu buildHelpMenu() {
        JMenu menu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        menu.add(aboutItem);
        return menu;
    }

    private void openGtableFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("GainTable files (*.gtable)", "gtable"));
        String lastDir = AppPreferences.getLastInputDir();
        if (lastDir != null) chooser.setCurrentDirectory(new File(lastDir));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path chosen = chooser.getSelectedFile().toPath();
            AppPreferences.setLastInputDir(chosen.getParent().toString());
            viewerPanel.openFile(chosen);
        }
    }

    private void saveAsCsv() {
        GainTable table = viewerPanel.getLoadedTable();
        if (table == null) {
            JOptionPane.showMessageDialog(this, "No .gtable file is loaded.",
                    "Nothing to Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        String lastDir = AppPreferences.getLastInputDir();
        if (lastDir != null) chooser.setCurrentDirectory(new File(lastDir));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path dest = chooser.getSelectedFile().toPath();
        if (!dest.getFileName().toString().toLowerCase().endsWith(".csv")) {
            dest = dest.resolveSibling(dest.getFileName() + ".csv");
        }

        try {
            writeGainTableCsv(table, dest);
            JOptionPane.showMessageDialog(this,
                    "Exported to " + dest.getFileName(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to write CSV:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void writeGainTableCsv(GainTable table, Path dest) throws IOException {
        double[] freqs      = table.frequenciesMHz();
        double[] azimuths   = table.azimuthsDeg();
        double[] elevations = table.elevationsDeg();
        short[]  gains      = table.gainsCentiDb();
        int A = azimuths.length, E = elevations.length;

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(dest))) {
            pw.println("FREQ,AZIMUTH,ELEVATION,GAIN");
            for (int fi = 0; fi < freqs.length; fi++) {
                for (int ai = 0; ai < A; ai++) {
                    for (int ei = 0; ei < E; ei++) {
                        short v = gains[fi * A * E + ai * E + ei];
                        if (v == GainTable.SENTINEL_CENTI_DB) continue;
                        pw.printf("%.6f,%.4f,%.4f,%.2f%n",
                                freqs[fi], azimuths[ai], elevations[ei], v / 100.0);
                    }
                }
            }
        }
    }

    private void showAboutDialog() {
        String message = "<html><b>JAntenna</b><br>"
                + "Version: " + BakerVersion.JANTENNA_VERSION + "<br>"
                + "Baker version: " + BakerVersion.VERSION + "<br><br>"
                + "Antenna pattern library: bake and visualise .gtable files.</html>";
        JOptionPane.showMessageDialog(this, message, "About JAntenna",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
