# JAntenna

Antenna pattern library extracted from [jvoacap](https://github.com/BadRumplestiltskin/jvoacap).

Provides a unified internal `GainTable` representation (frequency x azimuth x elevation tensor) capable of holding 1D, 2D, or 3D antenna patterns; readers for the VOACAPL `.voa` family; analytical bakers for HFMUFES KOP antenna types; and a conversion CLI.

## Coordinates

```xml
<dependency>
    <groupId>com.jantenna</groupId>
    <artifactId>jantenna</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Build

```
mvn install
```

Requires JDK 21.

## Status

Phase 1 skeleton. Migration of code from jvoacap in progress.
