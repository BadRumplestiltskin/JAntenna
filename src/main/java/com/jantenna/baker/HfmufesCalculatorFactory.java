package com.jantenna.baker;

import com.jantenna.physics.MutualImpedanceCalculator;
import com.jantenna.physics.VOACAP48;
import com.jantenna.math.SpecialFunctions;
import com.jantenna.math.VectorizedQuadratureIntegrator;
import com.jantenna.physics.AntennaGainCalculator;
import com.jantenna.physics.ConstantGainCalculator;
import com.jantenna.physics.CurtainArrayGainCalculator;
import com.jantenna.physics.DipoleGainCalculator;
import com.jantenna.physics.DoubleRhomboidGainCalculator;
import com.jantenna.physics.HalfRhombicGainCalculator;
import com.jantenna.physics.InvertedLGainCalculator;
import com.jantenna.physics.LogPeriodicGainCalculator;
import com.jantenna.physics.MonopoleGainCalculator;
import com.jantenna.physics.RhombicGainCalculator;
import com.jantenna.physics.SlopingLongWireGainCalculator;
import com.jantenna.physics.SlopingRhombicGainCalculator;
import com.jantenna.physics.SlopingVeeGainCalculator;
import com.jantenna.physics.TiltedDipoleGainCalculator;
import com.jantenna.physics.VerticalDipoleGainCalculator;
import com.jantenna.physics.VerticalRadialGroundGainCalculator;
import com.jantenna.physics.YagiGainCalculator;

/**
 * Instantiates any HFMUFES {@link AntennaGainCalculator} (KOP 1..17,
 * excluding KOP 10 PreStored) outside of the Spring container — required
 * by the {@code jvoacap-antennas} CLI which bypasses Spring for fast
 * startup per {@code docs/antennas.md} §11.
 *
 * <p>Shared helpers ({@link SpecialFunctions}, {@link MutualImpedanceCalculator})
 * are constructed once at factory creation and reused across all
 * KOP instances produced from the same factory.  Stateless and thread-
 * safe.</p>
 *
 * <p><b>KOP 10 PreStored is intentionally excluded.</b>  It's the
 * legacy table-lookup path that {@link com.voacap.antenna.GainTable}
 * itself replaces — trying to bake it would be circular.  Cards that
 * historically resolved to KOP 10 produce a baked pattern from the
 * referenced {@code .voa} file via the {@link Type11Baker} / {@link
 * Type13Baker} path instead.</p>
 */
public final class HfmufesCalculatorFactory {

    private final SpecialFunctions specialFunctions;
    private final MutualImpedanceCalculator mutualImpedance;

    public HfmufesCalculatorFactory() {
        this.specialFunctions = new SpecialFunctions();
        // VOACAP48 quadrature → vectorized integrator → MutualImpedanceCalculator.
        // We use the vectorized variant rather than ApacheCommonsIntegrator
        // because the bake harness sweeps the full 28 × 360 × 91 grid, and
        // ApacheCommons throws TooManyEvaluations on some sharp-lobe LPDA
        // cells in the wings.  Same workaround AreaCoverageEndpointTest
        // applies via -Dintegrator.implementation=vectorized; here we wire
        // it directly because the CLI bypasses Spring property binding.
        this.mutualImpedance = new MutualImpedanceCalculator(
                new VectorizedQuadratureIntegrator(VOACAP48.INSTANCE));
    }

    /**
     * Construct a calculator for the requested KOP.  Returns {@code null}
     * when the KOP is out of range (1..17), KOP 10 (PreStored — see class
     * javadoc), or otherwise unsupported by the analytical-baker path.
     * @param kop
     * @return 
     */
    public AntennaGainCalculator create(int kop) {
        return switch (kop) {
            case 1  -> new RhombicGainCalculator();
            case 2  -> new MonopoleGainCalculator(specialFunctions);
            case 3  -> new DipoleGainCalculator(specialFunctions);
            case 4  -> new YagiGainCalculator(specialFunctions);
            case 5  -> new VerticalDipoleGainCalculator(specialFunctions);
            case 6  -> new CurtainArrayGainCalculator(specialFunctions);
            case 7  -> new SlopingVeeGainCalculator();
            case 8  -> new InvertedLGainCalculator(specialFunctions);
            case 9  -> new SlopingRhombicGainCalculator();
            case 10 -> null;     // PreStored — bake .voa file directly via Type11/Type13Baker
            case 11 -> new SlopingLongWireGainCalculator(specialFunctions);
            case 12 -> new ConstantGainCalculator();
            case 13 -> new LogPeriodicGainCalculator(mutualImpedance);
            case 14 -> new TiltedDipoleGainCalculator(mutualImpedance);
            case 15 -> new HalfRhombicGainCalculator();
            case 16 -> new DoubleRhomboidGainCalculator();
            case 17 -> new VerticalRadialGroundGainCalculator(specialFunctions);
            default -> null;
        };
    }

    /** Human-readable KOP description for help text + diagnostic output.
     * @param kop
     * @return  */
    public static String describe(int kop) {
        return switch (kop) {
            case 1  -> "Rhombic";
            case 2  -> "Vertical Monopole";
            case 3  -> "Horizontal Half-Wave Dipole";
            case 4  -> "Horizontal Yagi";
            case 5  -> "Vertical Dipole";
            case 6  -> "Curtain Array";
            case 7  -> "Sloping Vee";
            case 8  -> "Inverted L";
            case 9  -> "Sloping Rhombic";
            case 10 -> "Pre-stored Pattern (not bakeable — use Type11/Type13 baker)";
            case 11 -> "Sloping Long Wire";
            case 12 -> "Constant Gain";
            case 13 -> "Horizontal Log-Periodic";
            case 14 -> "Tilted Dipole";
            case 15 -> "Half Rhombic";
            case 16 -> "Double Rhomboid";
            case 17 -> "Vertical Radial-Ground Monopole";
            default -> "Unknown";
        };
    }
}
