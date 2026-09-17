package com.jantenna.gui.viewer;

import com.jantenna.GainTable;
import com.jantenna.GainTableCodec;
import com.jantenna.gui.AppPreferences;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings("serial")
public class PatternViewerPanel extends JPanel {

    private final JLabel           fileNameLabel;
    private final JSlider          freqSlider;
    private final JLabel           freqValueLabel;
    private final AntennaPlotPanel hPlane;
    private final AntennaPlotPanel vPlane;
    private final AntennaPattern3DPanel panel3D;
    private final DataTablePanel   dataTable;
    private final JTabbedPane      viewTabs;

    private GainTable loadedTable;

    public PatternViewerPanel() {
        super(new BorderLayout());

        hPlane  = new AntennaPlotPanel(AntennaPlotPanel.CutType.H_PLANE);
        vPlane  = new AntennaPlotPanel(AntennaPlotPanel.CutType.V_PLANE);
        panel3D = new AntennaPattern3DPanel();
        dataTable = new DataTablePanel();

        // North: file bar + shared frequency slider
        JPanel fileBar = new JPanel();
        fileBar.setLayout(new BoxLayout(fileBar, BoxLayout.X_AXIS));
        fileBar.add(new JLabel("File:"));
        fileBar.add(Box.createHorizontalStrut(6));
        fileNameLabel = new JLabel("none");
        fileBar.add(fileNameLabel);
        fileBar.add(Box.createHorizontalGlue());

        JButton openBtn = new JButton("Open…");
        openBtn.addActionListener(e -> chooseAndOpenFile());
        fileBar.add(openBtn);

        fileBar.add(Box.createHorizontalStrut(12));
        fileBar.add(new JSeparator(SwingConstants.VERTICAL));
        fileBar.add(Box.createHorizontalStrut(12));
        fileBar.add(new JLabel("Freq:"));
        fileBar.add(Box.createHorizontalStrut(4));

        freqSlider = new JSlider(0, 0, 0);
        freqSlider.setEnabled(false);
        freqSlider.addChangeListener(e -> {
            int fi = freqSlider.getValue();
            updateFreqValueLabel(fi);
            hPlane.setFrequencyIndex(fi);
            vPlane.setFrequencyIndex(fi);
            panel3D.setFrequencyIndex(fi);
            dataTable.setFrequencyIndex(fi);
        });
        fileBar.add(freqSlider);

        freqValueLabel = new JLabel("—");
        fileBar.add(Box.createHorizontalStrut(4));
        fileBar.add(freqValueLabel);
        fileBar.add(Box.createHorizontalStrut(8));

        add(fileBar, BorderLayout.NORTH);

        // Centre: tabbed view
        JSplitPane cutsPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, hPlane, vPlane);
        cutsPane.setResizeWeight(0.5);

        viewTabs = new JTabbedPane();
        viewTabs.addTab("2D Cuts", cutsPane);
        viewTabs.addTab("3D View", panel3D);
        viewTabs.addTab("Data Table", dataTable);

        add(viewTabs, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------

    public void openFile(Path gtablePath) {
        try {
            GainTable table = GainTableCodec.read(gtablePath);
            loadedTable = table;

            fileNameLabel.setText(gtablePath.getFileName().toString());

            int fCount = table.frequencyCount();
            freqSlider.setEnabled(fCount > 1);
            freqSlider.setMinimum(0);
            freqSlider.setMaximum(Math.max(0, fCount - 1));
            freqSlider.setValue(0);
            updateFreqValueLabel(0);

            hPlane.setTable(table, 0);
            vPlane.setTable(table, 0);
            panel3D.setTable(table, 0);
            dataTable.setTable(table, 0);

            // Enable 3D tab only for patterns that have full azimuth data
            viewTabs.setEnabledAt(1, table.azimuthCount() > 1);

            AppPreferences.addRecentFile(gtablePath.toString());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open .gtable file:\n" + ex.getMessage(),
                    "Open Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public GainTable getLoadedTable() { return loadedTable; }

    public void setPolarMode(boolean polar) {
        hPlane.setPolarMode(polar);
        vPlane.setPolarMode(polar);
    }

    // -------------------------------------------------------------------------

    private void chooseAndOpenFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "GainTable files (*.gtable)", "gtable"));
        String lastDir = AppPreferences.getLastInputDir();
        if (lastDir != null) chooser.setCurrentDirectory(new java.io.File(lastDir));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path chosen = chooser.getSelectedFile().toPath();
            AppPreferences.setLastInputDir(chosen.getParent().toString());
            openFile(chosen);
        }
    }

    private void updateFreqValueLabel(int fi) {
        if (loadedTable == null) { freqValueLabel.setText("—"); return; }
        double freq = loadedTable.frequenciesMHz()[fi];
        freqValueLabel.setText(String.format("%.1f MHz", freq));
    }
}
