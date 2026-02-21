---
paths:
  - '**/*.java'
---
# Airlift Code Formatting

## Import Organization

### Never use wildcard imports
Always use explicit imports - no `import java.util.*`:

```java
// ❌ BAD
import java.util.*;
import com.example.*;

// ✅ GOOD
import java.util.List;
import java.util.Map;
import com.example.MyClass;
```

### Import order
Organize imports with blank lines between groups:

```java
// ✅ GOOD
import com.example.MyClass;        // All other packages
import org.springframework.Bean;

import javax.inject.Inject;        // javax packages

import java.util.List;             // java packages
import java.util.Map;

import static java.lang.String.format;  // static imports
import static org.assertj.core.api.Assertions.assertThat;
```

## Line Length

Maximum line length: **180 characters**

## Brace Placement

### Classes and methods - braces on next line

```java
// ✅ GOOD
public class MyClass
{
    public void myMethod()
    {
        // method body
    }
}
```

### Control statements - else/catch/finally/while on new line

```java
// ✅ GOOD
if (condition)
{
    doSomething();
}
else
{
    doSomethingElse();
}

try
{
    riskyOperation();
}
catch (Exception e)
{
    handleError(e);
}
finally
{
    cleanup();
}

do
{
    work();
}
while (condition);
```

## Always Use Braces

Always use braces for if, while, for, and do-while statements:

```java
// ❌ BAD
if (condition)
    doSomething();

for (int i = 0; i < 10; i++)
    process(i);

// ✅ GOOD
if (condition)
{
    doSomething();
}

for (int i = 0; i < 10; i++)
{
    process(i);
}
```

## Blank Lines

- Keep **1 blank line** maximum in declarations
- Keep **1 blank line** maximum in code
- Keep **0 blank lines** before closing brace

```java
// ✅ GOOD
public class Example
{
    private int field1;
    private int field2;
                              // max 1 blank line
    public void method1()
    {
        int x = 1;
        int y = 2;
                              // max 1 blank line
        process(x, y);
    }
                              // no blank line before }
}
```

## Spacing

### Braces and arrays

```java
// ✅ GOOD - spaces within braces
Map<String, Integer> map = new HashMap<>() { { put("key", 1); } };

// ✅ GOOD - spaces in array initializers
int[] numbers = { 1, 2, 3, 4, 5 };
String[] names = new String[] { "Alice", "Bob", "Charlie" };
```

## Alignment

### Don't align multiline parameters

```java
// ✅ GOOD
public void method(String param1,
    String param2,
    String param3)
{
    // body
}
```

### Do align multiline assignments

```java
// ✅ GOOD
String longVariable = "some value";
int    count        = 42;
double rate         = 3.14;
```

### Do align array initializers

```java
// ✅ GOOD
String[][] matrix = {
    { "a", "b", "c" },
    { "d", "e", "f" },
    { "g", "h", "i" }
};
```

## Wrapping

### Method call chains
Chop down if long:

```java
// ✅ GOOD
result = stream.filter(Objects::nonNull)
    .map(String::toLowerCase)
    .collect(toImmutableList());
```

### Method parameters
Chop down if long:

```java
// ✅ GOOD
processData(
    parameter1,
    parameter2,
    parameter3);
```

## Class Member Order

Order class members as follows:

1. Static final fields (public → protected → package → private)
2. Static fields (public → protected → package → private)
3. Final fields (public → protected → package → private)
4. Instance fields (public → protected → package → private)
5. Constructors
6. Static methods
7. Instance methods
8. Enums
9. Interfaces
10. Static inner classes
11. Inner classes

```java
// ✅ GOOD
public class Example
{
    public static final int MAX_SIZE = 100;
    private static final Logger log = Logger.get(Example.class);
    
    private static int staticCounter;
    
    private final String name;
    private int count;
    
    public Example(String name)
    {
        this.name = name;
    }
    
    public static void staticMethod()
    {
        // static method
    }
    
    public void instanceMethod()
    {
        // instance method
    }
    
    private static class InnerClass
    {
        // inner class
    }
}
```

## Keep Simple Constructs on One Line

Simple methods, lambdas, and classes can stay on one line:

```java
// ✅ ACCEPTABLE
public String name() { return name; }
list.forEach(item -> { process(item); });
private static class Empty { }
```
