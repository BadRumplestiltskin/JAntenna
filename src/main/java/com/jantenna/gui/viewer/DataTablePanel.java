package com.jantenna.gui.viewer;

import com.jantenna.GainTable;

import javax.swing.*;
import java.awt.*;

/** Tabular view of a {@link GainTable}'s raw grid values for one frequency slice. */
@SuppressWarnings("serial")
public class DataTablePanel extends JPanel {

    private final GainTableTableModel model = new GainTableTableModel();
    private final JTable table = new JTable(model);

    public DataTablePanel() {
        super(new BorderLayout());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setTable(GainTable table, int freqIndex) {
        model.setTable(table, freqIndex);
        sizeColumns();
    }

    public void setFrequencyIndex(int freqIndex) {
        model.setFrequencyIndex(freqIndex);
    }

    private void sizeColumns() {
        for (int c = 0; c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(c == 0 ? 70 : 60);
        }
    }
}
