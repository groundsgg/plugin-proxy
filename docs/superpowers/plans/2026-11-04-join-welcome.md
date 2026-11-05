# Join Welcome Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver the agreed welcome block once per fresh network join with conditional voice installation help.
**Architecture:** A renderer builds translated Adventure components; a Velocity listener handles connection lifecycle and observes mod negotiation. Wire both into plugin-proxy.
**Tech Stack:** Kotlin, Velocity, Adventure, library-i18n, JUnit 6.
**Spec:** `docs/superpowers/specs/2026-11-04-join-welcome-design.md`

## Global Constraints

- Discord is `https://grnds.link/discord`; TikTok and Shop are replaceable placeholders.
- Skip server switches and delivered cross-proxy transfers; preserve a pending first welcome through early region redirects. Clean up on disconnect/shutdown.
- Voice off or disconnected does not mean the mod is absent.
- Draw logo only with verified chat-font readiness; support all four plugin locales.

## Review Focus

- Mod handshake before the first backend connection must suppress the hint.
- Forward client voice handshakes intact and ignore backend-originated messages.
- Late callbacks from a disconnected session must not message a reconnect.
- A transfer onto another proxy must preserve the first welcome if still pending and suppress an already delivered welcome.
- Missing packs and markup-like player names must produce readable, safe text.

### Task 1: Welcome rendering and lifecycle

**Files:** New `welcome/WelcomeMessage.kt`, `welcome/WelcomeListener.kt`, tests under the same package; modify plugin initialization, four resource bundles, README.
**Interfaces:** Renderer `render(playerName, locale, logoReady, voiceInstalled): Component`; listener accepts a delayed scheduler and readiness lookup to test actual event handlers without a running proxy.

- [x] Write rendering tests for clickable URLs, localized voice visibility, safe names and logo fallback; lifecycle tests for switches, transfers, early/disabled voice, backend spoofing, disconnect and reconnect.
- [x] Run `./gradlew :velocity:test --offline --console=plain` and confirm new tests fail because the feature is absent.
- [x] Implement renderer, observer and lifecycle gates; wire a five-second delayed task and register observed voice channels. Add translations and document configurable placeholder links.
- [x] Run `./gradlew test --offline --console=plain` and repository formatting checks. Review the diff for forwarding, cleanup and cross-proxy behavior.

Execution: primary agent, current suitable checkout on a task branch; no separate implementation agents needed.

Review ruling: use a client cookie rather than unconditional transfer suppression, because preferred-region redirects happen at PostLogin before a first backend connection. Cookie state is cosmetic, reset for each LOGIN, queried only for TRANSFER, and tracked by the exact connection.

Validation: `./gradlew spotlessApply test spotlessCheck --offline --console=plain` passed after the transfer fix; 229 tests, zero failures/errors/skips. `git diff --check` passed. Live Minecraft clients and multi-proxy deployment smoke checks were not available in this session.

Visual follow-up: replace the small chat G plus text heading with the verified large HUD wordmark, switch readiness to PlayerHudAppearanceQuery, reserve space for the tall bitmap, and put native icon+label links on separate rows. Updated existing link/layout tests first (three expected failures), then implemented; full Gradle test and formatting checks passed. Stage follow-up is within the existing deployment authorization.
