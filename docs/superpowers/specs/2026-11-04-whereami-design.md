# Whereami

Players can use `/whereami` without a permission grant to identify their current
connection for support. The agreed presentation is one Grounds system message:

```text
[Grounds] Allgemein | Dein Standort im Netzwerk
  Diese Angaben helfen dem Support, Probleme einzugrenzen.

  [Pin] Region: nl-ams1
  [Knoten] Proxy: velocity-eu-1
  [Server] Server: lobby-2
```

Use the existing recipient-aware system renderer and palette. The hint and labels
are muted. Proxy and server values are interactive accent text: hover explains
that clicking copies the full ID. Retain complete IDs. Region is ordinary body
text. Missing values render as a noninteractive em dash. Localize all prose in
English, German, French and Spanish, including the player-only console response.

The user chose the indented block over a compact single-line summary. Send the
block once so the system prefix is not repeated on every row. No separate UI,
network request or location cache is needed for a command about its invoker.
Each proxy supplies its own `REGION` (falling back to the existing region code)
and `PROXY_ID`; read the invoker's current backend at execution time. This works
independently on every proxy and reflects backend switches immediately.

Implementation choices: colon-separated labels avoid fragile table alignment in
Minecraft's proportional font; no ID abbreviation is required. Keep existing
`/proxydebug` behavior unchanged.


Refinement: use the actual Minecraft Mini-CD values, not the web palette:
primary #F3F0EA, secondary #C1B9AA, interactive #67D9EC. The title is bold.
Three first-party, hard-alpha 10px pixel icons (pin, connected nodes, server rack)
use gold #E7B84B, copper #DDA16F, warm white and cyan. Their own atlas is
`grounds:connection`, U+E410–U+E412, rendered white to preserve texture colors.
One transparent top/bottom margin keeps the visible ink inside the native chat
row. Existing support copy, full IDs and independent copy actions remain.

Icons require both recipient-specific verified HUD readiness and an environment
allowlist proving every approved HUD pack contains the new glyphs. Otherwise
omit icons and preserve the text labels, including on Bedrock. No unverified PUA
characters or assumed client glyphs are sent. A separate immutable Stage pack pin
adds only the new font and atlas; existing pack content and global channels remain.
