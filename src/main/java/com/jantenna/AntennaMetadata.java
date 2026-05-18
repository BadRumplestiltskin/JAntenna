package com.jantenna;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * JSON-serialisable metadata sidecar for a baked {@code .gtable}.
 * Lives next to the binary as {@code <name>.json}.  Carries
 * provenance and grid summary for drift detection and catalogue
 * display.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AntennaMetadata(
        @JsonProperty("name")            String  name,
        @JsonProperty("group")           String  group,
        @JsonProperty("description")     String  description,
        @JsonProperty("source")          Source  source,
        @JsonProperty("baker")           Baker   baker,
        @JsonProperty("jantenna_version") String jantennaVersion,
        @JsonProperty("baked_at")        Instant bakedAt,
        @JsonProperty("grid")            Grid    grid,
        @JsonProperty("peak_gain_dbi")   Double  peakGainDbi,
        @JsonProperty("min_freq_mhz")    Double  minFreqMhz,
        @JsonProperty("max_freq_mhz")    Double  maxFreqMhz,
        @JsonProperty("format_version")  int     formatVersion
) {

    /** Where the pattern came from (file or analytical params). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(
            @JsonProperty("kind")    String kind,
            @JsonProperty("path")    String path,
            @JsonProperty("sha256")  String sha256,
            @JsonProperty("params")  String paramsJson
    ) { }

    /** Which baker module + JAntenna commit produced the .gtable. */
    public record Baker(
            @JsonProperty("module")  String module,
            @JsonProperty("sha")     String sha,
            @JsonProperty("version") String version
    ) { }

    /** Tensor shape + axis spans for catalogue queries. */
    public record Grid(
            @JsonProperty("frequencies_mhz") double[] frequenciesMhz,
            @JsonProperty("az_count")        int      azCount,
            @JsonProperty("el_count")        int      elCount,
            @JsonProperty("az_dependent")    boolean  azDependent,
            @JsonProperty("freq_dependent")  boolean  freqDependent
    ) { }
}
