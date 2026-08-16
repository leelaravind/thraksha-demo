# THRAKSHA DEMO — PHASE 7 IMPLEMENTATION PROGRESS

## NETWORK PROTECTION / LIVE OUTBOUND GUARD

Implementation run against `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_7.md`, on the
Phase 1–6 known-good baseline.

**Advice-mode device:** Samsung `SM-G781B` (S20 FE), Android 13, `DEVICE_ADMIN`.
**Full-power device:** AOSP `thraksha_do` AVD, Android 13, `DEVICE_OWNER` (the Samsung
unit's Knox warranty fuse permanently blocks Device Owner — see the Phase 4–6 report).

---

## BASELINE (before any Phase 7 change)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 60 tests, 0 failures |
| `connectedDebugAndroidTest` (both devices) | 36 + 36, 0 failures |

---

## WHAT PHASE 7 ADDS

A small, honest live-network pillar: VillainCaller sends one synthetic UDP datagram to a
reserved TEST-NET address; a **per-app-scoped** VpnService intercepts only VillainCaller's
traffic; the real TUN packet is parsed to destination metadata; a signed ThreatPack
network indicator matches; the existing PolicyEngine decides; Advice Mode forwards (and
says so honestly), Full Power drops before forwarding. No general VPN, no TCP stack, no
TLS interception, no payload-based detection.

### Files created

| File | Purpose |
|---|---|
| `security/network/NetworkObservation.kt` | Packet metadata domain object — addressing, protocol, size, timestamp, scoped package. **No payload field.** |
| `security/network/NetworkPacketParser.kt` | Pure IPv4/UDP header parser. Bounds-checked against declared length; every malformed input is a typed `Result`, never a throw. Renders no verdict. |
| `security/network/NetworkGuardState.kt` (`NetworkGuard`) | Process-wide `StateFlow` lifecycle: `Disabled / ConsentRequired / Starting / Active(counters,lastOutcome) / Error`. The single UI truth — never a Compose boolean. |
| `security/network/NetworkThreatEvaluator.kt` | Pure matcher: (`NetworkObservation`, verified ThreatPack) → `Finding?` by literal destination-IP equality. Payload is never an input. |
| `security/network/VpnScope.kt` | The §28 safety invariant: `ALLOWED_PACKAGES = {villaincaller}` and a TEST-NET-only route. The device-wide state (`0.0.0.0/0` + no allowlist) is unrepresentable. |
| `security/network/NetworkGuardEngine.kt` | Per-packet orchestration: parse → observe → evaluate → shared decision → FORWARD/BLOCK verdict → publish verified outcome. Owns no sockets. Fail-closed on Unavailable ThreatPack. |
| `security/policy/SecurityResponseCoordinator.kt` | The smallest extraction (§14) so the static audit AND the live network path share one PolicyEngine invocation + DECISION event, no duplication. |
| `tools/make_threatpack.sh` (extended) | Now also emits the signed `DESTINATION_IP` network indicator; pack bumped to v2. |
| `app/src/test/.../NetworkPacketParserTest.kt` | 15 parser tests incl. a 2000-iteration random-bytes fuzz that must never throw. |
| `app/src/test/.../NetworkThreatAndPolicyTest.kt` | 14 tests: indicator matching, all policy outcomes, BlockNetwork authority, VpnScope invariants. |
| `app/src/androidTest/.../NetworkGuardInstrumentedTest.kt` | 7 on-device tests driving the real engine + production ThreatPack with a crafted packet (no faked consent). |

### Files modified

| File | Change |
|---|---|
| `services/ThrakshaVpnService.kt` | **Rewritten.** Old device-wide echo loop deleted. Now a thin per-app-scoped guard: `addAllowedApplication(villaincaller)` (fails closed if scoping fails — no broad fallback), TEST-NET route, real read loop, `protect()`-ed outbound UDP forward / intentional drop, truthful foreground notification ("Monitoring VillainCaller network traffic"). |
| `security/threatintel/ThreatPackModels.kt` | Added `DESTINATION_IP` type + `NETWORK_SUPPORTED` set (evaluated only by the live evaluator, never the static scanner). |
| `security/threatintel/ThreatPackParser.kt` | Validates `DESTINATION_IP` values as dotted-quad IPv4; unknown types still fail closed. |
| `security/engine/Finding.kt` | Added `FindingSource.NETWORK_THREAT_INTELLIGENCE`. One finding model, three pillars. |
| `security/policy/SecurityAction.kt` | `BlockNetwork` activated: `VPN_GUARD` authority (not Device Owner at the API level), MEDIUM tier, executable. New `RequiredAuthority.VPN_GUARD`. |
| `security/policy/PolicyEngine.kt` | A `NETWORK_THREAT_INTELLIGENCE` finding maps to exactly `BlockNetwork` — never permission denial or suspension (§31). AUTO_DEFEND/LOCKDOWN eligibility now includes VPN_GUARD actions. |
| `security/policy/SecurityDecision.kt` | `executableActions` includes VPN_GUARD actions. |
| `security/engine/SecurityAuditEngine.kt` | Static decide path now delegates to `SecurityResponseCoordinator` (no behaviour change; shared with the network path). |
| `security/events/SecurityEvent.kt` | Added `NetworkAttemptObserved` (metadata only). |
| `ThrakshaApplication.kt` | Collector maps it to a `NETWORK` audit row. |
| `ui/screens/DashboardScreen.kt` | Network Guard card: real state, consent-driven enable/disable (genuine `VpnService.prepare()` via activity-result launcher), scoped package, Observed/Forwarded/Blocked counters, last-outcome (ADVISED/BLOCKED). Per-finding NETWORK source label. Stale "network monitor unavailable" text replaced. |
| `enforcement/DeviceOwnerEnforcer.kt` | `BlockNetwork` marked UNSUPPORTED here (executed by the Network Guard's packet path, not DPM). |
| `villaincaller/.../MainActivity.kt` | "Send Demo Outbound Packet" — one fixed synthetic UDP datagram (`THRAKSHA_DEMO_NETWORK_PROBE`) to `203.0.113.113:443` (RFC 5737 TEST-NET-3). No user data, no reply expected, no real server. |
| `.gitattributes` | (unchanged assets already `-text`; threatpack re-signed in place.) |

### Architecture / scope / protocol

* **Scoped package:** `com.thraksha.demo.villaincaller` only, via `addAllowedApplication`.
  Route: `203.0.113.0/24` (TEST-NET-3) — never `0.0.0.0/0`. GoodCaller, Guardian and every
  other app bypass the tunnel entirely.
* **Protocol:** IPv4 + UDP fully parsed. IPv6/TCP are `Unsupported` (safe, out of scope).
  The demo probe uses exactly UDP.
* **Network indicator:** `demo-network-endpoint`, type `DESTINATION_IP`, value
  `203.0.113.113`, classification `DEMO_TEST_NETWORK_INDICATOR`, severity HIGH, weight 88,
  in signed ThreatPack **v2**. Plus a `DESTINATION_IP` negative control the probe never hits.
* **Policy integration:** live findings flow through the same `PolicyEngine` via
  `SecurityResponseCoordinator`. Advice → forward; Full Power AUTO_DEFEND → BlockNetwork.

### Build & test results

| Check | Result |
|---|---|
| `clean :app :goodcaller :villaincaller assembleDebug` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **92 tests, 0 failures** (60 + 32 new: 15 parser, 14 network/policy, +3 threatpack) |
| `connectedDebugAndroidTest` — SM-G781B | **43 tests, 0 failures, 1 skipped** (DO round-trip) |
| `connectedDebugAndroidTest` — thraksha_do AVD | **43 tests, 0 failures, 4 skipped** (non-DO-only) |

### On-device runtime verification

**S20 FE — Advice Mode (DEVICE_ADMIN):**
Enable Network Guard → genuine VPN key icon in status bar → Status **ACTIVE**, Protected app
villaincaller. Open VillainCaller → Send Demo Outbound Packet. Guardian card:
`OUTBOUND ATTEMPT DETECTED · UDP → 203.0.113.113:443 · Signed demo network indicator matched
(threat-intel:demo-network-endpoint) · ADVISED — packet not automatically blocked ·
"Protected outbound send completed; remote delivery is not claimed."` Counters advanced
Observed/Forwarded/Blocked = 10/1/0. Audit feed:
`NETWORK → THREAT → DECISION(ADVISE) → ADVISED + ACTION(FORWARDED)`, all persisted.
GoodCaller never entered the tunnel.

**thraksha_do AVD — Full Power (DEVICE_OWNER, AUTO_DEFEND):**
Per §30 choreography — VillainCaller was suspended from Phase 6; restored via Guardian's
"Restore demo state" (verified `suspended=false`, audited RESTORE rows), which also resets
audit state so the profile audit does not re-suspend. Enable Network Guard → **genuine
Android VPN consent dialog** ("Connection request… monitor network traffic") → OK → Status
ACTIVE. Open VillainCaller (launchable) → Send Demo Outbound Packet. logcat:
`BLOCKED 203.0.113.113:443 (dropped before forward)`. Guardian card:
`BLOCKED — packet intercepted before forwarding · "Packet to 203.0.113.113:443 intercepted
and intentionally dropped before forwarding — no forwarding socket was created on this
path."` Counters Observed/Forwarded/Blocked = 12/0/1. Audit feed:
`NETWORK → THREAT(demo-network-endpoint) → DECISION(ACT, AUTO_DEFEND) →
ACTION(BLOCK_NETWORK SUCCEEDED)`. GoodCaller unaffected.

### Timings

The end-to-end interception→outcome all shares a single audit-second timestamp
(`21:12:49` Full Power; `21:03:37` Advice), i.e. sub-second and effectively immediate. The
parse → evaluate → policy path is pure in-memory work on a 55-byte packet (microseconds);
the only I/O is the local `protect()`ed UDP send in the Advice path. No optimisation needed
(§32); the interaction feels instantaneous on both devices.

### Safety verification

* Destructive-API grep (`wipeData|resetPassword|reboot(|setApplicationHidden|
  addUserRestriction|setUninstallBlocked|lockNow|FORCE_STOP`) → **0 code hits** (one KDoc).
  A network finding recommends only `BlockNetwork` — never permission denial or suspension.
* VPN scope: `addAllowedApplication(villaincaller)` + TEST-NET route; `0.0.0.0/0` appears
  only in the KDoc describing the prohibited-and-unrepresentable state. Startup fails closed
  to `NetworkGuard.State.Error` if scoping fails. Live route confirmed
  `203.0.113.0/24 -> tun0` in ConnectivityService logs.
* Signatures: rulepack **VALID**, threatpack v2 **VALID**; shipped public keys match the
  private keys; corruption/wrong-key tests still fail closed.
* APK assets: exactly 6 (rulepack + threatpack triples); **0** `PRIVATE KEY` matches; no
  keystore/.pem packaged. `git ls-files keys/` = 0; both private keys gitignored.
* Payload privacy: the probe marker `THRAKSHA_DEMO_NETWORK_PROBE` never appears in any
  event or audit row (asserted by instrumented test); only destination metadata is recorded.
* Audit chain verifies after the new NETWORK/ACTION rows (asserted on-device).

### Limitations / divergences

* Full Power is verified on the AOSP emulator, not the Samsung phone (Knox blocker,
  documented in the Phase 4–6 report). Advice Mode is verified on the real phone.
* On both devices VPN consent was genuine — the phone already held consent from a prior
  session (no dialog; the VPN key icon confirms a real tunnel), the emulator showed and
  accepted the real system dialog. Consent is never faked in code or tests.
* IPv6/TCP are intentionally `Unsupported` — the demo probe is UDP. Not a gap; scope.
* The parser observed a small number of incidental UDP packets from the scoped app
  (counted as Observed/ignored) beyond the demo probe — honest counters reflect this.
* `LOCKDOWN` reuses AUTO_DEFEND network-block eligibility only; wider Safe Mode is Phase 8.

### Acceptance criteria (§39) — all met

Old echo loop gone; no device-wide capture; only VillainCaller routed; genuine consent;
UI reflects real state; synthetic-only demo data; GoodCaller no network finding; real TUN
packet intercepted; destination IP/port parsed from the real packet; signed indicator in
verified ThreatPack; real Finding via existing PolicyEngine; Advice → ADVISED + forward
(no block claimed); Full Power AUTO_DEFEND → BlockNetwork dropped before forwarding, verified
from the actual code branch; payload never the signal and never persisted; no TLS/TCP stack/
backend; audit records observation+decision+outcome and still verifies; Phases 1–6 tests
pass; GoodCaller clean; profile + antivirus + Device Owner containment + restoration all
still work; latency measured; screenshots captured; no private key in APK/source; nothing
committed.

### Screenshots (`temp/screenshots/`)

Advice: `phase7_netguard_off.png`, `phase7_vpn_consent.png` (phone had prior consent →
active), `phase7_villain_netbutton.png`, `phase7_netguard_outcome.png` (ADVISED),
`phase7_advice_audit_top.png` (NETWORK/THREAT/DECISION/ADVISED/ACTION rows).
Full Power: `phase7_emu_consent.png` (real system dialog), `phase7_emu_villain_send.png`,
`phase7_emu_block_rows.png` (NETWORK/THREAT/DECISION), `phase7_emu_action_block.png`
(BLOCK_NETWORK SUCCEEDED), `phase7_emu_blocked_final.png` (BLOCKED card).

### Git working tree

47 entries; nothing committed. New this phase: `security/network/` (6 files),
`security/policy/SecurityResponseCoordinator.kt`, `NetworkPacketParserTest.kt`,
`NetworkThreatAndPolicyTest.kt`, `NetworkGuardInstrumentedTest.kt`, re-signed
`assets/threatpack.*`. Modified: `ThrakshaVpnService.kt` (rewritten), `SecurityAction.kt`,
`PolicyEngine.kt`, `SecurityDecision.kt`, `SecurityAuditEngine.kt`, `Finding.kt`,
`SecurityEvent.kt`, `ThrakshaApplication.kt`, `DeviceOwnerEnforcer.kt`,
`ThreatPackModels.kt`, `ThreatPackParser.kt`, `DashboardScreen.kt`,
`villaincaller/MainActivity.kt`, `tools/make_threatpack.sh`. Both private keys remain
gitignored and out of the APK.

The `thraksha_do` AVD is left provisioned (Device Owner) and Network Guard-capable for
repeat demos; VillainCaller is restored and launchable.
