# Join welcome

Show a compact Grounds welcome once on a fresh network connection. Reuse the existing chat logo when the client's chat resource pack is verified; otherwise show the Grounds name. Put clickable Discord, TikTok and Shop links below it. Discord uses the lobby bossbar's `https://grnds.link/discord`; the other two links use explicitly documented, replaceable placeholders.

Keep the original voice request: explain party, game and nearby voice and link to Simple Voice Chat installation only when the client has not announced the mod. Disabled voice and failed UDP connections must not be treated as missing installation. Observe client channel registration and the voice handshake without consuming its messages.

Approach: the existing proxy plugin owns network joins and translations, so render and deliver there. Backend delivery would repeat on server changes; voice presence snapshots conflate missing installation with disconnected voice. Wait five seconds after the first backend connection for mod negotiation and pack readiness. Carry a delivered marker in the namespaced `grounds:welcome` client cookie across transfers. Reset it on a fresh login before preferred-region routing; incoming transfers with an absent/pending marker receive their first welcome, while delivered markers suppress repeats. Cancel delayed delivery on disconnect, and identify pending work by connection object to avoid stale reconnect callbacks. Provide English, German, French and Spanish copy.

User supplied the layout and selected the three links and placeholder policy. Implementation choices: five-second grace period, standard text fallback, configurable link environment variables. No publishing is part of this change.

Review fix: an unconditional transfer skip loses the first welcome when PreferredRegionListener redirects before backend selection. Regression tests cover both this route and transfers during the delivery delay, delivered markers, and stale cookie replies.
