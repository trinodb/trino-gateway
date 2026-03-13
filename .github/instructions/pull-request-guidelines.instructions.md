# Pull Request and Commit Guidelines

## Pull Request Structure

### Single logical change per PR
A typical pull request should strive to contain a **single logical change**, but not necessarily a single commit. Unrelated changes should generally be extracted into their own PRs.

```
// ❌ BAD - Multiple unrelated changes in one PR
- Add new feature X
- Refactor unrelated class Y
- Fix bug in component Z

// ✅ GOOD - Single logical change
- Add new feature X with related refactoring
```

### Stack of commits
If a pull request contains a stack of commits, then popping any number of commits from the top of the stack should not break the PR, meaning that **every commit should build and pass all tests**.

```bash
# ✅ GOOD - Each commit is valid
git log --oneline
abc123 Add feature tests
def456 Implement feature logic
ghi789 Add feature interface

# Each of these should work:
git checkout ghi789  # Should build and pass tests
git checkout def456  # Should build and pass tests
git checkout abc123  # Should build and pass tests
```

## Commit Organization

### Separate mechanical from logical changes
Keep mechanical changes separate from logical and functional changes:

**Mechanical changes** (separate commits):
- Refactoring
- Renaming
- Removing duplication
- Extracting helper methods
- Static imports

**Logical changes** (separate commits):
- Functional changes
- Business logic modifications
- New features

```
// ✅ GOOD commit structure
Commit 1: Extract helper method for validation
Commit 2: Add new validation logic for feature X
Commit 3: Update tests for new validation

// ❌ BAD commit structure
Commit 1: Add feature X, refactor Y, rename Z, fix bug in W
```

### Commit separation test
When in doubt about splitting a change into a separate commit, ask yourself:

> "If all other work in the PR needs to be reverted after merging to master for some objective reason (such as a bug has been discovered), is it worth keeping that commit still in master?"

If yes → separate commit. If no → combine with related commit.

## Commit Messages

### Seven rules of a great commit message

1. **Separate subject from body with a blank line**
2. **Limit the subject line to 50 characters**
3. **Capitalize the subject line**
4. **Do not end the subject line with a period**
5. **Use the imperative mood** (as used in a command or request)
6. **Wrap the body at 72 characters**
7. **Use the body to explain what and why versus how**

### Examples

```
// ✅ GOOD
Add caching support for query results

Implement an LRU cache to store query results and reduce
database load. The cache has a configurable TTL and size limit.

This improves response time by 40% for repeated queries.

// ❌ BAD
added caching.

Added some caching code to make things faster. I used LRU
algorithm because it seemed good and set the cache size to 1000
items which should be enough for most cases.
```

```
// ✅ GOOD - Imperative mood
Fix race condition in connection pool
Add support for SSL connections
Remove deprecated configuration options

// ❌ BAD - Past tense
Fixed race condition in connection pool
Added support for SSL connections
Removed deprecated configuration options
```

### Subject line patterns

```
// ✅ GOOD - Clear, imperative, under 50 characters
Add routing rule validation
Fix memory leak in query processor
Refactor authentication module
Update dependencies to latest versions

// ❌ BAD - Too long, wrong mood, punctuation
Added a new feature that allows users to validate routing rules.
fixed a bug
Refactoring.
```

### Body formatting

```
// ✅ GOOD - Wrapped at 72 characters, explains what and why
Implement query result caching

Add an LRU cache to store query results. The cache improves
performance by reducing redundant database queries. Cache size
and TTL are configurable via gateway configuration.

This change reduces average query latency by 40% in production
workloads with high query repetition.

// ❌ BAD - No wrapping, explains only how
Implement query result caching

I created a new class called QueryCache that extends LinkedHashMap and overrides removeEldestEntry to implement LRU behavior. Then I added methods to put and get cache entries. I also added configuration parameters.
```

## Commit History

### Keep logical changes together
Commit messages and history are important because they are used by other developers to keep track of the motivation behind changes.

**Rewriting and reordering commits is a natural part of the review process.**

```bash
# ✅ GOOD - Use interactive rebase to reorder and clean up
git rebase -i HEAD~5

# Reorder commits to group related changes
# Squash fixup commits
# Rewrite unclear commit messages
```

### Evolution of change
Order commits in a way that explains the **evolution of the change** by itself.

```
// ✅ GOOD - Logical progression
Commit 1: Add interface for new router
Commit 2: Implement basic routing logic
Commit 3: Add support for weighted routing
Commit 4: Add comprehensive tests
Commit 5: Update documentation

// ❌ BAD - Random order
Commit 1: Update documentation
Commit 2: Fix typo in router
Commit 3: Add tests
Commit 4: Implement routing logic
Commit 5: Add interface
```

## Review Process

### Make reviews efficient
Reviews are time-consuming and volunteer-driven. **Keep reviews as simple as possible** by:

- Breaking large changes into multiple PRs
- Separating mechanical from logical changes
- Writing clear commit messages
- Responding promptly to feedback
- Keeping the PR up to date with the base branch

### Rebase regularly
The rapid evolution of the project means contributors must:

- **Rebase their pull request branch regularly**
- **Ensure no conflicts prevent merging**

```bash
# Keep your branch up to date
git fetch upstream
git rebase upstream/main

# Resolve any conflicts
# Force push to update PR (after review approval)
git push --force-with-lease origin feature-branch
```

## Merge Strategy

Pull requests are usually merged using the **rebase and merge strategy** to:
- Preserve commits from the contributor
- Avoid adding empty merge commits
- Maintain a linear history

## Quick Reference

| Do | Don't |
|----|-------|
| Single logical change per PR | Multiple unrelated changes |
| Every commit builds and passes tests | Broken intermediate commits |
| Separate mechanical from logical | Mix refactoring with features |
| Imperative mood in subject | Past tense in subject |
| 50 char subject, 72 char body | Long subject lines |
| Explain what and why | Explain only how |
| Rebase regularly | Let branch get stale |
| Clean commit history | Keep fixup/WIP commits |
