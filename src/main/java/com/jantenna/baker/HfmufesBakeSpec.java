package com.jantenna.baker;

import com.jantenna.AntennaMetadata;

import com.jantenna.physics.MufParameters;

/**
 * Parameter spec for an analytical HFMUFES antenna bake per
 * {@code docs/antennas.md} §9.4 (Phase D — TD-110).  Carries the
 * subset of {@link MufParameters} fields that vary per antenna design
 * (geometry + ground), separated from the per-evaluation fields that
 * the bake harness fills in (frequency, elevation, off-azimuth).
 *
 * @param kop          antenna type code (1..17), matches voacapl HFMUFES KOP
 * @param description  human-readable label for the metadata sidecar
 * @param sigmaSm      ground conductivity S/m
 * @param epsilonR     ground relative permittivity
 * @param antTiltDeg   primary geometry parameter (KOP-specific — tilt angle,
 *                     leg length, or # of bays depending on KOP)
 * @param antLengthWl  secondary geometry parameter (often leg length in λ)
 * @param antHeightWl  tertiary geometry parameter (often feed height in λ)
 * @param tex          extra params (e.g. {@code [Z0, alpha, tau, n]} for LPDA);
 *                     may be empty for simpler KOPs
 * @param userGainDb   additional user-applied gain offset, applied AFTER the
 *                     analytical formula (mirrors voacapl's
 *                     {@code antgains += parm(1)} step at antcalc.for:291)
 */
public record HfmufesBakeSpec(
        int kop,
        String description,
        double sigmaSm,
        double epsilonR,
        double antTiltDeg,
        double antLengthWl,
        double antHeightWl,
        double[] tex,
        double userGainDb
) {

    /** Build the {@link MufParameters} skeleton for a particular (freq, az, el). */
    public MufParameters at(double frequencyMHz, double azimuthRad, double elevationRad) {
        return new MufParameters(
                kop,
                1,
                azimuthRad,
                sigmaSm,
                epsilonR,
                antTiltDeg,
                antLengthWl,
                antHeightWl,
                tex == null ? new double[0] : tex.clone(),
                elevationRad,
                frequencyMHz);
    }

    /** Canonical JSON form for {@code AntennaMetadata.Source.paramsJson}. */
    public String toCanonicalJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"kop\":").append(kop).append(',');
        sb.append("\"sigmaSm\":").append(sigmaSm).append(',');
        sb.append("\"epsilonR\":").append(epsilonR).append(',');
        sb.append("\"antTiltDeg\":").append(antTiltDeg).append(',');
        sb.append("\"antLengthWl\":").append(antLengthWl).append(',');
        sb.append("\"antHeightWl\":").append(antHeightWl).append(',');
        sb.append("\"userGainDb\":").append(userGainDb).append(',');
        sb.append("\"tex\":[");
        if (tex != null) {
            for (int i = 0; i < tex.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(tex[i]);
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}
