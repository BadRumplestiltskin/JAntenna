package com.jantenna.gui.viewer;

import com.jantenna.GainTable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PolarPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.DefaultPolarItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

@SuppressWarnings("serial")
public class AntennaPlotPanel extends JPanel {

    public enum CutType { H_PLANE, V_PLANE }

    private final CutType      cutType;
    private final JLabel       titleLabel;
    private final JSlider      angleSlider;
    private final JLabel       angleValueLabel;
    private final JPanel       chartHolder;
    private final JToggleButton polarToggle;

    private GainTable table;
    private int       freqIdx        = 0;
    private int       angleIdx       = 0;
    private boolean   polarMode      = true;
    private ChartPanel chartPanel;
    private String    peakAnnotation;

    public AntennaPlotPanel(CutType cutType) {
        super(new BorderLayout());
        this.cutType = cutType;

        titleLabel = new JLabel(cutType == CutType.H_PLANE
                ? "H-Plane (Azimuth Cut)" : "V-Plane (Elevation Cut)", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        add(titleLabel, BorderLayout.NORTH);

        chartHolder = new JPanel(new BorderLayout());
        chartHolder.setBackground(Color.LIGHT_GRAY);
        JLabel placeholder = new JLabel("Open a .gtable file via File > Open .gtable", SwingConstants.CENTER);
        placeholder.setForeground(Color.DARK_GRAY);
        chartHolder.add(placeholder, BorderLayout.CENTER);
        add(chartHolder, BorderLayout.CENTER);

        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT));

        polarToggle = new JToggleButton("Polar", true);
        polarToggle.addActionListener(e -> {
            polarMode = polarToggle.isSelected();
            polarToggle.setText(polarMode ? "Polar" : "Cartesian");
            if (table != null) refreshChart();
        });
        controlBar.add(polarToggle);

        String angleAxisName = cutType == CutType.H_PLANE ? "Elevation:" : "Azimuth:";
        controlBar.add(new JLabel(angleAxisName));

        angleSlider = new JSlider(0, 0, 0);
        angleSlider.setEnabled(false);
        angleSlider.addChangeListener(e -> {
            angleIdx = angleSlider.getValue();
            updateAngleValueLabel();
            if (table != null) refreshChart();
        });
        controlBar.add(angleSlider);

        angleValueLabel = new JLabel("—");
        controlBar.add(angleValueLabel);

        add(controlBar, BorderLayout.SOUTH);
    }

    public void setTable(GainTable table, int freqIdx) {
        this.table   = table;
        this.freqIdx = freqIdx;
        this.angleIdx = 0;

        int count = cutType == CutType.H_PLANE ? table.elevationCount() : table.azimuthCount();
        angleSlider.setEnabled(count > 1);
        angleSlider.setMinimum(0);
        angleSlider.setMaximum(Math.max(0, count - 1));
        angleSlider.setValue(0);

        updateAngleValueLabel();
        refreshChart();
    }

    public void setFrequencyIndex(int freqIdx) {
        this.freqIdx = freqIdx;
        if (table != null) refreshChart();
    }

    public void setPolarMode(boolean polar) {
        this.polarMode = polar;
        polarToggle.setSelected(polar);
        polarToggle.setText(polar ? "Polar" : "Cartesian");
        if (table != null) refreshChart();
    }

    private void updateAngleValueLabel() {
        if (table == null) {
            angleValueLabel.setText("—");
            return;
        }
        if (cutType == CutType.H_PLANE) {
            angleValueLabel.setText(String.format("%.1f°", table.elevationsDeg()[angleIdx]));
        } else {
            int A = table.azimuthCount();
            double azFwd  = table.azimuthsDeg()[angleIdx];
            double azRear = table.azimuthsDeg()[(angleIdx + A / 2) % A];
            angleValueLabel.setText(String.format("%.1f° / %.1f°", azFwd, azRear));
        }
    }

    private void refreshChart() {
        peakAnnotation = null;
        JFreeChart chart = polarMode ? buildPolarChart() : buildCartesianChart();
        if (chartPanel == null) {
            chartPanel = new ChartPanel(chart) {
                @Override
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    String ann = peakAnnotation;
                    if (ann != null) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
                        g2.setColor(Color.BLACK);
                        g2.drawString(ann, 8, 55);
                        g2.dispose();
                    }
                }
            };
            chartHolder.removeAll();
            chartHolder.add(chartPanel, BorderLayout.CENTER);
            chartHolder.revalidate();
            chartHolder.repaint();
        } else {
            chartPanel.setChart(chart);
        }
    }

    private double[] extractGains() {
        int A = table.azimuthCount();
        int E = table.elevationCount();
        short[] raw = table.gainsCentiDb();

        if (cutType == CutType.H_PLANE) {
            double[] gains = new double[A];
            for (int a = 0; a < A; a++) {
                short v = raw[freqIdx * A * E + a * E + angleIdx];
                gains[a] = (v == GainTable.SENTINEL_CENTI_DB) ? Double.NaN : v / 100.0;
            }
            return gains;
        } else {
            double[] gains = new double[E];
            for (int e = 0; e < E; e++) {
                short v = raw[freqIdx * A * E + angleIdx * E + e];
                gains[e] = (v == GainTable.SENTINEL_CENTI_DB) ? Double.NaN : v / 100.0;
            }
            return gains;
        }
    }

    private double peakGain(double[] gains) {
        double peak = Double.NEGATIVE_INFINITY;
        for (double g : gains) {
            if (!Double.isNaN(g) && g > peak) peak = g;
        }
        return Double.isInfinite(peak) ? 0.0 : peak;
    }

    private JFreeChart buildPolarChart() {
        double[] gains = extractGains();
        double peak    = peakGain(gains);

        // V-plane: also extract the rear (opposite-azimuth) gains so the full
        // semicircle is shown and the peak includes both lobes
        double[] rearGains = null;
        if (cutType == CutType.V_PLANE) {
            int A = table.azimuthCount(), E = table.elevationCount();
            int rearIdx = (angleIdx + A / 2) % A;
            short[] raw = table.gainsCentiDb();
            rearGains = new double[E];
            for (int e = 0; e < E; e++) {
                short v = raw[freqIdx * A * E + rearIdx * E + e];
                rearGains[e] = (v == GainTable.SENTINEL_CENTI_DB) ? Double.NaN : v / 100.0;
            }
            double rearPeak = peakGain(rearGains);
            if (rearPeak > peak) peak = rearPeak;
        }

        // Snap to 10 dB grid so rings land on round values
        double upper   = Math.ceil(peak / 10.0) * 10.0;
        double lower   = Math.max(-100.0, upper - 40.0);

        // autoSort=false: insertion order controls the drawn line direction
        XYSeries series = new XYSeries("Gain", false);

        if (cutType == CutType.H_PLANE) {
            double[] azimuths = table.azimuthsDeg();
            for (int a = 0; a < azimuths.length; a++) {
                if (!Double.isNaN(gains[a])) series.add(azimuths[a], gains[a]);
            }
            // close the loop
            if (!series.isEmpty()) {
                double firstAngle = (double) series.getX(0);
                double firstGain  = (double) series.getY(0);
                series.add(firstAngle + 360.0, firstGain);
            }
        } else {
            double[] elevations = table.elevationsDeg();
            // Right half (0°→90°): forward azimuth, radial line from inner ring to arc
            if (elevations.length > 0) series.add(elevations[0], lower);
            for (int e = 0; e < elevations.length; e++) {
                if (!Double.isNaN(gains[e])) series.add(elevations[e], gains[e]);
            }
            // Left half (90°→180°): rear azimuth plotted at angles 180°−el,
            // sweeping from top back down to the inner ring on the left
            for (int e = elevations.length - 1; e >= 0; e--) {
                if (rearGains != null && !Double.isNaN(rearGains[e]))
                    series.add(180.0 - elevations[e], rearGains[e]);
            }
            if (elevations.length > 0) series.add(180.0 - elevations[0], lower);
        }

        // --- Peak indicator: find forward-gains peak, set annotation, build line + dot ---
        int    peakIdx = 0;
        double peakVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < gains.length; i++) {
            if (!Double.isNaN(gains[i]) && gains[i] > peakVal) { peakVal = gains[i]; peakIdx = i; }
        }
        if (Double.isInfinite(peakVal)) { peakVal = lower; peakIdx = 0; }
        double peakAngle = (cutType == CutType.H_PLANE)
                ? table.azimuthsDeg()[peakIdx]
                : table.elevationsDeg()[peakIdx];
        peakAnnotation = (cutType == CutType.H_PLANE)
                ? String.format("Peak: %.1f dBi   Az: %.1f°", peakVal, peakAngle)
                : String.format("Peak: %.1f dBi   El: %.1f°", peakVal, peakAngle);

        // Radial line from chart centre (lower bound) to the peak point
        XYSeries peakLine = new XYSeries("PeakLine", false);
        peakLine.add(peakAngle, lower);
        peakLine.add(peakAngle, peakVal);

        // Single dot at the peak point
        XYSeries peakDot = new XYSeries("PeakDot", false);
        peakDot.add(peakAngle, peakVal);

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        dataset.addSeries(peakLine);   // series index 1
        dataset.addSeries(peakDot);    // series index 2
        JFreeChart chart = ChartFactory.createPolarChart(titleLabel.getText(), dataset, false, false, false);

        PolarPlot plot = (PolarPlot) chart.getPlot();
        if (cutType == CutType.H_PLANE) {
            // Compass: theta = angle + offset, y = centerY + sin(theta)*r → offset=-90 puts 0° at top
            plot.setAngleOffset(-90.0);
            plot.setCounterClockwise(false);
        } else {
            // Elevation: CCW negates angle → theta = -angle + 0; 0°=East, 90°=top
            plot.setAngleOffset(0.0);
            plot.setCounterClockwise(true);
        }

        NumberAxis radialAxis = (NumberAxis) plot.getAxis();
        radialAxis.setLowerBound(lower);
        radialAxis.setUpperBound(upper);
        radialAxis.setTickUnit(new NumberTickUnit(10.0));

        plot.setBackgroundPaint(Color.WHITE);
        plot.setAngleGridlinePaint(Color.BLACK);
        plot.setRadiusGridlinePaint(Color.BLACK);
        plot.setOutlinePaint(Color.BLACK);
        chart.setBackgroundPaint(Color.WHITE);

        // Series 0: main gain curve
        DefaultPolarItemRenderer mainRenderer = new DefaultPolarItemRenderer();
        mainRenderer.setShapesVisible(false);
        mainRenderer.setConnectFirstAndLastPoint(false);
        plot.setRenderer(0, mainRenderer);

        // Series 1: peak indicator line (centre → peak)
        DefaultPolarItemRenderer lineRenderer = new DefaultPolarItemRenderer();
        lineRenderer.setShapesVisible(false);
        lineRenderer.setConnectFirstAndLastPoint(false);
        lineRenderer.setDefaultPaint(Color.RED);
        lineRenderer.setDefaultStroke(new BasicStroke(1.5f));
        plot.setRenderer(1, lineRenderer);

        // Series 2: dot at the peak point
        DefaultPolarItemRenderer dotRenderer = new DefaultPolarItemRenderer();
        dotRenderer.setShapesVisible(true);
        dotRenderer.setConnectFirstAndLastPoint(false);
        dotRenderer.setDefaultPaint(Color.RED);
        dotRenderer.setDefaultShape(new Ellipse2D.Double(-5, -5, 10, 10));
        plot.setRenderer(2, dotRenderer);

        return chart;
    }

    private JFreeChart buildCartesianChart() {
        double[] gains = extractGains();
        double peak    = peakGain(gains);

        XYSeries series = new XYSeries("Gain");

        if (cutType == CutType.H_PLANE) {
            double[] azimuths = table.azimuthsDeg();
            for (int a = 0; a < azimuths.length; a++) {
                if (!Double.isNaN(gains[a])) series.add(azimuths[a], gains[a]);
            }
        } else {
            double[] elevations = table.elevationsDeg();
            for (int e = 0; e < elevations.length; e++) {
                if (!Double.isNaN(gains[e])) series.add(elevations[e], gains[e]);
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        String xAxisLabel = cutType == CutType.H_PLANE ? "Azimuth (°)" : "Elevation (°)";
        JFreeChart chart = ChartFactory.createXYLineChart(
                titleLabel.getText(), xAxisLabel, "Gain (dBi)", dataset);

        XYPlot plot = chart.getXYPlot();
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setLowerBound(peak - 45.0);

        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setDefaultShapesVisible(false);

        return chart;
    }
}
