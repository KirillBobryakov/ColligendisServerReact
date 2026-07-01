---
name: git-commit
description: Generate conventional commit messages for Java projects. Use when user says "commit", "create commit", "commit changes", or after completing code changes that need to be committed.
---

# Git Commit Message Skill

Generate conventional, informative commit messages for Java projects.

## When to Use
- After making code changes
- User says "commit this" / "commit changes" / "create commit"
- Before creating PRs

## Format Standard

Use Conventional Commits format:
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types (Java context)
- **feat**: New feature (new API, new functionality)
- **fix**: Bug fix
- **refactor**: Code refactoring (no functional change)
- **test**: Add/update tests
- **docs**: Documentation only
- **perf**: Performance improvement
- **build**: Maven/Gradle changes
- **chore**: Maintenance (dependency updates, etc)

### Scope Examples (Colligendis)
- Layer: `controller`, `service`, `parser`, `database`, `dto`, `security`
- Domain: `numista`, `neo4j`, `auth`, `catalogue`, `collection`, `meshok`
- Component: `NTypeService`, `AbstractService`, `NumistaPageLoader`

### Subject Rules
- Imperative mood: "Add support" not "Added support"
- No period at end
- Max 50 chars
- Lowercase after type

### Body (optional but recommended)
- Explain WHAT and WHY, not HOW
- Wrap at 72 chars
- Reference issues: "Fixes #123" / "Relates to #456"

## Examples

### Simple fix
```
fix(parser): prevent NPE when Numista page has no issuer link

Check for missing issuer element before parsing to avoid
NullPointerException during NType page load.
```

### Feature with breaking change
```
feat(auth)!: move refresh token to HTTP-only cookie

BREAKING CHANGE: Refresh tokens are no longer returned in
AuthResponse JSON. Clients must rely on cookie-based refresh.
```

### Refactoring
```
refactor(database): extract normalized property sync utility

Move Unicode normalization and Neo4j property backfill into
NormalizedNeo4jPropertyUtil for reuse across entity services.
```

### Test addition
```
test(parser): add Numista collection save response parser tests

Cover success, partial failure, and malformed JSON payloads
from Numista collection save API responses.
```

### Build/dependency update
```
build(deps): add selenium-java for Cloudflare page loading

Add Selenium and WebDriverManager dependencies to support
browser-based Numista page fetches when HTTP client is blocked.
```

## Workflow

1. **Analyze changes** using `git diff --staged` (or `git diff` if nothing staged)
2. **Identify scope** from modified files
3. **Determine type** based on change nature
4. **Generate message** following format
5. **Execute commit**: `git commit -m "message"`

Follow the repository git safety protocol: run `git status`, `git diff`, and
`git log` before committing; only commit when the user explicitly asks.

## Token Optimization

- Read staged changes ONCE: `git diff --staged --stat` + targeted file diffs
- Don't read entire files unless necessary
- Use concise body - aim for 2-3 lines max
- Batch multiple small changes into logical commits

## Anti-patterns

❌ Avoid:
- "fix stuff" / "update code" / "changes"
- "WIP" commits (unless explicitly requested)
- Mixing unrelated changes (use separate commits)
- Over-detailed technical implementation in message

✅ Good commits:
- Single logical change
- Clear, searchable subject
- References issues when applicable
- Explains business value

## Integration with GitHub

After commit, suggest next steps:
- "Push changes?"
- "Create PR for issue #X?"
- "Continue with next task?"

## Common Patterns for Java Projects

### Adding new functionality
```
feat(controller): add Numista collection sync endpoints

Expose save, delete, and refresh endpoints so users can
mirror their Numista collection in Neo4j.
```

### Fixing bugs
```
fix(neo4j): match issuer search on normalizedName

Issuer catalogue search was matching raw display names and
missing diacritic variants. Use normalizedName consistently.
```

### Dependency updates
```
build(deps): bump spring-boot-starter-webflux

Update WebFlux starter for security patches. No API changes
required in controller code.
```

### Documentation improvements
```
docs(neo4j): document NumistaCollectionItem relationships

Add node labels, relationship directions, and uuid conventions
to the Neo4j data model reference.
```

### Performance optimizations
```
perf(parser): cache parsed denomination numeric filters

Avoid re-parsing denomination strings on every catalogue
filter request by caching parsed numeric values.
```

## Multi-file Changes

When changes span multiple components:

```
refactor(catalogue): split catalogue controller into services

- Move summary queries to CatalogueSummaryService
- Move NType listing to CatalogueNtypesService
- Keep controller thin with request validation only

Improves testability without changing REST contracts.
```

## Breaking Changes

Always use BREAKING CHANGE footer:

```
feat(api)!: rename RulerParser to RulingAuthorityParser

BREAKING CHANGE: Parser class and bean name changed from
RulerParser to RulingAuthorityParser. Update any direct
references in custom wiring or tests.
```

## Quick Reference Card

| Change Type | Type | Example Scope |
|-------------|------|---------------|
| New feature | feat | controller, parser, auth |
| Bug fix | fix | numista, neo4j, security |
| Refactoring | refactor | database, service |
| Tests | test | parser, util |
| Docs | docs | neo4j, readme |
| Build | build | maven, deps |
| Performance | perf | parser, neo4j |
| Maintenance | chore | ci, tooling |

## References

- [Conventional Commits Specification](https://www.conventionalcommits.org/)
- Source: [decebals/claude-code-java](https://github.com/decebals/claude-code-java)
