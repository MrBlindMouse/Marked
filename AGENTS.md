# AGENTS.md — Marked

## App in one line

Marked is an Android mark app (Kotlin + Jetpack Compose + Material 3, single Gradle module `:app`) that adds marks — logos (PNG / built-in SVG), text, signatures, stickers/emoji, timestamps — onto photos and videos **on-device**. No cloud, no upload, no account; a mark should look like it was always part of the media.

Flow: `Landing (media select) → Mark editor (Add Mark) → Process (apply & export)`.

## How to run and test

```bash
./gradlew assembleDebug   # build
./gradlew test            # unit tests (JVM, JUnit 4)
```

- Tooling baselines: AGP `9.4.1`, Kotlin `2.2.10` (+ compose plugin), Gradle wrapper `9.6.0`, Compose BOM `2026.02.01`, kotlinx-serialization-json, svg-android (`com.github.japgolly.android:svg-android:2.0.6`).
- `compileSdk`/`targetSdk` 37, `minSdk` 31, `versionCode` 1 / `versionName` 1.0.
- Unit tests: `app/src/test/java/com/bmdstudios/marked/{editor,model}` (pure JVM logic tests — gestures, anchoring, formatting, recipe round-trips, grade matrix). `app/src/androidTest` holds only the template instrumented test; no real device/instrumented tests yet.

## Principles (project ceilings)

- Simple — one job: place a mark. No editing suite.
- Private — fully on-device, local-only storage; no cloud.
- Useful — drag anywhere, pinch to scale, live preview, one-tap export.
- Elegant — restraint in chrome; type-led marks; E-Ink look (warm paper / graphite ink / hairline greys; no accent hues).
- YAGNI; clean breaks over shims; reuse existing helpers; no new dependencies when avoidable.

Explicit non-goals (DESIGN.md "Out of scope (v1)"): trim, filters, audio handling, multi-clip, keyframe-driven timeline motion, extra brand packs, rotation.

## Design contract

Design source of truth: [`DESIGN.md`](DESIGN.md) — principles, concepts (Layer / Transform / Style flat model), MVP scope, decisions, roadmap notes.

Contract to honor when editing: a layer is flat — `Layer = transform + style + type-specific payload`. The pill is a styled backing rect owned by the layer, not a separate layer. Rotation is skipped (v1).

**Planned is not implemented** — check status before assuming a feature exists.

## Architecture map

Single module `app/` under `com.bmdstudios.marked`.

| Area | Path | Role |
|------|------|------|
| Entry | `app/src/main/java/com/bmdstudios/marked/MainActivity.kt` | Single activity; hosts `MarkedEditorScreen` (all routes) |
| Exit | `MarkedExitCta.kt` (root) | Donation/exit CTA + double-back exit |
| Editor hub | `editor/MarkedEditorScreen.kt` | **God module** (~1600 lines): route enum, all screen state, creation helpers, gestures, dialogs, layer controls, process/export wiring |
| Landing | `editor/MarkedLandingScreen.kt` | Media select: scrollable list, long-press multi-select, Add Mark CTA; defines `MarkedMedia` |
| Assets | `editor/MarkedAssetsScreen.kt` | Asset import/list, Add Mark |
| Settings | `editor/MarkedSettingsScreen.kt` | Export defaults (folder/prefix/suffix) + Save |
| Recipes | `editor/MarkedRecipesScreen.kt` | Saved recipes list + Apply |
| Render | `editor/LayerRenderer.kt` | `renderMark(source, layers)` composite + per-layer draw (`drawText`, `drawSignature`, `drawAsset`, `drawSvg`, `drawPlacedContent`); `MARK_INK_COLOR`; `LayerStyle.basicColorMatrix()` grade |
| Video | `editor/MarkedVideoEncode.kt` | `encodeMarkedVideo`: MediaExtractor → decoder → per-frame `renderMark` → encoder → MediaMuxer; `Bitmap.toYuv420` |
| Video preview | `editor/MarkedVideoPreview.kt` | MediaPlayer → SurfaceView |
| Export | `editor/ExportManager.kt` | Gallery save (MediaStore) + share sheet for photo & video |
| Recipes model | `editor/MarkedRecipe.kt` | `@Serializable` recipe + `RecipeLayer`/`RecipePayload`, save/load JSON |
| Built-in SVGs | `editor/BuiltinSvgs.kt` | `BuiltinSvgMark` + `BUILTIN_SVG_MARKS` (Star, Ring) |
| Models | `model/Layer.kt`, `model/LayerType.kt`, `model/LayerTransform.kt`, `model/LayerStyle.kt` | Flat mark model, gesture/grade logic |
| Settings store | `settings/MarkedSettings.kt` | Export defaults persisted to `filesDir/marked_settings.json` |
| Theme | `ui/theme/{Theme,Color,Type}.kt` | `MarkedTheme`, E-Ink color schemes, typography, `MarkedMicroLabel` |

Key flows:

- Photo: pick → `renderMark` composite → `shareMark` (export to gallery + share sheet).
- Video: pick → `encodeMarkedVideo` (per-frame composite) → `exportVideoToGallery` → `shareVideo`. Encode runs at source resolution; marks composite per frame; rotation not applied.
- Batch: recipe layers applied via `RecipeLayer.toLayer(index)` to up to 10 picked media (images → composite + export; videos → encode + export each).

## Gotchas

- Navigation: state-based only — private `MarkedRoute` enum inside `MarkedEditorScreen` (`Landing`, `MarkEditor`, `Settings`, `Recipes`, `Assets`). Adding a screen means adding a route + a branch; no nav library.
- Undo stack (`MarkedEditorScreen`): holds list-of-layer-list snapshots; commits go through `commitLayers` (pushes snapshot). **Pinch/pan gesture updates mutate `layers` directly and bypass the undo stack** — don't assume gestures are undoable.
- Layer ids: `"text-<n>-<timestamp>"`-style with `layers.size + 1 + System.currentTimeMillis()`; recipes rehydrate as `recipe-layer-$index`.
- Timestamps: rendered with `format.format(LocalTime.now())` at render time; corner anchors via `timestampAnchor(...)` (text anchors top-left, corner presets shift the anchor).
- Signatures: strokes captured in display space via pointer input; converted to authored space via `signatureAuthoredPoint` (`authored = display / scale - transform pos`); render requires ≥ 2 points.
- Text/sticker/stamp transforms: text-like layers anchor top-left; assets/stickers/SVG anchor center.
- Export naming: `<prefix><epochMillis><suffix>.png`; images → `Pictures/<folder>`, videos → `Movies/<folder>` (note: videos hardcode `Movies`, images use `Pictures` — divergence to be aware of).
- E-Ink ink: `MARK_INK_COLOR` (graphite `0xFF1B1A16`) for timestamp/sticker fallbacks; newly created text/signature marks seed from `MaterialTheme.colorScheme.primary`. Text swatches live in `MarkedEditorScreen` (`MARK_TEXT_SWATCH_COLORS`).
- Back behaviour: landing = first back → donation CTA, second back → exit (`onExit` → activity `finish()`); all other screens → landing. Implemented via `BackHandler` in `MarkedEditorScreen`.
- Logging: per-file `TAG` constants — `MarkedEditor`, `MarkedRenderer`, `MarkedExport`, `MarkedVideoEncode`.
- minSdk 31 — do not use APIs newer than API 31 without guards.
- JSON persistence: kotlinx.serialization `Json { ignoreUnknownKeys = true }`; recipes → `filesDir/recipes/<sanitized-name>.json`; settings → `filesDir/marked_settings.json`.

## Important files (cheat sheet)

- `app/src/main/java/com/bmdstudios/marked/editor/MarkedEditorScreen.kt` — flow state machine, mark creation, gestures, layer list, process wiring. Read before touching editor behaviour.
- `app/src/main/java/com/bmdstudios/marked/editor/LayerRenderer.kt` — the composite render pipeline; the single place mark visuals are drawn.
- `app/src/main/java/com/bmdstudios/marked/editor/MarkedVideoEncode.kt` — frame-by-frame marked video encode.
- `app/src/main/java/com/bmdstudios/marked/editor/ExportManager.kt` — gallery save + share.
- `app/src/main/java/com/bmdstudios/marked/editor/MarkedRecipe.kt` — recipe persistence/rehydration.
- `app/src/main/java/com/bmdstudios/marked/model/Layer.kt` — flat layer model + `applyGesture` / `withStyle`.
- `app/src/main/java/com/bmdstudios/marked/ui/theme/` — E-Ink tokens and typography.

## Index

- No child `AGENTS.md` files exist yet under this repo. Split per-package (e.g. `editor/`, `model/`) when a package grows enough to warrant its own context file.
- [`DESIGN.md`](DESIGN.md) — design source of truth.
