package com.jantenna.gui.bake;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class BatchResultTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = { "File", "Status", "Message" };

    private final List<Object[]> rows = new ArrayList<>();

    public void addRow(String file, String status, String message) {
        rows.add(new Object[]{ file, status, message });
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    public void updateRow(int index, String status, String message) {
        if (index >= 0 && index < rows.size()) {
            rows.get(index)[1] = status;
            rows.get(index)[2] = message;
            fireTableRowsUpdated(index, index);
        }
    }

    public void clear() {
        int size = rows.size();
        rows.clear();
        if (size > 0) fireTableRowsDeleted(0, size - 1);
    }

    @Override public int getRowCount()    { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Object getValueAt(int row, int col) {
        return rows.get(row)[col];
    }
}
