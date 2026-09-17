package com.jantenna.gui.viewer;

import com.jantenna.GainTable;

import javax.swing.table.AbstractTableModel;

/**
 * Raw-grid view of one frequency slice of a {@link GainTable}: rows are
 * azimuth entries, columns are elevation entries (column 0 is the azimuth
 * label). Shows the stored grid values directly, no interpolation.
 */
@SuppressWarnings("serial")
class GainTableTableModel extends AbstractTableModel {

    private GainTable table;
    private int freqIndex;

    // Axis labels never change while a table is loaded, so format them once
    // rather than on every repaint of every visible cell.
    private String[] azimuthLabels  = new String[0];
    private String[] elevationLabels = new String[0];

    void setTable(GainTable table, int freqIndex) {
        this.table = table;
        this.freqIndex = freqIndex;
        this.azimuthLabels   = formatDegrees(table == null ? null : table.azimuthsDeg());
        this.elevationLabels = formatDegrees(table == null ? null : table.elevationsDeg());
        fireTableStructureChanged();
    }

    private static String[] formatDegrees(double[] axis) {
        if (axis == null) return new String[0];
        String[] out = new String[axis.length];
        for (int i = 0; i < axis.length; i++) out[i] = String.format("%.1f°", axis[i]);
        return out;
    }

    void setFrequencyIndex(int freqIndex) {
        this.freqIndex = freqIndex;
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return table == null ? 0 : table.azimuthCount();
    }

    @Override
    public int getColumnCount() {
        return table == null ? 0 : 1 + table.elevationCount();
    }

    @Override
    public String getColumnName(int column) {
        if (table == null) return "";
        if (column == 0) return "Az \\ El";
        return elevationLabels[column - 1];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (table == null) return null;
        if (columnIndex == 0) {
            return azimuthLabels[rowIndex];
        }
        double dbi = table.readDbi(freqIndex, rowIndex, columnIndex - 1);
        if (dbi <= GainTable.SENTINEL_DBI) return "—";
        return String.format("%.2f", dbi);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
}
