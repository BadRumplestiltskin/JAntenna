package com.jantenna.repository;

import com.jantenna.GainTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Composes multiple {@link GainTableRepository}s into a single layered
 * lookup chain.  User layer is searched first, classpath defaults
 * second.
 *
 * <p>{@link #load} returns the first hit; {@link #contains} returns
 * {@code true} if any layer has the pattern; {@link #list} unions all
 * layers' lists with stable order (first-layer entries first, then
 * second-layer entries not seen in the first, etc.).</p>
 */
public final class LayeredGainTableRepository implements GainTableRepository {

    private final List<GainTableRepository> layers;

    public LayeredGainTableRepository(GainTableRepository... layers) {
        Objects.requireNonNull(layers, "layers");
        this.layers = List.of(layers);
    }

    @Override
    public boolean contains(String name) {
        for (GainTableRepository layer : layers) {
            if (layer.contains(name)) return true;
        }
        return false;
    }

    @Override
    public GainTable load(String name) throws IOException {
        for (GainTableRepository layer : layers) {
            if (layer.contains(name)) {
                return layer.load(name);
            }
        }
        throw new AntennaNotInRepository(name);
    }

    @Override
    public List<String> list() throws IOException {
        Set<String> union = new LinkedHashSet<>();
        for (GainTableRepository layer : layers) {
            union.addAll(layer.list());
        }
        List<String> out = new ArrayList<>(union);
        Collections.sort(out);
        return out;
    }
}
