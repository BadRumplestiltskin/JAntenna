package com.jantenna.baker;

import com.jantenna.BakerVersion;
import com.jantenna.AntennaMetadata;

import com.jantenna.GainTable;
import com.jantenna.physics.AntennaGainCalculator;
import com.jantenna.physics.MufParameters;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Bakes any HFMUFES analytical {@link AntennaGainCalculator} (KOP 1..17)
 * into a {@link GainTable} per {@code docs/antennas.md} §9.4.
 *
 * <p>The harness loops over the standard ITU-R HF grid
 * (28 frequencies × 360 azimuths × 91 elevations = 917,280 points by
 * default) and calls the underlying calculator at every grid cell with
 * matching Fresnel reflection coefficients derived from the
 * {@code (sigma, epsilon)} ground parameters in the spec.</p>
 *
 * <p>The Fresnel formulas mirror
 * {@code HighFrequencyMufService.calculateGain} (the runtime path):</p>
 * <pre>
 *   dif  = epsilonR - j * 60 * sigmaSm * wave
 *   acsq = sqrt(dif - cos²(elev))
 *   qper = (dif*sin(elev) - acsq) / (dif*sin(elev) + acsq)
 *   qpar = (sin(elev)     - acsq) / (sin(elev)     + acsq)
 * </pre>
 *
 * <p>Per locked Phase-D scope (M17 closure-ladder re-baseline once),
 * the resulting {@link GainTable} reproduces the analytical formula's
 * output to within int16 × 100 quantisation (≤ 0.005 dB) at every
 * grid cell.  Trilinear interpolation between cells introduces a
 * further ~0.05 dB worst-case error near sharp lobes — well within
 * the 4.60 dB closure-ladder margin.</p>
 */
public final class HfmufesAnalyticalBaker implements AntennaBaker {

    /** Speed of light in MHz·km, matches {@code HighFrequencyMufService.VOFL}. */
    private static final double VOFL = 299.792458;

    private final AntennaGainCalculator calculator;
    private final HfmufesBakeSpec spec;
    private final double[] frequenciesMHz;

    /** Build with the default 3..30 MHz integer-step axis (28 frequencies). */
    public HfmufesAnalyticalBaker(AntennaGainCalculator calculator, HfmufesBakeSpec spec) {
        this(calculator, spec, standardFreqAxis());
    }

    /** Build with an explicit frequency axis (e.g. {@code [14.15]} for single-freq). */
    public HfmufesAnalyticalBaker(AntennaGainCalculator calculator,
                                   HfmufesBakeSpec spec,
                                   double[] frequenciesMHz) {
        if (calculator.getAntennaType() != spec.kop()) {
            throw new IllegalArgumentException(
                    "Calculator KOP " + calculator.getAntennaType()
                    + " disagrees with spec KOP " + spec.kop());
        }
        this.calculator = calculator;
        this.spec = spec;
        this.frequenciesMHz = frequenciesMHz.clone();
    }

    @Override
    public String sourceKind() {
        return "HFMUFES_KOP_" + spec.kop();
    }

    @Override
    public BakeResult bake(Path source, String targetGroup, String targetName) {
        // source path ignored — analytical
        //
        // Written straight into the flat int16 grid: an intermediate
        // double[F][360][91] tensor would cost ~7 MB and a second full pass
        // over 917k cells purely to convert.
        short[] flat = new short[frequenciesMHz.length * 360 * 91];
        double peak = Double.NEGATIVE_INFINITY;
        int idx = 0;

        for (int f = 0; f < frequenciesMHz.length; f++) {
            double freq = frequenciesMHz[f];
            double wave = VOFL / freq;
            // Ground constants depend on wavelength alone, not on direction.
            Complex dif = new Complex(spec.epsilonR(), -60.0 * spec.sigmaSm() * wave);
            for (int a = 0; a < 360; a++) {
                double azRad = FastMath.toRadians(a);
                for (int e = 0; e < 91; e++) {
                    if (e == 0) {
                        // Below-horizon row — encode sentinel so the runtime
                        // sentinel-collapse path engages naturally.
                        flat[idx++] = GainTable.toCentiDb(GainTable.SENTINEL_DBI);
                        continue;
                    }
                    double elRad = FastMath.toRadians(e);
                    double sinEl = FastMath.sin(elRad);
                    double cosEl = FastMath.cos(elRad);
                    Complex acsq = dif.subtract(cosEl * cosEl).sqrt();
                    Complex qpar = new Complex(sinEl).subtract(acsq)
                                                     .divide(new Complex(sinEl).add(acsq));
                    Complex qper = dif.multiply(sinEl).subtract(acsq)
                                                     .divide(dif.multiply(sinEl).add(acsq));
                    MufParameters params = spec.at(freq, azRad, elRad);
                    double gainDbi = calculator.calculateGain(params, sinEl, cosEl, wave, qpar, qper, dif)
                                   + spec.userGainDb();
                    flat[idx++] = GainTable.toCentiDb(gainDbi);
                    if (gainDbi > peak) peak = gainDbi;
                }
            }
        }
        double[] azimuths   = new double[360]; for (int i = 0; i < 360; i++) azimuths[i] = i;
        double[] elevations = new double[91];  for (int i = 0; i < 91;  i++) elevations[i] = i;
        GainTable table = new GainTable(frequenciesMHz.clone(), azimuths, elevations, flat);

        // Metadata
        AntennaMetadata.Source src = new AntennaMetadata.Source(
                sourceKind(), null, null, spec.toCanonicalJson());
        AntennaMetadata.Baker bakerMeta = new AntennaMetadata.Baker(
                HfmufesAnalyticalBaker.class.getSimpleName(),
                BakerVersion.MODULE_SHA,
                BakerVersion.VERSION);
        AntennaMetadata.Grid gridMeta = new AntennaMetadata.Grid(
                frequenciesMHz.clone(), 360, 91, true,
                frequenciesMHz.length > 1);
        AntennaMetadata meta = new AntennaMetadata(
                targetName, targetGroup,
                spec.description() == null ? "HFMUFES KOP " + spec.kop() : spec.description(),
                src, bakerMeta,
                BakerVersion.JANTENNA_VERSION, Instant.now(),
                gridMeta,
                peak,
                frequenciesMHz[0],
                frequenciesMHz[frequenciesMHz.length - 1],
                1);
        return new BakeResult(table, meta);
    }

    /** Standard 3..30 MHz integer-step frequency axis (28 entries). */
    static double[] standardFreqAxis() {
        double[] freqs = new double[28];
        for (int i = 0; i < 28; i++) freqs[i] = 3.0 + i;
        return freqs;
    }
}
