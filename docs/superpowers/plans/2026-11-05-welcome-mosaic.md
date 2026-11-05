# Welcome Mosaic Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline.

**Goal:** A larger, sharper, seamless welcome logo that fits closed chat.
**Architecture:** Two native bitmap cells from existing branded artwork; compact decorated rendering; exact pack hash gates on Stage.
**Tech Stack:** Kotlin, Adventure, Java ImageIO, Minecraft bitmap fonts, Gradle, Argo.
**Spec:** ../specs/2026-11-05-welcome-mosaic-design.md

## Global Constraints

Ten rows, original colors, 14px social icons, separate click targets, conditional voice hint, fallback unchanged, preserve current Stage features.

## Review Focus

Native cell limits; seams from glyph advance; closed-chat cropping; four locales; old-pack fallback.

## Task 1: Native mosaic

- [ ] Add failing font tests for 512x241 source, two 256x241 cells, height56/ascent50 and fractional join spacing.
- [ ] Run focused test, then update WelcomeFont and pinned asset provenance.
- [ ] Verify each cell with native client atlas code and pixel reconstruction.

## Task 2: Compact welcome

- [ ] Add failing tests for both slices, ten rows, icon/logo bounds, all locales and unchanged fallback.
- [ ] Update WelcomeGlyphs, WelcomeLayout, WelcomeMessage and four compact greeting keys.
- [ ] Run focused tests then full Gradle test, spotlessApply and build for both projects.

## Task 3: Stage

- [ ] Review changes, sign isolated source commits and build immutable artifacts.
- [ ] Preserve active content ZIP and current Stage features; publish catalog, pack and proxy image.
- [ ] Update hash gates and pack selection with concurrency checks; activate Stage.
- [ ] Verify both Java proxies, Bedrock fallback and client atlas behavior.
