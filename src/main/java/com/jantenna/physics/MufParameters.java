package com.jantenna.physics;

import java.util.Arrays;
import java.util.Objects;

/**
 * A record to encapsulate the diverse set of parameters required for the
 * High-Frequency MUF (Maximum Usable Frequency) and antenna gain calculations.
 * This replaces the legacy FORTRAN parameter array with a type-safe,
 * immutable data structure.
 *
 * @param kop The antenna type code (1-17).
 * @param kas The calculation mode flag.
 * @param toaz The takeoff azimuth in degrees.
 * @param ysig The ground conductivity.
 * @param yeps The ground dielectric constant.
 * @param ynd The primary antenna dimension or angle (e.g., tilt angle, # of bays).
 * @param ynl The secondary antenna dimension (e.g., leg length, height).
 * @param ynh The tertiary antenna dimension (e.g., feed height).
 * @param tex An array for additional, miscellaneous parameters.
 * @param delta The takeoff angle in radians.
 * @param frequencyMHz The frequency in MHz.
 */
public record MufParameters(
        int kop,
        int kas,
        double toaz,
        double ysig,
        double yeps,
        double ynd,
        double ynl,
        double ynh,
        double[] tex,
        double delta,
        double frequencyMHz
) {
    /**
     * Antenna leg length (secondary dimension).
     * Convenience accessor for ynl.
     */
    public double anl() {
        return ynl;
    }

    /**
     * Antenna feed height (tertiary dimension).
     * Convenience accessor for ynh.
     */
    public double anh() {
        return ynh;
    }

    /**
     * Extra antenna parameters array.
     * Convenience accessor for tex.
     */
    public double[] aex() {
        return tex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MufParameters that)) return false;
        return kop == that.kop
                && kas == that.kas
                && Double.compare(that.toaz, toaz) == 0
                && Double.compare(that.ysig, ysig) == 0
                && Double.compare(that.yeps, yeps) == 0
                && Double.compare(that.ynd, ynd) == 0
                && Double.compare(that.ynl, ynl) == 0
                && Double.compare(that.ynh, ynh) == 0
                && Double.compare(that.delta, delta) == 0
                && Double.compare(that.frequencyMHz, frequencyMHz) == 0
                && Arrays.equals(tex, that.tex);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(kop, kas, toaz, ysig, yeps, ynd, ynl, ynh, delta, frequencyMHz);
        result = 31 * result + Arrays.hashCode(tex);
        return result;
    }

    @Override
    public String toString() {
        return "MufParameters[kop=" + kop + ", kas=" + kas + ", toaz=" + toaz
                + ", ysig=" + ysig + ", yeps=" + yeps + ", ynd=" + ynd
                + ", ynl=" + ynl + ", ynh=" + ynh + ", tex=" + Arrays.toString(tex)
                + ", delta=" + delta + ", frequencyMHz=" + frequencyMHz + ']';
    }
}