package com.jantenna.physics;

import com.jantenna.math.SpecialFunctions;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
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

        // IF (BETA < 90 OR BETA > 270) -> RAIN=0.05, go to 615
        if (beta < 90.0 || beta > 270.0) {
            return 10.0 * FastMath.log10(FastMath.max(0.05, 0.001));
        }

        double el1 = FastMath.abs(params.ynl() / wave);
        double[] tex = params.tex();
        double phi = params.ynd();
        double x = FastMath.abs(params.ynh() / wave);

        double eil = 0.5 * el1;

        // Number of bays
        int nb = (int) FastMath.abs(phi);
        int nbb = (int) (FastMath.abs(FastMath.abs(phi) * 100.0 - nb * 100.0) + 0.5);
        double cbay = (nbb == 0) ? 1.0 : -1.0;
        if (nbb == 0) nbb = 1;

        // Number of elements per bay
        int ne = (int) FastMath.abs(tex[0]);
        int nee = (int) (FastMath.abs(FastMath.abs(tex[0]) * 100.0 - ne * 100.0) + 0.5);
        double cele = (nee == 0) ? 1.0 : -1.0;
        if (nee == 0) nee = 1;

        double dy = FastMath.abs(tex[1]) / wave;    // bay horizontal spacing (wavelengths)
        double dz = FastMath.abs(tex[2]) / wave;    // element vertical spacing
        double dx = FastMath.abs(tex[3]) / wave;    // screen distance

        double thetaz = 90.0;
        double deltap = 0.0;
        double reTA = beta * MathConstants.PI / 180.0;
        double sb = FastMath.sin(reTA);
        double cb = FastMath.cos(reTA);

        // === Compute impedance sums ===
        // Self-impedance: DIJ = 0.01767766952 * EIL, HIJ=EIL, KODE=1
        Complex rzz = zmut(0.01767766952 * eil, eil, 1);

        double rin;
        int kode;

        // IF Re(RZZ) >= 3631.53: full-wave case
        if (rzz.getReal() >= 3631.53) {
            rzz = new Complex(3631.53, -2356.47);
            kode = -1;
            rin = (double)(nb * ne) * 3631.53;
        } else {
            kode = 1;

            // DIJ = 2*DX, call ZMUT(RPZZ)
            Complex rpzz = zmut(2.0 * dx, eil, 1);

            double ts2 = (2.0 * dx) * (2.0 * dx);
            int ijend = ne - 1;
            int ipjend = 2 * ne;
            int mnend = nb - 1;

            // Per-element self/image arrays (0-indexed, ne entries)
            Complex[] rdzz = new Complex[ne];
            Complex[] rtzz = new Complex[ne];
            computeRdzzRtzz(ne, x, dz, dx, eil, rdzz, rtzz);

            // Collinear mutual-impedance arrays for same-bay different-element pairs
            Complex[] rzb = new Complex[ijend];
            Complex[] rpzb = new Complex[ijend];
            computeRzbArrays(ijend, dz, ts2, eil, rzb, rpzb);

            // Image-collinear arrays indexed by IPJ=2..IPJEND (slot 0 and 1 unused)
            Complex[] rdzb = new Complex[ipjend + 1];
            Complex[] rtzb = new Complex[ipjend + 1];
            computeRdzbRtzb(ipjend, x, dz, ts2, eil, rdzb, rtzb);

            // Bay-pair arrays (all indexed 1..mnend, inner arrays 1..ne or 1..ijend or 2..ipjend)
            Complex[] rze   = new Complex[mnend + 1];
            Complex[] rpze  = new Complex[mnend + 1];
            Complex[][] rdze  = new Complex[mnend + 1][ne + 1];
            Complex[][] rtze  = new Complex[mnend + 1][ne + 1];
            Complex[][] rArr  = new Complex[mnend + 1][ijend + 1];
            Complex[][] rpArr = new Complex[mnend + 1][ijend + 1];
            Complex[][] rdz   = new Complex[mnend + 1][ipjend + 1];
            Complex[][] rt    = new Complex[mnend + 1][ipjend + 1];
            computeBayArrays(mnend, ne, ijend, ipjend, dy, dz, dx, x, ts2, eil,
                    rze, rpze, rdze, rtze, rArr, rpArr, rdz, rt);

            // Sum all self- and mutual-impedances over all element pairs
            Complex[] zsums = sumImpedances(nb, ne, rzz, rpzz, rdzz, rtzz,
                    rzb, rpzb, rdzb, rtzb,
                    rze, rpze, rdze, rtze,
                    rArr, rpArr, rdz, rt);
            Complex zsum1 = zsums[0];
            Complex zsum2 = zsums[1];
            Complex zsum3 = zsums[2];
            Complex zsum4 = zsums[3];

            // RIN = Re(ZSUM1 - ZSUM2 + RHCP*(ZSUM3 - ZSUM4))
            // RHCP = (1 - SQRD) / (1 + SQRD), SQRD = sqrt(DIF)
            Complex sqrd = dif.sqrt();
            Complex rhcp = Complex.ONE.subtract(sqrd).divide(Complex.ONE.add(sqrd));
            rin = zsum1.subtract(zsum2).add(rhcp.multiply(zsum3.subtract(zsum4))).getReal();
        }

        // === Element arraying factor ===
        // label 355: STHETA = COS(DELTA) = T
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

        // Bay arraying factor
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

        // Real-image (screen) arraying factor: TT = sin(PI2 * DX * stheta * CB)
        double ttScreen = FastMath.sin(MathConstants.PI2 * dx * stheta * cb);

        // SPSI2 = 1 - CPSI^2
        double spsi2 = 1.0 - cpsi * cpsi;
        if (spsi2 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        // Element pattern factor: TT = (cos(PI2*EIL*CPSI) - cos(PI2*EIL)) / SPSI2
        double pi2Eil = MathConstants.PI2 * eil;
        double ttElem = (FastMath.cos(pi2Eil * cpsi) - FastMath.cos(pi2Eil)) / spsi2;

        // ZT = (SB^2*Q^2*AZV*conj(AZV) + CB^2*AZH*conj(AZH)) * TT^2
        double azvMag2 = azv.getReal() * azv.getReal() + azv.getImaginary() * azv.getImaginary();
        double azhMag2 = azh.getReal() * azh.getReal() + azh.getImaginary() * azh.getImaginary();
        double zt2 = (sb * sb * q * q * azvMag2 + cb * cb * azhMag2) * ttScreen * ttScreen;

        // TRAIN = ZT * AF * conj(AF) * TT^2 / RIN
        double afMag2 = af.getReal() * af.getReal() + af.getImaginary() * af.getImaginary();
        double train = zt2 * afMag2 * ttElem * ttElem / rin;

        double rain;
        if (kode < 0) {
            // Full-wave case — EQN.(4.114) P. 273
            // CTU = CMPLX(-0.0419290, +0.0461374); CTD = CMPLX(-0.0184019, +0.0612938)
            Complex ctu = new Complex(-0.0419290, +0.0461374);
            Complex ctd = new Complex(-0.0184019, +0.0612938);
            // CURNT = SIN(PI2*EIL) + CTU*(1-COS(PI2*EIL)) + CTD*(1-COS(PI*EIL))
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
            // Normal case
            double sinPi2Eil = FastMath.sin(pi2Eil);
            rain = 480.0 * (1.0 / (sinPi2Eil * sinPi2Eil)) * train;
        }

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001));
    }

    // -------------------------------------------------------------------------
    // Impedance-table builders
    // -------------------------------------------------------------------------

    /**
     * Fills RDZZ[0..ne-1] and RTZZ[0..ne-1].
     * RDZZ(I) = ZMUT(2*TT, EIL, 1) where TT = (I-1)*DZ + X
     * RTZZ(I) = ZMUT(2*sqrt(DX^2 + TT^2), EIL, 1)
     */
    private void computeRdzzRtzz(int ne, double x, double dz, double dx, double eil,
                                  Complex[] rdzz, Complex[] rtzz) {
        for (int i = 0; i < ne; i++) {
            double tt = i * dz + x;
            rdzz[i] = zmut(2.0 * tt, eil, 1);
            rtzz[i] = zmut(2.0 * FastMath.sqrt(dx * dx + tt * tt), eil, 1);
        }
    }

    /**
     * Fills RZB[0..ijend-1] and RPZB[0..ijend-1].
     * RZB(IJ)  = COLL(IJ*DZ, EIL, 1)
     * RPZB(IJ) = COLL(sqrt(TS2 + IJ^2*DZ^2), EIL, 1)
     */
    private void computeRzbArrays(int ijend, double dz, double ts2, double eil,
                                   Complex[] rzb, Complex[] rpzb) {
        for (int ij = 0; ij < ijend; ij++) {
            double cij = ij + 1;
            rzb[ij] = coll(cij * dz, eil, 1);
            rpzb[ij] = coll(FastMath.sqrt(ts2 + cij * cij * dz * dz), eil, 1);
        }
    }

    /**
     * Fills RDZB[2..ipjend] and RTZB[2..ipjend] (slots 0 and 1 are unused).
     * TT = (IPJ-2)*DZ + 2*X
     * RDZB(IPJ) = ZMUT(TT, EIL, 1)
     * RTZB(IPJ) = ZMUT(sqrt(TS2 + TT^2), EIL, 1)
     */
    private void computeRdzbRtzb(int ipjend, double x, double dz, double ts2, double eil,
                                  Complex[] rdzb, Complex[] rtzb) {
        for (int ipj = 2; ipj <= ipjend; ipj++) {
            double tt = (ipj - 2.0) * dz + 2.0 * x;
            rdzb[ipj] = zmut(tt, eil, 1);
            rtzb[ipj] = zmut(FastMath.sqrt(ts2 + tt * tt), eil, 1);
        }
    }

    /**
     * Fills all bay-pair impedance tables for MN=1..mnend.
     *
     * <pre>
     *   HIJ = MN*DY - EIL
     *   RZE(MN)      = COLL(HIJ, EIL, 1)
     *   RPZE(MN)     = ECH(2*DX, HIJ, EIL, 1)
     *   RDZE(MN,I)   = ECH(2*((I-1)*DZ+X), HIJ, EIL, 1)
     *   RTZE(MN,I)   = ECH(sqrt(TS2 + (2*((I-1)*DZ+X))^2), HIJ, EIL, 1)
     *   R(MN,IJ)     = ECH(IJ*DZ, HIJ, EIL, 1)
     *   RP(MN,IJ)    = ECH(sqrt(TS2 + IJ^2*DZ^2), HIJ, EIL, 1)
     *   RDZ(MN,IPJ)  = ECH((IPJ-2)*DZ+2*X, HIJ, EIL, 1)
     *   RT(MN,IPJ)   = ECH(sqrt(TS2 + ((IPJ-2)*DZ+2*X)^2), HIJ, EIL, 1)
     * </pre>
     */
    private void computeBayArrays(int mnend, int ne, int ijend, int ipjend,
                                   double dy, double dz, double dx, double x, double ts2,
                                   double eil,
                                   Complex[] rze, Complex[] rpze,
                                   Complex[][] rdze, Complex[][] rtze,
                                   Complex[][] rArr, Complex[][] rpArr,
                                   Complex[][] rdz, Complex[][] rt) {
        for (int mn = 1; mn <= mnend; mn++) {
            double hij = mn * dy - eil;
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
    }

    // -------------------------------------------------------------------------
    // Impedance accumulation
    // -------------------------------------------------------------------------

    /**
     * Selects the four impedance terms for the element pair (m,i) vs (n2,j).
     * Returns {@code Complex[4]} = { zterm1, zterm2, zterm3, zterm4 }.
     *
     * <ul>
     *   <li>Same bay, same element (m==n2, i==j): self terms.</li>
     *   <li>Same bay, different element (m==n2, i!=j): collinear terms.</li>
     *   <li>Different bay, same element (m!=n2, i==j): echelon single terms.</li>
     *   <li>Different bay, different element (m!=n2, i!=j): echelon mixed terms.</li>
     * </ul>
     */
    private Complex[] selectImpedanceTerm(int m, int i, int n2, int j,
                                           Complex rzz, Complex rpzz,
                                           Complex[] rdzz, Complex[] rtzz,
                                           Complex[] rzb, Complex[] rpzb,
                                           Complex[] rdzb, Complex[] rtzb,
                                           Complex[] rze, Complex[] rpze,
                                           Complex[][] rdze, Complex[][] rtze,
                                           Complex[][] rArr, Complex[][] rpArr,
                                           Complex[][] rdz, Complex[][] rt) {
        Complex zterm1, zterm2, zterm3, zterm4;
        if (m == n2 && i == j) {
            // Self: ZTERM1=RZZ, ZTERM2=RPZZ, ZTERM3=RDZZ(I), ZTERM4=RTZZ(I)
            zterm1 = rzz;
            zterm2 = rpzz;
            zterm3 = rdzz[i - 1];
            zterm4 = rtzz[i - 1];
        } else if (m == n2) {
            // Same bay, different element: collinear
            int ij2 = FastMath.abs(i - j);
            zterm1 = rzb[ij2 - 1];
            zterm2 = rpzb[ij2 - 1];
            zterm3 = rdzb[i + j];
            zterm4 = rtzb[i + j];
        } else if (i == j) {
            // Different bay, same element: echelon
            int mn2 = FastMath.abs(m - n2);
            zterm1 = rze[mn2];
            zterm2 = rpze[mn2];
            zterm3 = rdze[mn2][i];
            zterm4 = rtze[mn2][i];
        } else {
            // Different bay AND different element
            int mn2 = FastMath.abs(m - n2);
            int ij2 = FastMath.abs(i - j);
            zterm1 = rArr[mn2][ij2];
            zterm2 = rpArr[mn2][ij2];
            zterm3 = rdz[mn2][i + j];
            zterm4 = rt[mn2][i + j];
        }
        return new Complex[]{zterm1, zterm2, zterm3, zterm4};
    }

    /**
     * Accumulates ZSUM1..ZSUM4 over all (m,i,n2,j) element pairs.
     * Returns {@code Complex[4]} = { zsum1, zsum2, zsum3, zsum4 }.
     */
    private Complex[] sumImpedances(int nb, int ne,
                                     Complex rzz, Complex rpzz,
                                     Complex[] rdzz, Complex[] rtzz,
                                     Complex[] rzb, Complex[] rpzb,
                                     Complex[] rdzb, Complex[] rtzb,
                                     Complex[] rze, Complex[] rpze,
                                     Complex[][] rdze, Complex[][] rtze,
                                     Complex[][] rArr, Complex[][] rpArr,
                                     Complex[][] rdz, Complex[][] rt) {
        Complex zsum1 = Complex.ZERO;
        Complex zsum2 = Complex.ZERO;
        Complex zsum3 = Complex.ZERO;
        Complex zsum4 = Complex.ZERO;

        for (int m = 1; m <= nb; m++) {
            for (int i = 1; i <= ne; i++) {
                for (int n2 = 1; n2 <= nb; n2++) {
                    for (int j = 1; j <= ne; j++) {
                        Complex[] terms = selectImpedanceTerm(m, i, n2, j,
                                rzz, rpzz, rdzz, rtzz,
                                rzb, rpzb, rdzb, rtzb,
                                rze, rpze, rdze, rtze,
                                rArr, rpArr, rdz, rt);
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
     *
     * @param dij  distance between element axes
     * @param eil  half-length of elements
     * @param kode mode: 1 = normal half-wave, -1 = full-wave
     * @return complex impedance
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

        // ZSUM = (CSZ1(UZ)-2*CSU1)*CMPLX(CW1,-SW1) + (CSZ1(VZ)-2*CSV1)*CMPLX(CW1,SW1)
        //       + 2*(CSUZP-CSU1-CSV1) + 2*CSUZP*(CW1+1)
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
     * Note: in FORTRAN, COLL uses COMMON /CUR/ DIJ, EIL, HIJ, KODE.
     * In the KOP 6 code, for collinear pairs DIJ is used as the axial distance
     * but the subroutine itself only uses HIJ and EIL (no DIJ).
     * The FORTRAN calls are: "HIJ = CMN*DY-EIL; CALL COLL(RZE(MN))" — collinear.
     * For RPZB calls: "DIJ=SQRT(TS+...); CALL ZMUT(RPZB(IJ))" — NOT COLL.
     * COLL is called with HIJ only; the parameter 'dij' passed here is unused inside COLL.
     *
     * @param hij  axial distance between dipole near-ends (not including element length)
     * @param eil  half-length of elements
     * @param kode mode: 1 = normal, -1 = full-wave
     * @return complex mutual impedance
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
     *
     * @param dij  perpendicular distance between element axes
     * @param hij  axial offset between element centers
     * @param eil  half-length of elements
     * @param kode mode: 1 = normal, -1 = full-wave
     * @return complex mutual impedance
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

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
