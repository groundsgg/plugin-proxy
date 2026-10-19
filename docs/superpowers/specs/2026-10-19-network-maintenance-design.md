# Network maintenance and MOTD

Approved scope: Config Service backed maintenance, scheduled start, optional automatic end, separately estimated end, permission/UUID whitelist bypass, kick existing non-bypass players, configurable announcements, all proxies/Java/Bedrock, Portal maintenance and MOTD editors. Never enable live maintenance as a deployment test.

## Contract

Config app `velocity`, environment `stage` or `prod`, documents `maintenance/active` and existing `motd/active`. Stage and Production are separate connections, never fall back across environments.

Maintenance JSON (strict at Forge, compatible explicit parser in proxy):
```
{
  "schemaVersion": 1,
  "enabled": false,
  "id": "UUID",
  "startsAt": "2026-10-19T20:00:00.000Z",
  "endsAt": null,
  "estimatedEndAt": null,
  "reason": "Wartungsarbeiten",
  "joinMessage": "<red>Wartungsarbeiten</red>\n{reason}\nVoraussichtliches Ende: {end}",
  "motd": "<red>Wartungsarbeiten</red>\n<gray>Voraussichtliches Ende: {end}</gray>",
  "kickExisting": true,
  "whitelist": [],
  "announcementSeconds": [10800,7200,3600,1800,600,300,120,60],
  "announcementMessage": "<yellow>Wartungsarbeiten in {remaining}: {reason}</yellow>"
}
```
Required fields above. UUID canonical, no UUID-version constraint (Floodgate support). Dates ISO UTC. endsAt/estimatedEndAt nullable, if set > startsAt. endsAt automatically disables effective maintenance; estimatedEndAt only displays ETA. Limits: reason 500 chars, messages/MOTD 4096 chars, whitelist 1000 UUIDs, offsets <=32 distinct integers 1..604800. Preserve ID on edits; create a new ID when intentionally starting a new episode. Offset threshold local announcements never replay after bootstrap/restart. Whitelist OR `grounds.maintenance.bypass` exempts joins AND existing-player kicks. Kick only on transition/episode entry, never merely because a config refresh changed metadata. Local player enumeration and delivery on every proxy ensures network-wide behavior without duplicate cross-proxy broadcasts.

MOTD JSON preserves existing shape: {text:string,maxPlayers?:integer,source?:string,updatedBy?:string,updatedAt?:string}. text 1..4096, maxPlayers 1..1000000. Forge stamps actor/time. Preview is safely rendered React text, supported-format preview may be partial and must say so.

Forge routes:
- GET /v1/admin/network/:environment => {environment,available,maintenance:Document|null,motd:Document|null}. Config missing document returns defaults in Document.settings and version:null. Unconfigured environment => available:false, both null.
- PUT /v1/admin/network/:environment/maintenance or /motd body {expectedVersion:string|null,settings:...} => Document.
- Document = {version:string|null,settings:...,updatedAt:string|null,updatedBy:string|null}.
- Decimal version strings preserve BIGINT precision. null means atomic create-if-absent using Config POST; existing updates require If-Match. Conflict 409. No unconditional writes or arbitrary config paths.
- Permissions ADMIN_AREA_ACCESS plus NETWORK_STAGE_VIEW/NETWORK_STAGE_MANAGE or NETWORK_PRODUCTION_VIEW/NETWORK_PRODUCTION_MANAGE. Manage implies ability to view via either-permission check. Service-account Forge JWTs cannot enter these routes.
- Forge dedicated env NETWORK_CONFIG_STAGE_URL/TOKEN_FILE and NETWORK_CONFIG_PRODUCTION_URL/TOKEN_FILE. Re-read projected token per request. Fixed paths, 5s timeout, no redirects, bounded responses, sanitized errors.

Proxy caches valid documents, polls every 5s and refreshes on NATS invalidation hints. Pings/logins never do remote IO. Keep last valid on failure, deny ordinary new joins once read freshness >30s; bypass still allowed. No mass kick caused by config outages. HTTP 404 is known disabled initial configuration. LoginEvent uses verified UUID and permission provider. Maintenance ping overrides normal MOTD; Java may advertise Maintenance status with incompatible protocol while actual LoginEvent enforces admission. Bedrock sees MOTD and login denial.

Infrastructure: Forge SA receives exactly existing network/stage/resourcepacks/global plus velocity/stage/{motd,maintenance}/active via pipe-separated grants. Config create endpoint accepts exact writers consistent with PUT. NATS Stage Config publisher adds config.velocity.stage.changed to existing subject. Preserve live bindings/flags and never change unrelated networking enforcement.
