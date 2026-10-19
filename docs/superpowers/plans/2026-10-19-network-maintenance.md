# Implementation plan

1. Config Service: multi-document least-privilege grants; atomic create authorization; tests and verification.
2. Proxy: pure validated schedule engine and bypass admission/kick tests; Config polling; LoginEvent and server-list integration; permission manifest and localization; build.
3. Forge: strict typed contract; bounded private Config client/CAS and atomic create; environment-specific RBAC routes; tests/typecheck.
4. Portal: protected environment-aware network page; schedule/whitelist/announcements editor, explicit bypass kick behavior, MOTD editor and safe preview; tests/typecheck/build.
5. Infra: exact grants and NATS publisher subjects, explicit environment connections; relevant checks.
6. Strong primary security/correctness review of all domains, cross-proxy behavior, conflicts, unavailable Production, UUID handling and bypass kicks. Create reviewable PRs and record deployment prerequisites. Never turn maintenance on to test.
