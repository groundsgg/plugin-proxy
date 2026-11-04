# Whereami

Players can use `/whereami` without a permission grant to identify their current
connection for support. The agreed presentation is one Grounds system message:

```text
[Grounds] Deine aktuelle Verbindung im Netzwerk.
  Diese Angaben helfen dem Support, Probleme einzugrenzen.

  Region: nl-ams1
  Proxy: velocity-eu-1
  Server: lobby-2
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
