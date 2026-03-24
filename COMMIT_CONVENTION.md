# Commit Convention

## Format

```
<short summary in imperative mood>

<optional detailed description>
```

## Rules

- Start with a capital letter
- Use imperative mood: "Add feature", not "Added feature" or "Adds feature"
- First line: concise summary, under 72 characters
- If needed, add a blank line then a detailed description
- No `Co-Authored-By` trailers
- Group related changes into one commit when possible

## Examples

```
Add custom icon and toolbar buttons
```

```
Bump version to 1.2.0, update plugin description

- Version managed in gradle.properties only
- Description lists all features and storage details
```

```
Fix task status not updating on refresh
```

```
Refactor TaskStorageService to use coroutines
```
