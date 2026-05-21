package com.jantenna.gui.viewer;

import com.jantenna.GainTable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom 3-D antenna-pattern panel.
 *
 * The gain at each (azimuth, elevation) grid point is mapped to a spherical
 * radius and rendered as a coloured polygon surface using the painter's
 * algorithm.  Mouse-drag controls viewpoint pan (left/right) and tilt
 * (up/down).
 *
 * Requires a pattern with azimuth data (A > 1).  Displays a message when
 * the loaded table is elevation-only.
 */
@SuppressWarnings("serial")
public class AntennaPattern3DPanel extends JPanel {

    // Viewpoint angles
    private double panDeg  = 30.0;
    private double tiltDeg = -25.0;  // negative = looking from above

    private GainTable table;
    private int       freqIdx = 0;

    private final JComboBox<String> freqCombo;
    private final JPanel            canvas;
    private Point dragAnchor;

    public AntennaPattern3DPanel() {
        super(new BorderLayout(0, 2));

        // Top bar: frequency selector
        freqCombo = new JComboBox<>();
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        topBar.add(new JLabel("Frequency:"));
        topBar.add(freqCombo);
        topBar.add(new JLabel("   Drag to pan / tilt"));
        add(topBar, BorderLayout.NORTH);

        // 3-D canvas
        canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (table == null || table.azimuthCount() <= 1) {
                    g.setColor(Color.GRAY);
                    g.setFont(g.getFont().deriveFont(14f));
                    String msg = "3-D view requires azimuth data (Type-13 or analytical pattern)";
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(msg,
                            (getWidth() - fm.stringWidth(msg)) / 2,
                            getHeight() / 2);
                } else {
                    render((Graphics2D) g);
                }
            }
        };
        canvas.setBackground(new Color(20, 20, 30));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { dragAnchor = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e)  {
                if (dragAnchor != null) {
                    int dx = e.getX() - dragAnchor.x;
                    int dy = e.getY() - dragAnchor.y;
                    panDeg  = (panDeg  + dx * 0.5) % 360.0;
                    tiltDeg = Math.max(-89.0, Math.min(89.0, tiltDeg + dy * 0.3));
                    dragAnchor = e.getPoint();
                    canvas.repaint();
                }
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        add(canvas, BorderLayout.CENTER);

        freqCombo.addActionListener(e -> {
            int idx = freqCombo.getSelectedIndex();
            if (idx >= 0) { freqIdx = idx; canvas.repaint(); }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setTable(GainTable table, int freqIdx) {
        this.table   = table;
        this.freqIdx = freqIdx;
        freqCombo.removeAllItems();
        if (table != null) {
            for (double f : table.frequenciesMHz()) {
                freqCombo.addItem(String.format("%.3f MHz", f));
            }
            if (freqIdx < freqCombo.getItemCount()) freqCombo.setSelectedIndex(freqIdx);
        }
        canvas.repaint();
    }

    public void setFrequencyIndex(int idx) {
        this.freqIdx = idx;
        if (idx >= 0 && idx < freqCombo.getItemCount()) freqCombo.setSelectedIndex(idx);
        canvas.repaint();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = canvas.getWidth(), h = canvas.getHeight();
        int cx = w / 2, cy = h / 2;
        double scale = Math.min(w, h) * 0.40;

        double cosPan  = Math.cos(Math.toRadians(panDeg));
        double sinPan  = Math.sin(Math.toRadians(panDeg));
        double cosTilt = Math.cos(Math.toRadians(tiltDeg));
        double sinTilt = Math.sin(Math.toRadians(tiltDeg));

        double[] azimuths   = table.azimuthsDeg();
        double[] elevations = table.elevationsDeg();
        int A = azimuths.length, E = elevations.length;
        short[] raw = table.gainsCentiDb();

        // Gain range for colour / radius normalisation (current frequency only)
        double peak = Double.NEGATIVE_INFINITY;
        int freqOffset = freqIdx * A * E;
        for (int i = freqOffset; i < freqOffset + A * E; i++) {
            if (raw[i] != GainTable.SENTINEL_CENTI_DB) {
                double gainDb = raw[i] / 100.0;
                if (gainDb > peak) peak = gainDb;
            }
        }
        if (Double.isInfinite(peak)) return;
        double lower = Math.max(-99.0, peak - 40.0);
        double range = Math.max(0.1, peak - lower);

        // Subsample: ~72 az steps, ~46 el steps for interactive performance
        int azStep = Math.max(1, A / 72);
        int elStep = Math.max(1, E / 46);

        // Pre-transform all sampled vertices once
        int azCount = (int) Math.ceil((double) A / azStep);
        int elCount = (int) Math.ceil((double) E / elStep);
        double[] sx = new double[azCount * elCount];
        double[] sy = new double[azCount * elCount];
        double[] sd = new double[azCount * elCount];  // depth
        double[] sg = new double[azCount * elCount];  // normalised gain [0,1]

        for (int ai = 0; ai < azCount; ai++) {
            int aIdx = ai * azStep;
            double azRad = Math.toRadians(azimuths[aIdx]);
            double cosAz = Math.cos(azRad), sinAz = Math.sin(azRad);
            for (int ei = 0; ei < elCount; ei++) {
                int eIdx = Math.min(ei * elStep, E - 1);
                short v = raw[freqIdx * A * E + aIdx * E + eIdx];
                double gainDb = (v == GainTable.SENTINEL_CENTI_DB) ? lower : v / 100.0;
                double r = Math.max(0.0, (gainDb - lower) / range);

                double elRad = Math.toRadians(elevations[eIdx]);
                double cosEl = Math.cos(elRad), sinEl = Math.sin(elRad);

                // World coordinates: compass convention — az=0°=North=Y+, az=90°=East=X+, Z=up
                // Matches H-plane polar (0°=top): overhead view (tilt=90°) maps 1-to-1
                double px = r * cosEl * sinAz;   // East component
                double py = r * cosEl * cosAz;   // North component
                double pz = r * sinEl;

                // Pan rotation (around Z)
                double rx  = px * cosPan - py * sinPan;
                double ry  = px * sinPan + py * cosPan;

                // Tilt rotation (around X′)
                double ry2 = ry * cosTilt - pz * sinTilt;
                double rz2 = ry * sinTilt + pz * cosTilt;

                int vi = ai * elCount + ei;
                sx[vi] = cx + scale * rx;
                sy[vi] = cy - scale * rz2;   // screen Y = -(z-after-tilt)
                sd[vi] = ry2;                 // depth: larger = farther from viewer
                sg[vi] = (gainDb - lower) / range;
            }
        }

        // Build quads (azimuth wraps at 2π)
        record Quad(int[] xs, int[] ys, double depth, float hue) {}
        List<Quad> quads = new ArrayList<>(azCount * (elCount - 1));

        for (int ai = 0; ai < azCount; ai++) {
            int ai2 = (ai + 1) % azCount;
            for (int ei = 0; ei < elCount - 1; ei++) {
                int v00 = ai  * elCount + ei;
                int v10 = ai2 * elCount + ei;
                int v11 = ai2 * elCount + (ei + 1);
                int v01 = ai  * elCount + (ei + 1);

                int[] xs = { (int) sx[v00], (int) sx[v10], (int) sx[v11], (int) sx[v01] };
                int[] ys = { (int) sy[v00], (int) sy[v10], (int) sy[v11], (int) sy[v01] };
                double depth = (sd[v00] + sd[v10] + sd[v11] + sd[v01]) / 4.0;
                float  gain  = (float) ((sg[v00] + sg[v10] + sg[v11] + sg[v01]) / 4.0);
                // Hue: blue (0.667) at gain=0 → red (0.0) at gain=1
                float hue = 0.667f * (1.0f - gain);
                quads.add(new Quad(xs, ys, depth, hue));
            }
        }

        // Painter's algorithm: draw farthest (largest depth) first
        quads.sort((a, b) -> Double.compare(b.depth(), a.depth()));

        for (Quad q : quads) {
            Color fill = Color.getHSBColor(q.hue(), 0.85f, 0.90f);
            g.setColor(fill);
            g.fillPolygon(q.xs(), q.ys(), 4);
            g.setColor(fill.darker());
            g.drawPolygon(q.xs(), q.ys(), 4);
        }

        // Draw a simple axis indicator (Z axis = vertical)
        drawAxes(g, cx, cy, scale, cosPan, sinPan, cosTilt, sinTilt);
    }

    /** Draws small labelled world-axis arrows at the bottom-left of the canvas. */
    private void drawAxes(Graphics2D g, int cx, int cy, double scale,
                          double cosPan, double sinPan, double cosTilt, double sinTilt) {
        int bx = 55, by = canvas.getHeight() - 55;
        double axLen = 40.0;
        double[][] worldAxes = { {1,0,0}, {0,1,0}, {0,0,1} };
        String[]   labels    = { "E", "N", "up" };
        Color[]    colours   = { Color.RED, Color.GREEN, new Color(100, 180, 255) };

        for (int i = 0; i < 3; i++) {
            double px = worldAxes[i][0], py = worldAxes[i][1], pz = worldAxes[i][2];
            double rx  = px * cosPan - py * sinPan;
            double ry  = px * sinPan + py * cosPan;
            double ry2 = ry * cosTilt - pz * sinTilt;
            double rz2 = ry * sinTilt + pz * cosTilt;
            int ex = (int)(bx + axLen * rx);
            int ey = (int)(by - axLen * rz2);
            g.setColor(colours[i]);
            g.setStroke(new BasicStroke(2));
            g.drawLine(bx, by, ex, ey);
            g.drawString(labels[i], ex + 2, ey - 2);
        }
        g.setStroke(new BasicStroke(1));
    }
}
