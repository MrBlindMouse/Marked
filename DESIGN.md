# Marked — Design

Status: draft v0 · greenfield (init scaffold only, nothing built yet)

## Vision
Marked adds logos, signatures, text and stickers to photos and videos — on-device. No cloud, no upload, no account. A mark should look like it was always part of the media.

## Principles
1. Simple — one job: place a mark. No editing suite.
2. Elegant & beautiful — restraint in chrome; type-led marks; soft surfaces; nothing decorative without purpose.
3. Useful — drag anywhere, pinch to scale, live preview, one-tap export.
4. Private — fully on-device; local-only storage.

## Concepts
- **Layer** — one placed mark. `type: ASSET | TEXT | SIGNATURE | TIMESTAMP | STICKER`.
- **Transform** — position (x/y), scale. Plus type-specific style: font/size/color/weight (text), stroke width (signature), format + position preset (timestamp).
- **Style** — opacity, shadow, border/outline, background pill (rounded backing), applied per layer where meaningful.

Flat model: layer = transform + style + type-specific payload. Pill is a styled backing rect on the layer, not a separate layer.

## MVP scope
- One source item (photo | video).
- Upload asset: PNG, SVG.
- Layer types: asset, text, signature, timestamp, sticker/emoji — with full style set.
- Drag / pinch positioning & sizing on preview.
- Undo, delete, reorder (front/back).
- Export: save to device + share sheet.

## Out of scope (v1)
Trim, filters, audio handling, multi-clip, keyframe-driven timeline motion, extra brand packs.

## Decisions (v0)
- Export: save to gallery (MediaStore) + share sheet.
- SVG: keep (needs on-device vector renderer/parser); resolved Phase 3 with AndroidSVG (svg-android) — SVG marks rasterize through the parser into a `Picture` drawn via `Canvas.drawPicture`.
- Timestamp: fixed presets.
- Rotation: skip (v1).
- Monetization: ads dropped by maintainer decision; donation CTA on exit instead (double-back: first press shows CTA, second closes it and exits the app completely); CTA also asks for a Play Store review before the donate links.
- Outline: layer-level. Layer-level is well-suited to PNG opacity — opacity composites over the whole layer including its stroke as one uniform draw (stroke inset from bounds to avoid clipping irregular PNGs).
- Pill: layer-owned.

## Roadmap notes
- Resolution & scaling: no artificial limits; overlays render at source resolution; layer scale via pinch.
- Batch + recipes: recipes implemented (Phase 5 save/apply); batch apply to many photos and videos shipped (encode → export each).
- Editing functions (color grade / contrast / exposure / ...): implemented as layer-style parameters (like opacity) so a mark can carry its own grade into a recipe and the composite renderer applies it per layer.
- Monetization: free forever; donation CTA on exit (no ads).

## Next steps
Finalize → scaffold editor screens and render pipeline.
