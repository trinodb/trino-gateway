---
paths:
  - '**/*.java'
---
# Trino Code Style Guidelines

## Core Principles

**Readability over rules**: When readability and code style rules conflict, prioritize readability.

**Consistency**: Keep code consistent with surrounding code where possible.

**Production quality**: Maintain the same quality for production and test code.

## Naming Conventions

### Avoid `get` prefix unless Java bean
Omit `get` or replace with specific verbs (`find`, `fetch`, `compute`):

```java
// ❌ BAD
public String getName() { return name; }
public User getUser(String id) { /* fetches from DB */ }

// ✅ GOOD
public String name() { return name; }
public User findUser(String id) { /* fetches from DB */ }
public User fetchUser(String id) { /* fetches from API */ }
```

### Avoid abbreviations
Use full words except for well-known abbreviations (`max`, `min`, `ttl`):

```java
// ❌ BAD
int cnt, idx, usr, msg, cfg

// ✅ GOOD
int count, index, user, message, configuration
```

## Language Features

### Avoid `var`
Using `var` is discouraged - use explicit types.

### Avoid ternary operator
Use ternary only for trivial expressions:

```java
// ❌ BAD
String result = condition ? fetchFromDatabase() : computeDefault();

// ✅ GOOD
String result;
if (condition) {
    result = fetchFromDatabase();
}
else {
    result = computeDefault();
}

// ✅ ACCEPTABLE (trivial)
int value = enabled ? 1 : 0;
```

### Avoid default in enum switches
Omit `default` clause in exhaustive enum switches for static analysis:

```java
// ❌ BAD
switch (status) {
    case RUNNING: return "active";
    case STOPPED: return "inactive";
    default: throw new IllegalStateException();
}

// ✅ GOOD
switch (status) {
    case RUNNING: return "active";
    case STOPPED: return "inactive";
}
throw new IllegalStateException("Unknown status: " + status);
```

## Collections and Data Structures

### Prefer Guava immutable collections
Use Guava's immutable collections over JDK's unmodifiable for deterministic iteration:

```java
// ❌ BAD
List<String> names = Collections.unmodifiableList(list);

// ✅ GOOD
List<String> names = ImmutableList.copyOf(list);
ImmutableSet<String> uniqueNames = ImmutableSet.of("alice", "bob");
```

### Use streams appropriately
Use streams when appropriate, but avoid in performance-critical sections:

```java
// ✅ GOOD
List<String> activeUsers = users.stream()
    .filter(User::isActive)
    .map(User::name)
    .collect(toImmutableList());
```

## Error Handling

### Categorize exceptions
Always categorize errors with appropriate error codes:

```java
// ❌ BAD
throw new RuntimeException("Too many partitions");

// ✅ GOOD
throw new TrinoException(HIVE_TOO_MANY_OPEN_PARTITIONS, "Too many partitions");
```

## String Formatting

### Prefer String formatting over concatenation
Use `format()` (statically imported) for readable string construction:

```java
// ❌ BAD
String msg = "Session property " + name + " is invalid: " + value;

// ✅ GOOD
String msg = format("Session property %s is invalid: %s", name, value);

// ✅ ACCEPTABLE (simple append)
String path = basePath + "/data";
```

Avoid in performance-critical code.

## Testing

### Avoid mocks
Write mocks by hand instead of using mocking libraries - encourages testable design.

### Use AssertJ
Prefer AssertJ for complex assertions:

```java
// ✅ GOOD
assertThat(result)
    .isNotNull()
    .hasSize(3)
    .extracting(User::name)
    .containsExactly("alice", "bob", "charlie");
```

### Use Airlift's Assertions
For cases not covered by AssertJ, use Airlift's `Assertions` class.

## Class Design

### Define API for private inner classes
Declare members as public in private inner classes if they're part of the class API.

### Utility classes
- Must be `final`
- Must have private constructor
- Must not have public constructor

## License and Documentation

### Add license header
Run `mvn license:format` to generate appropriate license headers.

### Alphabetize documentation
Alphabetize sections in documentation files and table of contents.

## IDE Annotations

### Use @Language for SQL and regex
Document API intent with language injection annotations:

```java
// ✅ GOOD
public void execute(@Language("SQL") String query) {
    // ...
}

@Language("RegExp")
String pattern = "[0-9]+";
```
