---
description: Trino Maven and pom.xml standards
applyTo: '**/pom.xml'
---
# Trino Maven Standards

## Keep pom.xml Clean and Sorted

### Automatic sorting
Run this command to fix most ordering and structure issues:

```bash
./mvnw sortpom:sort
```

### Requirements
- Dependencies must be ordered correctly
- XML elements must be ordered correctly
- Overall pom.xml structure must be correct

Your build will fail if these requirements are not met.

## Validation

### Run checks before PR
Always run before opening a pull request:

```bash
./mvnw validate
```

This runs checkstyle and other Maven checks.
