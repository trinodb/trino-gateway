---
paths:
  - '**/*.java'
---
# Trino Vector API Guidelines

## Assumptions

Safe to assume JVM has Vector API ([JEP 508](https://openjdk.org/jeps/508)) enabled, but NOT safe to assume it will be faster than scalar code.

## Implementation Approach

When adding Vector API implementations:

### 1. Provide scalar equivalent
Always include an equivalent scalar implementation in code.

### 2. Use hardware detection
Use configuration flags and hardware support detection to only enable vectorized code on hardware where it performs better.

### 3. Add matching tests
Ensure vectorized and scalar implementations produce identical results:

```java
@Test
public void testVectorizedMatchesScalar() {
    // Test that vector and scalar produce same results
    assertThat(vectorizedImplementation(input))
        .isEqualTo(scalarImplementation(input));
}
```

### 4. Include micro-benchmarks
Demonstrate performance benefits compared to scalar equivalent. Verify benefits hold for all enabled CPU architectures.

## Hardware Considerations

Test on multiple CPU platforms:
- **AMD CPUs**: Datacenter processors
- **ARM CPUs**: Both Apple Silicon and datacenter (note: significant differences between generations and types)
- **Intel CPUs**: Datacenter processors

Performance characteristics can vary dramatically between platforms.

## Configuration Pattern

```java
// ✅ GOOD
if (isVectorApiSupported() && isArchitectureBeneficial()) {
    return vectorizedImplementation(data);
}
return scalarImplementation(data);
```
