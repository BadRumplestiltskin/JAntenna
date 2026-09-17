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

    void setTable(GainTable table, int freqIndex) {
        this.table = table;
        this.freqIndex = freqIndex;
        fireTableStructureChanged();
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
        return String.format("%.1f°", table.elevationsDeg()[column - 1]);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (table == null) return null;
        if (columnIndex == 0) {
            return String.format("%.1f°", table.azimuthsDeg()[rowIndex]);
        }
        int elIndex = columnIndex - 1;
        int a = table.azimuthCount();
        int e = table.elevationCount();
        short raw = table.gainsCentiDb()[freqIndex * a * e + rowIndex * e + elIndex];
        if (raw == GainTable.SENTINEL_CENTI_DB) return "—";
        return String.format("%.2f", raw / 100.0);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
}
