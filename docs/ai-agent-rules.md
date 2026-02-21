# AI Agent Rules

This document describes how AI coding agent rules are managed in the Trino Gateway project using [Rulesync](https://github.com/dyoshikawa/rulesync).

## Overview

Trino Gateway uses a centralized rule management system that automatically generates configuration files for various AI development tools from a single source of truth. This ensures consistency across different AI coding assistants while maintaining code style standards.

## Architecture

The project uses **Rulesync** to maintain a Single Source of Truth (SSOT) for AI agent rules:

```
.rulesync/rules/              # Source of truth - all rules defined here
├── airlift-formatting.md     # Airlift code formatting standards
├── trino-code-style.md       # Trino Java code style guidelines
├── trino-maven.md            # Maven and pom.xml standards
├── trino-vector-api.md       # Vector API implementation guidelines
└── pull-request-guidelines.md # PR and commit message guidelines

↓ Generated automatically by Rulesync ↓

.claude/rules/                # Claude Code configuration
.cursor/rules/                # Cursor IDE configuration
.github/instructions/         # GitHub Copilot configuration
```

## Rule Categories

### 1. Trino Code Style (`trino-code-style.md`)

Core Java coding standards for the Trino Gateway project:

- **Naming conventions**: Avoid `get` prefix, no abbreviations
- **Language features**: Explicit types (avoid `var`), minimal ternary operators
- **Collections**: Prefer Guava immutable collections
- **Error handling**: Categorized exceptions with error codes
- **String formatting**: Use `format()` for readability
- **Testing**: Avoid mocking libraries, prefer AssertJ

### 2. Airlift Formatting (`airlift-formatting.md`)

Code formatting standards based on the Airlift style template:

- **Import organization**: No wildcard imports, specific import order
- **Line length**: Maximum 180 characters
- **Brace placement**: Braces on next line for classes/methods
- **Control statements**: Always use braces
- **Class member ordering**: Specific field and method arrangement

### 3. Maven Standards (`trino-maven.md`)

Build and dependency management guidelines:

- **pom.xml maintenance**: Use `./mvnw sortpom:sort` for automatic sorting
- **Pre-PR validation**: Run `./mvnw validate` before submitting pull requests
- **Dependency ordering**: Strict ordering requirements enforced by build

### 4. Vector API Guidelines (`trino-vector-api.md`)

Guidelines for Java Vector API ([JEP 508](https://openjdk.org/jeps/508)) usage:

- **Scalar fallbacks**: Always provide equivalent scalar implementations
- **Hardware detection**: Enable vectorized code only where beneficial
- **Testing**: Ensure vectorized and scalar implementations match
- **Platform testing**: Verify on AMD, ARM, and Intel CPUs

### 5. Pull Request Guidelines (`pull-request-guidelines.md`)

Guidelines for creating pull requests and writing commit messages:

- **PR structure**: Single logical change per PR, every commit should build
- **Commit organization**: Separate mechanical from logical changes
- **Commit messages**: Follow the seven rules (50 char subject, imperative mood, etc.)
- **Review process**: Keep reviews simple and efficient
- **Merge strategy**: Use rebase and merge to maintain linear history

## Supported AI Tools

Rulesync automatically generates configurations for:

| Tool             | Configuration Location         | Auto-loaded |
|------------------|-------------------------------|-------------|
| Claude Code      | `.claude/rules/*.md`          | ✅          |
| Cursor           | `.cursor/rules/*.mdc`         | ✅          |
| GitHub Copilot   | `.github/instructions/*.md`   | ✅          |

## Managing Rules

### Prerequisites

Install Rulesync globally:

```bash
npm install -g rulesync
```

### Modifying Existing Rules

1. Edit the source file in `.rulesync/rules/`:

```bash
vim .rulesync/rules/trino-code-style.md
```

2. Regenerate configurations for all AI tools:

```bash
rulesync generate --targets claudecode,copilot,cursor --features rules
```

### Adding New Rules

1. Create a new rule file in `.rulesync/rules/`:

```bash
cat > .rulesync/rules/my-new-rule.md << 'EOF'
# My New Rule

## Description

Guidelines for...
EOF
```

2. Generate configurations:

```bash
rulesync generate --targets claudecode,copilot,cursor --features rules
```

### Importing from Existing Configurations

If you have existing tool-specific configurations, import them:

```bash
# Import from Cursor
rulesync import --targets cursor

# Import from Claude Code
rulesync import --targets claudecode

# Import from GitHub Copilot
rulesync import --targets copilot
```

### Adding Support for Additional Tools

Rulesync supports many AI coding tools. To add support for additional tools:

```bash
# Example: Add Windsurf support
rulesync generate --targets claudecode,copilot,cursor,windsurf --features rules
```

See the [Rulesync documentation](https://github.com/dyoshikawa/rulesync) for a full list of supported tools.

## Best Practices

### 1. Single Source of Truth

Always edit rules in `.rulesync/rules/` directory. Never manually edit generated files in `.claude/`, `.cursor/`, or `.github/instructions/` as they will be overwritten.

### 2. Consistent Formatting

Follow markdown best practices when writing rules:

- Use clear headings and sections
- Provide code examples with good/bad patterns
- Keep rules concise and actionable
- Use consistent emoji conventions (✅ for good, ❌ for bad)

### 3. Version Control

Commit both source rules and generated configurations:

```bash
git add .rulesync/rules/
git add .claude/rules/ .cursor/rules/ .github/instructions/
git commit -m "Update AI agent rules"
```

### 4. Automated Regeneration

Consider adding Rulesync to your CI/CD pipeline or pre-commit hooks to ensure generated files are always up-to-date:

```bash
# .git/hooks/pre-commit
#!/bin/bash
rulesync generate --targets claudecode,copilot,cursor --features rules
git add .claude/rules/ .cursor/rules/ .github/instructions/
```

## Troubleshooting

### Rules Not Loading

If AI tools are not recognizing your rules:

1. Verify files were generated correctly:
   ```bash
   ls -la .claude/rules/ .cursor/rules/ .github/instructions/
   ```

2. Check Rulesync version:
   ```bash
   rulesync --version
   ```

3. Regenerate configurations:
   ```bash
   rulesync generate --targets claudecode,copilot,cursor --features rules
   ```

### Conflicting Rules

If you encounter conflicts between tool-specific behaviors:

1. Check the source rule in `.rulesync/rules/`
2. Verify the generated output for each tool
3. Adjust the source rule to be more tool-agnostic

## References

- [Airlift Codestyle Repository](https://github.com/airlift/codestyle)
- [Trino Code Style Guidelines](https://trino.io/development/code-style.html)
- [Rulesync Documentation](https://github.com/dyoshikawa/rulesync)
- [Rulesync Official Website](https://rulesync.dyoshikawa.com/)

## Related Documentation

- [Development Guide](development.md)
- [Contributing Guidelines](../CONTRIBUTING.md)
