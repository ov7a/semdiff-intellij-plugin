# Semantic Diff for IntelliJ

Before using this plugin, please vote for this IntelliJ IDEA feature request: [Make diff tool SMART, semantic, structure aware](https://youtrack.jetbrains.com/projects/IJPL/issues/IJPL-99523/Make-diff-tool-SMART-semantic-structure-aware).

Also, ***disclaimer***: this plugin is 100% vibecoded, so don't expect any quality, and treat it as MVP or proof-of-concept. 

Shows diffs computed by an external semantic diff tool inside the IDE's own diff viewer, instead of
the built-in line-based comparison.

The plugin is an integration layer only. It bundles no binaries — you install a tool yourself and
point the plugin at it.

Requires IntelliJ IDEA 2026.2. Not published to the Marketplace.

## Supported tools

| Tool | Granularity | Notes |
|---|---|---|
| [difftastic](https://github.com/Wilfred/difftastic) | intra-line spans with syntax kinds | Best results. |
| [diffsitter](https://github.com/afnanenayet/diffsitter) | intra-line spans | Fewer languages — no Kotlin grammar in 0.9. |
| [sem](https://github.com/Ataraxy-Labs/sem) | whole changed declarations | Coarsest: marks entire functions and classes, never individual tokens. The only one that reports moved code. |

Any failure — missing binary, non-zero exit, timeout, output the plugin cannot read — falls back to
the built-in diff and says so once.

## Install

```bash
./gradlew build
```

Then *Settings → Plugins → ⚙ → Install Plugin from Disk* and pick the zip from
`build/distributions/`.

## Configure

*Settings → Tools → Diff & Merge → Semantic Diff*

On first start the plugin looks for an installed tool on PATH and in the usual install locations
(`/opt/homebrew/bin`, `/usr/local/bin`, `~/.cargo/bin`, `~/.local/bin`), because a GUI-launched IDE
on macOS does not inherit a shell PATH. If it finds one, semantic diff already works. If it finds
none, the plugin is inert.

In a diff window, the toolbar's viewer chooser switches between **Semantic**, **Side-by-side** and
**Unified**. The platform remembers that choice per diff place.

## Seeing the difference

Two cases under `test-data/cases/`, both opened with *Compare Two Files*:

- **`formatter-run/`** — the same code with a different layout and not one token changed. The
  built-in diff marks several blocks, and "Ignore whitespaces" does not help, because the text that
  was on one line is now on four. difftastic and diffsitter report no differences at all.
- **`showcase/`** — ten numbered sections, each changing in exactly one way, covering everything the
  plugin can show: reformatting, a renamed local, changed strings, comments, types, keywords and
  delimiters, a moved method, an added and a deleted one. Read the section comments in the file;
  each says what it demonstrates.

## Limitations

- **Two-side diffs only.** The platform exposes no custom-fragment hook for its three-way viewer, so
  merges and conflicts keep the built-in comparison.
- **The diff toolbar's ignore setting does not change the semantic result.** A semantic diff is
  already whitespace-insensitive, so the tool's answer is used whatever the setting is. The
  consequence is that with "Ignore whitespaces" on, the built-in and semantic diffs agree about much
  more and switching between them looks less dramatic.
- **Unified viewer is not covered.** It builds its own diff provider; only the two-side path is
  wired up.
- **One active tool globally.** The platform's per-file-type external-tool selection is not
  replicated.
- **Moves are shown as a deletion plus an insertion.** IntelliJ's fragments are a monotonic line
  alignment and cannot express a move. The experimental viewer draws a box round both halves and
  names the counterpart, which is as close as this viewer gets.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how it works, the module layout, how to add a tool, and
how to run the tests.
