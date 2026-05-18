package com.jantenna.physics;

import com.jantenna.math.MathConstants;
import com.jantenna.math.SpecialFunctions;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/**
 * KOP 6: Curtain array with perfectly conducting screen antenna gain calculator.
 * Port of FORTRAN MUFESGAN.FOR lines 498-687, plus subroutines ZMUT (zmut.for),
 * COLL (coll.for), and ECH (ech.for).
 */
public final class CurtainArrayGainCalculator implements AntennaGainCalculator {

    private final SpecialFunctions specialFunctions;

    public CurtainArrayGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 6;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        double beta = params.toaz();

        if (beta < 90.0 || beta > 270.0) {
            return 10.0 * FastMath.log10(FastMath.max(0.05, 0.001));
        }

        double el1 = FastMath.abs(params.ynl() / wave);
        double[] tex = params.tex();
        double phi = params.ynd();
        double x = FastMath.abs(params.ynh() / wave);

        double eil = 0.5 * el1;

        int nb = (int) FastMath.abs(phi);
        int nbb = (int) (FastMath.abs(FastMath.abs(phi) * 100.0 - nb * 100.0) + 0.5);
        double cbay = (nbb == 0) ? 1.0 : -1.0;
        if (nbb == 0) nbb = 1;

        int ne = (int) FastMath.abs(tex[0]);
        int nee = (int) (FastMath.abs(FastMath.abs(tex[0]) * 100.0 - ne * 100.0) + 0.5);
        double cele = (nee == 0) ? 1.0 : -1.0;
        if (nee == 0) nee = 1;

        double dy = FastMath.abs(tex[1]) / wave;
        double dz = FastMath.abs(tex[2]) / wave;
        double dx = FastMath.abs(tex[3]) / wave;

        double thetaz = 90.0;
        double deltap = 0.0;
        double reTA = beta * MathConstants.PI / 180.0;
        double sb = FastMath.sin(reTA);
        double cb = FastMath.cos(reTA);

        Geometry g = new Geometry(dy, dz, dx, x, eil);
        Dims d = Dims.of(nb, ne);

        Complex rzz = zmut(0.01767766952 * eil, eil, 1);

        double rin;
        int kode;

        if (rzz.getReal() >= 3631.53) {
            kode = -1;
            rin = (double) nb * ne * 3631.53;
        } else {
            kode = 1;
            ImpedanceTables tables = buildImpedanceTables(d, g, rzz);
            Complex[] zsums = sumImpedances(d, tables);
            Complex zsum1 = zsums[0];
            Complex zsum2 = zsums[1];
            Complex zsum3 = zsums[2];
            Complex zsum4 = zsums[3];

            Complex sqrd = dif.sqrt();
            Complex rhcp = Complex.ONE.subtract(sqrd).divide(Complex.ONE.add(sqrd));
            rin = zsum1.subtract(zsum2).add(rhcp.multiply(zsum3.subtract(zsum4))).getReal();
        }

        double stheta = t;
        double cpsi = stheta * sb;

        double cthetaz = FastMath.cos(thetaz * MathConstants.PI / 180.0);
        double sbz = FastMath.sin(deltap * MathConstants.PI / 180.0);

        Complex azv = Complex.ZERO;
        Complex azh = Complex.ZERO;

        double factor = cele;
        for (int m = 1; m <= ne; m++) {
            double em = m;
            double zm = x + (em - 1.0) * dz;
            if ((m % nee) == 1 || nee == 1) {
                factor = factor * cele;
            }
            double tt = MathConstants.PI2 * zm * (q - cthetaz);
            Complex zt = new Complex(FastMath.cos(tt), FastMath.sin(tt));
            tt = 2.0 * MathConstants.PI2 * zm * q;
            Complex ztr = new Complex(FastMath.cos(tt), -FastMath.sin(tt));
            azv = azv.add(zt.multiply(Complex.ONE.subtract(qper.multiply(ztr))).multiply(factor));
            azh = azh.add(zt.multiply(Complex.ONE.add(qpar.multiply(ztr))).multiply(factor));
        }

        Complex af = Complex.ONE;
        if (nb > 1) {
            af = Complex.ZERO;
            double factorBay = cbay;
            for (int n2 = 1; n2 <= nb; n2++) {
                double en = n2;
                if ((n2 % nbb) == 1 || nbb == 1) {
                    factorBay = factorBay * cbay;
                }
                double tt = MathConstants.PI2 * dy * (en - 1.0);
                double ts = cpsi - stheta * sbz;
                Complex zt = new Complex(FastMath.cos(tt * ts), FastMath.sin(tt * ts));
                af = af.add(zt.multiply(factorBay));
            }
        }

        double ttScreen = FastMath.sin(MathConstants.PI2 * dx * stheta * cb);

        double spsi2 = 1.0 - cpsi * cpsi;
        if (spsi2 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double pi2Eil = MathConstants.PI2 * eil;
        double ttElem = (FastMath.cos(pi2Eil * cpsi) - FastMath.cos(pi2Eil)) / spsi2;

        double azvMag2 = azv.getReal() * azv.getReal() + azv.getImaginary() * azv.getImaginary();
        double azhMag2 = azh.getReal() * azh.getReal() + azh.getImaginary() * azh.getImaginary();
        double zt2 = (sb * sb * q * q * azvMag2 + cb * cb * azhMag2) * ttScreen * ttScreen;

        double afMag2 = af.getReal() * af.getReal() + af.getImaginary() * af.getImaginary();
        double train = zt2 * afMag2 * ttElem * ttElem / rin;

        double rain;
        if (kode < 0) {
            Complex ctu = new Complex(-0.0419290, +0.0461374);
            Complex ctd = new Complex(-0.0184019, +0.0612938);
            double sinPi2Eil = FastMath.sin(pi2Eil);
            double cosPi2Eil = FastMath.cos(pi2Eil);
            double cospiEil = FastMath.cos(MathConstants.PI * eil);
            Complex curnt = new Complex(sinPi2Eil, 0.0)
                    .add(ctu.multiply(1.0 - cosPi2Eil))
                    .add(ctd.multiply(1.0 - cospiEil));
            double curntMag2 = curnt.getReal() * curnt.getReal()
                    + curnt.getImaginary() * curnt.getImaginary();
            rain = 480.0 * train / curntMag2;
        } else {
            double sinPi2Eil = FastMath.sin(pi2Eil);
            rain = 480.0 * (1.0 / (sinPi2Eil * sinPi2Eil)) * train;
        }

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001));
    }

    // -------------------------------------------------------------------------
    // Parameter bundles - keep method arities under the S107 threshold.
    // -------------------------------------------------------------------------

    /** Dimensions of the curtain array (bay count, element count, derived loop bounds). */
    private record Dims(int nb, int ne, int ijend, int ipjend, int mnend) {
        static Dims of(int nb, int ne) {
            return new Dims(nb, ne, ne - 1, 2 * ne, nb - 1);
        }
    }

    /** Wavelength-normalised geometry of the curtain array. */
    private record Geometry(double dy, double dz, double dx, double x, double eil) {
        double ts2() { return 4.0 * dx * dx; }
    }

    /** Bay-pair impedance tables (rows indexed 1..mnend; output of {@link #computeBayArrays}). */
    private record BayTables(
            Complex[] rze, Complex[] rpze,
            Complex[][] rdze, Complex[][] rtze,
            Complex[][] r, Complex[][] rp,
            Complex[][] rdz, Complex[][] rt
    ) { }

    /** Full impedance-table set consumed by {@link #sumImpedances}. */
    private record ImpedanceTables(
            Complex rzz, Complex rpzz,
            Complex[] rdzz, Complex[] rtzz,
            Complex[] rzb, Complex[] rpzb,
            Complex[] rdzb, Complex[] rtzb,
            BayTables bay
    ) { }

    // -------------------------------------------------------------------------
    // Impedance-table builders
    // -------------------------------------------------------------------------

    private ImpedanceTables buildImpedanceTables(Dims d, Geometry g, Complex rzz) {
        Complex rpzz = zmut(2.0 * g.dx(), g.eil(), 1);

        Complex[] rdzz = new Complex[d.ne()];
        Complex[] rtzz = new Complex[d.ne()];
        computeRdzzRtzz(d, g, rdzz, rtzz);

        Complex[] rzb = new Complex[d.ijend()];
        Complex[] rpzb = new Complex[d.ijend()];
        computeRzbArrays(d, g, rzb, rpzb);

        Complex[] rdzb = new Complex[d.ipjend() + 1];
        Complex[] rtzb = new Complex[d.ipjend() + 1];
        computeRdzbRtzb(d, g, rdzb, rtzb);

        BayTables bay = computeBayArrays(d, g);

        return new ImpedanceTables(rzz, rpzz, rdzz, rtzz, rzb, rpzb, rdzb, rtzb, bay);
    }

    /** Per-element self/image arrays (slots 0..ne-1). */
    private void computeRdzzRtzz(Dims d, Geometry g, Complex[] rdzz, Complex[] rtzz) {
        for (int i = 0; i < d.ne(); i++) {
            double tt = i * g.dz() + g.x();
            rdzz[i] = zmut(2.0 * tt, g.eil(), 1);
            rtzz[i] = zmut(2.0 * FastMath.sqrt(g.dx() * g.dx() + tt * tt), g.eil(), 1);
        }
    }

    /** Collinear mutual-impedance arrays for same-bay different-element pairs (slots 0..ijend-1). */
    private void computeRzbArrays(Dims d, Geometry g, Complex[] rzb, Complex[] rpzb) {
        double ts2 = g.ts2();
        for (int ij = 0; ij < d.ijend(); ij++) {
            double cij = ij + 1;
            rzb[ij] = coll(cij * g.dz(), g.eil(), 1);
            rpzb[ij] = coll(FastMath.sqrt(ts2 + cij * cij * g.dz() * g.dz()), g.eil(), 1);
        }
    }

    /** Image-collinear arrays indexed by IPJ=2..IPJEND (slots 0 and 1 left unused). */
    private void computeRdzbRtzb(Dims d, Geometry g, Complex[] rdzb, Complex[] rtzb) {
        double ts2 = g.ts2();
        for (int ipj = 2; ipj <= d.ipjend(); ipj++) {
            double tt = (ipj - 2.0) * g.dz() + 2.0 * g.x();
            rdzb[ipj] = zmut(tt, g.eil(), 1);
            rtzb[ipj] = zmut(FastMath.sqrt(ts2 + tt * tt), g.eil(), 1);
        }
    }

    /** Bay-pair impedance tables for MN=1..mnend. */
    private BayTables computeBayArrays(Dims d, Geometry g) {
        int ne = d.ne();
        int ijend = d.ijend();
        int ipjend = d.ipjend();
        int mnend = d.mnend();
        double dz = g.dz();
        double dx = g.dx();
        double x = g.x();
        double eil = g.eil();
        double ts2 = g.ts2();

        Complex[] rze  = new Complex[mnend + 1];
        Complex[] rpze = new Complex[mnend + 1];
        Complex[][] rdze  = new Complex[mnend + 1][ne + 1];
        Complex[][] rtze  = new Complex[mnend + 1][ne + 1];
        Complex[][] rArr  = new Complex[mnend + 1][ijend + 1];
        Complex[][] rpArr = new Complex[mnend + 1][ijend + 1];
        Complex[][] rdz   = new Complex[mnend + 1][ipjend + 1];
        Complex[][] rt    = new Complex[mnend + 1][ipjend + 1];

        for (int mn = 1; mn <= mnend; mn++) {
            double hij = mn * g.dy() - eil;
            rze[mn]  = coll(hij, eil, 1);
            rpze[mn] = ech(2.0 * dx, hij, eil, 1);

            for (int i = 1; i <= ne; i++) {
                double tt = 2.0 * ((i - 1.0) * dz + x);
                rdze[mn][i] = ech(tt, hij, eil, 1);
                rtze[mn][i] = ech(FastMath.sqrt(ts2 + tt * tt), hij, eil, 1);
            }

            for (int ij = 1; ij <= ijend; ij++) {
                double cij = ij;
                rArr[mn][ij]  = ech(cij * dz, hij, eil, 1);
                rpArr[mn][ij] = ech(FastMath.sqrt(ts2 + cij * cij * dz * dz), hij, eil, 1);
            }

            for (int ipj = 2; ipj <= ipjend; ipj++) {
                double tt = (ipj - 2.0) * dz + 2.0 * x;
                rdz[mn][ipj] = ech(tt, hij, eil, 1);
                rt[mn][ipj]  = ech(FastMath.sqrt(ts2 + tt * tt), hij, eil, 1);
            }
        }

        return new BayTables(rze, rpze, rdze, rtze, rArr, rpArr, rdz, rt);
    }

    // -------------------------------------------------------------------------
    // Impedance accumulation
    // -------------------------------------------------------------------------

    /** Pair indices (m, i) against (n2, j) - used to disambiguate the four impedance regimes. */
    private record PairIndices(int m, int i, int n2, int j) { }

    /**
     * Selects the four impedance terms for the element pair (m,i) vs (n2,j).
     * Returns {@code Complex[4]} = { zterm1, zterm2, zterm3, zterm4 }.
     */
    private Complex[] selectImpedanceTerm(PairIndices p, ImpedanceTables t) {
        int m = p.m();
        int i = p.i();
        int n2 = p.n2();
        int j = p.j();
        BayTables bay = t.bay();
        Complex zterm1;
        Complex zterm2;
        Complex zterm3;
        Complex zterm4;
        if (m == n2 && i == j) {
            zterm1 = t.rzz();
            zterm2 = t.rpzz();
            zterm3 = t.rdzz()[i - 1];
            zterm4 = t.rtzz()[i - 1];
        } else if (m == n2) {
            int ij2 = FastMath.abs(i - j);
            zterm1 = t.rzb()[ij2 - 1];
            zterm2 = t.rpzb()[ij2 - 1];
            zterm3 = t.rdzb()[i + j];
            zterm4 = t.rtzb()[i + j];
        } else if (i == j) {
            int mn2 = FastMath.abs(m - n2);
            zterm1 = bay.rze()[mn2];
            zterm2 = bay.rpze()[mn2];
            zterm3 = bay.rdze()[mn2][i];
            zterm4 = bay.rtze()[mn2][i];
        } else {
            int mn2 = FastMath.abs(m - n2);
            int ij2 = FastMath.abs(i - j);
            zterm1 = bay.r()[mn2][ij2];
            zterm2 = bay.rp()[mn2][ij2];
            zterm3 = bay.rdz()[mn2][i + j];
            zterm4 = bay.rt()[mn2][i + j];
        }
        return new Complex[]{zterm1, zterm2, zterm3, zterm4};
    }

    /**
     * Accumulates ZSUM1..ZSUM4 over all (m,i,n2,j) element pairs.
     * Returns {@code Complex[4]} = { zsum1, zsum2, zsum3, zsum4 }.
     */
    private Complex[] sumImpedances(Dims d, ImpedanceTables t) {
        Complex zsum1 = Complex.ZERO;
        Complex zsum2 = Complex.ZERO;
        Complex zsum3 = Complex.ZERO;
        Complex zsum4 = Complex.ZERO;

        for (int m = 1; m <= d.nb(); m++) {
            for (int i = 1; i <= d.ne(); i++) {
                for (int n2 = 1; n2 <= d.nb(); n2++) {
                    for (int j = 1; j <= d.ne(); j++) {
                        Complex[] terms = selectImpedanceTerm(new PairIndices(m, i, n2, j), t);
                        zsum1 = zsum1.add(terms[0]);
                        zsum2 = zsum2.add(terms[1]);
                        zsum3 = zsum3.add(terms[2]);
                        zsum4 = zsum4.add(terms[3]);
                    }
                }
            }
        }

        return new Complex[]{zsum1, zsum2, zsum3, zsum4};
    }

    // -------------------------------------------------------------------------
    // FORTRAN subroutine ports
    // -------------------------------------------------------------------------

    /**
     * Port of ZMUT subroutine (zmut.for).
     * Calculates self- or mutual impedance between parallel dipoles of equal length.
     */
    private Complex zmut(double dij, double eil, int kode) {
        double d2 = dij * dij;
        double el2 = eil * eil;
        double tt = FastMath.sqrt(d2 + 4.0 * el2);
        double uz = MathConstants.PI2 * (tt - 2.0 * eil);
        double vz = MathConstants.PI2 * (tt + 2.0 * eil);
        double uzp = MathConstants.PI2 * dij;
        tt = FastMath.sqrt(d2 + el2);
        double u1 = MathConstants.PI2 * (tt - eil);
        double v1 = MathConstants.PI2 * (tt + eil);
        double w1 = 2.0 * MathConstants.PI2 * eil;
        double cw1 = FastMath.cos(w1);
        double sw1 = FastMath.sin(w1);

        Complex csu1 = csz1(u1);
        Complex csv1 = csz1(v1);
        Complex csuzp = csz1(uzp);

        Complex zsum = csz1(uz).subtract(csu1.multiply(2.0))
                .multiply(new Complex(cw1, -sw1))
                .add(csz1(vz).subtract(csv1.multiply(2.0))
                        .multiply(new Complex(cw1, sw1)))
                .add(csuzp.subtract(csu1).subtract(csv1).multiply(2.0))
                .add(csuzp.multiply(2.0 * (cw1 + 1.0)));

        if (kode > 0) {
            return zsum.multiply(60.0 / (1.0 - cw1));
        } else {
            return zsum.multiply(30.0);
        }
    }

    /**
     * Port of COLL subroutine (coll.for).
     * Calculates mutual impedance between collinear dipoles of equal length.
     */
    private Complex coll(double hij, double eil, int kode) {
        double uz = MathConstants.PI2 * 2.0 * (hij - eil);
        double u1 = MathConstants.PI2 * 2.0 * hij;
        double v2 = MathConstants.PI2 * 2.0 * (hij + 2.0 * eil);
        double u3 = MathConstants.PI2 * 2.0 * (hij + eil);
        double v4 = MathConstants.PI2 * 2.0 * (hij + 3.0 * eil);

        Complex csu1 = csz1(u1);
        Complex csv2 = csz1(v2);
        Complex csu3 = csz1(u3);

        double hlog = FastMath.log(hij / (hij + eil));
        double h2log = FastMath.log((hij + 2.0 * eil) / (hij + eil));

        double tVal = MathConstants.PI2 * (hij - eil);
        Complex ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        Complex sum = ze.multiply(csz1(uz).subtract(csu1))
                .add(ze.conjugate().multiply(FastMath.log(hij / (hij - eil))));

        tVal = MathConstants.PI2 * (hij + eil);
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        sum = sum.add(ze.multiply(csu3.subtract(csv2)))
                .add(ze.conjugate().multiply(h2log));
        sum = sum.add(ze.multiply(csu3.subtract(csu1)))
                .add(ze.conjugate().multiply(hlog));

        tVal = MathConstants.PI2 * (hij + 3.0 * eil);
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        sum = sum.add(ze.multiply(csz1(v4).subtract(csv2)))
                .add(ze.conjugate().multiply(FastMath.log((hij + 2.0 * eil) / (hij + 3.0 * eil))));

        double ts = 2.0 * FastMath.cos(MathConstants.PI2 * eil);
        tVal = MathConstants.PI2 * hij;
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        sum = sum.add(ze.multiply(csu3.subtract(csu1)).multiply(ts))
                .add(ze.conjugate().multiply(ts * hlog));

        tVal = MathConstants.PI2 * (hij + 2.0 * eil);
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        sum = sum.add(ze.multiply(csu3.subtract(csv2)).multiply(ts))
                .add(ze.conjugate().multiply(ts * h2log));

        if (kode > 0) {
            return sum.multiply(30.0 / (1.0 - FastMath.cos(2.0 * MathConstants.PI2 * eil)));
        } else {
            return sum.multiply(15.0);
        }
    }

    /**
     * Port of ECH subroutine (ech.for).
     * Calculates mutual impedance between dipole elements in echelon of equal length.
     */
    private Complex ech(double dij, double hij, double eil, int kode) {
        double d2 = dij * dij;
        double hml = hij - eil;
        double hpl = hij + eil;

        double tt = FastMath.sqrt(d2 + hml * hml);
        double uz = MathConstants.PI2 * (tt + hml);
        double vz = MathConstants.PI2 * (tt - hml);

        tt = FastMath.sqrt(d2 + hpl * hpl);
        double uzp = MathConstants.PI2 * (tt - hpl);
        double vzp = MathConstants.PI2 * (tt + hpl);

        double ts = hij;
        tt = FastMath.sqrt(d2 + ts * ts);
        double u1 = MathConstants.PI2 * (tt + ts);
        double v1 = MathConstants.PI2 * (tt - ts);

        ts = hij + 2.0 * eil;
        tt = FastMath.sqrt(d2 + ts * ts);
        double u2 = MathConstants.PI2 * (tt - ts);
        double v2 = MathConstants.PI2 * (tt + ts);

        ts = hij + 3.0 * eil;
        tt = FastMath.sqrt(d2 + ts * ts);
        double u4 = MathConstants.PI2 * (tt - ts);
        double v4 = MathConstants.PI2 * (tt + ts);

        Complex csu1 = csz1(u1);
        Complex csv1 = csz1(v1);
        Complex csu2 = csz1(u2);
        Complex csv2 = csz1(v2);
        Complex csuzp = csz1(uzp);
        Complex csvzp = csz1(vzp);

        Complex zsum = Complex.ZERO;

        double tVal = MathConstants.PI2 * (eil - hij);
        Complex ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        zsum = zsum.add(ze.conjugate().multiply(csz1(uz).subtract(csu1)))
                   .add(ze.multiply(csz1(vz).subtract(csv1)));

        tVal = MathConstants.PI2 * (eil + hij);
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        zsum = zsum.add(ze.conjugate().multiply(csuzp.subtract(csu2)))
                   .add(ze.multiply(csvzp.subtract(csv2)));

        tVal = MathConstants.PI2 * (-eil - hij);
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        zsum = zsum.add(ze.conjugate().multiply(csvzp.subtract(csu1)))
                   .add(ze.multiply(csuzp.subtract(csv1)));

        tVal = MathConstants.PI2 * (3.0 * eil + hij);
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        zsum = zsum.add(ze.conjugate().multiply(csz1(u4).subtract(csu2)))
                   .add(ze.multiply(csz1(v4).subtract(csv2)));

        double tsVal = 2.0 * FastMath.cos(MathConstants.PI2 * eil);

        tVal = MathConstants.PI2 * hij;
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        zsum = zsum.add(ze.conjugate().multiply(csuzp.subtract(csv1)).multiply(tsVal))
                   .add(ze.multiply(csvzp.subtract(csu1)).multiply(tsVal));

        tVal = MathConstants.PI2 * (2.0 * eil + hij);
        ze = new Complex(FastMath.cos(tVal), FastMath.sin(tVal));
        zsum = zsum.add(ze.conjugate().multiply(csuzp.subtract(csu2)).multiply(tsVal))
                   .add(ze.multiply(csvzp.subtract(csv2)).multiply(tsVal));

        if (kode > 0) {
            return zsum.multiply(30.0 / (1.0 - FastMath.cos(2.0 * MathConstants.PI2 * eil)));
        } else {
            return zsum.multiply(15.0);
        }
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) - i*Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
