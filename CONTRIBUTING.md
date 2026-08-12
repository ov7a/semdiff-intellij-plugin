# Contributing

## How it works

The plugin does not render diffs. It supplies `List<LineFragment>` to the stock two-side viewer
through `DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER`, which `DiffUtil.createTextDiffProvider` reads off
the `DiffRequest`. That single seam is the whole integration: the gutter, folding, navigation,
aligned mode and every diff action keep working, and only the fragments come from somewhere else.

```
DiffRequest ──► SemanticDiffTool.createComponent
                  │  request.putUserData(CUSTOM_DIFF_COMPUTER, SemanticDiffComputer)
                  └─► RichSemanticDiffViewer            (a SimpleDiffViewer subclass)
                          │ performRediff (background, cancellable)
                          └─► SemanticDiffComputer.compute
                                 │ write temp files, run the CLI
                                 │ handler.parseOutput → SemanticDiffResult   (tool-agnostic)
                                 │ FragmentPlanner → List<FragmentSpec>
                                 └─► LineFragmentImpl
```

Everything left of `LineFragmentImpl` is IntelliJ-free and unit-testable.

Any failure — missing binary, non-zero exit, timeout, unparseable output, a result the planner
refuses — delegates to `ComparisonManager` and reports once per session per reason.

## Modules

| Module | IntelliJ? | Contents |
|---|---|---|
| `semdiff-model` | no | DTOs, `FragmentPlanner`, `AlignmentBuilder`, UTF-8↔UTF-16 offset conversion |
| `semdiff-tools` | no | `SemanticDiffToolHandler` and one implementation per tool |
| `semdiff-ide` | yes | settings state, command runner, temp files, tool discovery, notifications |
| `semdiff-ui-settings` | yes | the settings page, Detect and Test |
| `semdiff-ui-diff` | yes | `FrameDiffTool`, the diff computer, tool ordering, the experimental viewer |
| root | yes | `plugin.xml`, packaging, platform tests |

`semdiff-model` and `semdiff-tools` must stay IntelliJ-free; `checkNoIntellijDependencies` fails the
build if that slips.

Platform tests live in the **root** project, because that is where `plugin.xml` is on the test
classpath — without it the plugin's services never register.

## Adding a tool

Implement [`SemanticDiffToolHandler`](semdiff-tools/src/main/kotlin/dev/ov7a/semdiff/tools/SemanticDiffToolHandler.kt),
add it to `HandlerRegistry`, add it to `CliTools` in `build-logic`, and generate its goldens.
`HandlerContractTest` then checks it against the rules every handler must follow. Nothing outside
`semdiff-tools` changes.

There is deliberately no `buildArguments`. Argv comes from substituting `%1`/`%2`/`%3` into the
argument pattern held in settings, which the handler only *seeds* — positional operands, flagged
operands and leading subcommands are all expressible that way, and the user can correct them.

`SemanticDiffResult` is the contract: an alignment plus either character `spans` (difftastic,
diffsitter) or whole-declaration `regions` (sem). `Granularity` says which, and is the only place
anything branches on the tool's precision.

## Tests

```bash
./gradlew testAll -Psemdiff.requireTools=true
```

- `provisionCliTools` downloads the pinned binaries, checksum-verified, into
  `build/cli-tools/<id>/<version>/`. `SEMDIFF_TOOLS_DIR` overrides it with a pre-seeded cache laid
  out as `<dir>/<tool-id>/<version>/<executable>`.
- Without `-Psemdiff.requireTools=true` a missing binary skips its tests; with it, they fail. CI
  should always pass it so the suite can never be quietly green.
- `-Psemdiff.updateGolden=true` rewrites `test-data/expected/`. Goldens are checked in so a tool
  upgrade shows up as a reviewable diff.
- The golden tests run the real binaries over `test-data/cases` and pin the parsed model. They also
  assert every `Changed` result survives `FragmentPlanner` — a pinned model the viewer cannot use is
  worth nothing.
- `./gradlew runIde` starts a sandbox IDE.

## Build

Gradle 9, Kotlin 2.4, JVM 21, IntelliJ Platform Gradle Plugin 2.18.

`buildPlugin` is wired into `assemble`, so `build` produces the installable zip.

The version lives in `gradle.properties` and can be overridden per build with `-Pversion=1.2.3`,
which is how the release workflow stamps the tag into the plugin descriptor and the zip name.

## Releasing

Create a release in the GitHub UI. Publishing it creates the tag and fires
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds with the tag as the
version — a leading `v` is stripped — and attaches the zip to that release.

The workflow runs `build`, so the tests and the plugin verifier gate the release. Two things to know
if it fails for a reason that is not your change:

- It downloads the IntelliJ platform (a few GB) and the pinned CLI tools, so a cold run is slow.
- The goldens in `test-data/expected` were generated on macOS. If a tool's output differs on Linux
  the golden tests will fail there first; regenerate on Linux and commit if that turns out to be
  real, rather than assuming the release is broken.

`verifyPlugin` prints a few dozen `nonexistent 'classPath' elements` warnings. Those come from the
Plugin Verifier reading the *target IDE's* own `product-info.json`, which lists module jars absent
from the distribution. They are about the IDE, not this plugin.

## Platform notes

Findings that are not obvious from the API and cost real time to establish. Sources are the
IntelliJ Community sources and the installed distribution.

**`CUSTOM_DIFF_COMPUTER` is two-side only.** `SimpleThreesideDiffViewer` calls `ComparisonManager`
directly, so there is no hook for three-way. The handler interface carries `supportsThreeWay` for
completeness, but no researched tool supports it either.

**Making the tool the default must use the tool order, not a `DiffToolSubstitutor`.**
`switchToDiffTool` opens with `if (isSameToolOrSubstitutor(chosen, active)) return`, which resolves
the chosen tool *through* registered substitutors. A substitutor mapping `SimpleDiffTool ->
SemanticDiffTool` therefore makes picking Side-by-side a silent no-op and the built-in viewer
unreachable. `SemanticDiffToolOrder` prepends the tool to `DiffSettings.diffToolsOrder` per
`DiffPlaces` constant instead — the same mechanism the chooser itself uses, so switching works and
persists.

**The computer must be detached from the request on dispose.** `switchToDiffTool` re-applies the
*same* `DiffRequest`, so a computer left on it is picked up by the built-in viewer too. Doing this in
`onDispose` is safe because `doApplyRequest` destroys the old viewer before creating the new one.

**Do not consult the `ComparisonPolicy`.** Falling back to the built-in computer for anything but
`DEFAULT` silently disables the plugin for everyone who has "Ignore whitespaces" on. A semantic diff
is already whitespace-insensitive; there is nothing to re-apply.

**difftastic reports UTF-8 byte offsets**, and counts lines as though every file ended with a
newline, so a file that does not gets one phantom line past the end. Its JSON is gated behind
`DFT_UNSTABLE=yes`, and changes within a chunk come out in a different order on each run, so handlers
normalize before returning.

**Entity-level tools report `regions` and no `spans`.** `FragmentPlanner` has to treat regions as
changed lines, or an equal-length file pair produces an empty diff. It also splits a changed run at
reported entity boundaries, or two adjacent changed declarations merge into one block that reads as
a single enormous change.

**A move is not expressible.** `LineFragment` is a monotonic line alignment. Tools that report a
moved declaration produce crossing ranges, so `AlignmentBuilder.fromChangedLinesUsingContent` works
the correspondence out from the lines the tool did *not* mark and the move comes out as a deletion
plus an insertion. The experimental viewer draws the move itself as a box.

**`FrameDiffTool.getIcon()` exists only from 2026.2.** Before that the chooser is a text segmented
button, so there are no icons to differentiate.

**Highlighter colours must not come from the scheme alone.** `additionalTextAttributes` is shipped
for schemes named `Default` and `Darcula`; a current IDE uses `IntelliJ Light` and `Dark`, so a
scheme lookup returns null there and nothing is drawn at all. Every colour has a built-in `JBColor`
default and the scheme is an override.

**Marks must clash with what they mark.** Colouring a changed string green, under green string text,
is invisible. So is setting a foreground at all, since syntax highlighting already owns it. The
experimental viewer underlines and boxes in colours deliberately unlike the syntax colours.
`SpanColors.DIAGNOSTIC` paints everything as a magenta block, which is the fastest way to tell "not
drawing" from "not visible".
