# PHASE 8 — THREAT CORPUS QUALITY REPORT

**Pack:** `app/src/main/assets/threatpack.json`, **packVersion 3**, schemaVersion 1
**Generated:** 2026-08-13T23:48:44Z by `tools/build_threatpack.py`
**Signed:** RSA-2048 / SHA-256 over the exact asset bytes with the existing
`keys/threatpack_private.pem` (gitignored, never packaged); verified immediately after
signing and again by the unit/instrumented suites and the app's fail-closed loader.
**Signed output size:** 2,175,986 bytes (`threatpack.sig` 344 B, public key 392 B).
**Pack expiry:** 2027-02-10T00:00:00Z (past it the app shows THREAT INTELLIGENCE STALE).

## Sources and dates

| Source | Export date (source-stated) | Acquired |
|---|---|---|
| abuse.ch ThreatFox full JSON | 2026-08-13 22:42 UTC | 2026-08-14 |
| abuse.ch MalwareBazaar full CSV | 2026-08-13 23:02 UTC (recent header) | 2026-08-14 |
| abuse.ch MalwareBazaar CSCB CSV | 2025-08-14 09:13 UTC (as published) | 2026-08-14 |
| abuse.ch URLhaus online CSV | 2026-08-13 23:16 UTC | 2026-08-14 (archived only) |

## Input → output accounting

| Metric | Count |
|---|---|
| ThreatFox IOCs in export | 103,059 |
| → Android-relevant (`apk.*` family or `android` tag) | 1,420 |
| → of which sha256 APK hashes | 377 (all accepted) |
| → of which ip:port | 697 → 670 unique routable IPv4s accepted |
| → of which domain/url/md5/sha1 | 346 — archived, **not shipped** (no evaluator in this build) |
| MalwareBazaar rows in export | 1,121,003 |
| → `file_type = apk` | 9,654 |
| → duplicates with ThreatFox hashes | 8 (merged, ThreatFox provenance kept) |
| → accepted after cap (most recent first) | 2,623 |
| → not shipped due to 3,000-hash cap | 7,023 (cap per the research's investor-pack sizing) |
| CSCB rows | 408 SHA-256 thumbprints accepted (all rows carrying a SHA-256 thumbprint) |
| Malformed rejects (all sources) | 0 |
| Non-routable/reserved IP rejects | 0 |
| Package-name indicators | 0 real (no legitimate source; not fabricated) + 1 controlled demo |

## Shipped pack composition

| Type | Classification | Count | Tier | Expiry |
|---|---|---|---|---|
| BASE_APK_SHA256 | REAL_WORLD_THREAT | 3,000 | STRONG | 2028-08-08 |
| SIGNING_CERT_SHA256 | REAL_WORLD_THREAT | 408 | STRONG | 2028-08-08 |
| DESTINATION_IP | REAL_WORLD_NETWORK_IOC | 670 | CORROBORATING | 2026-11-12 |
| Demo indicators (VillainCaller package/signer/APK, negative controls, network demo) | DEMO_* | 8 | as before (package = CORROBORATING by type default) | none |
| **Total** | | **4,086** | | |

Duplicates inside the shipped pack: 0 (natural-key `(type, value)` uniqueness is
asserted by the build tool and re-checked by the on-device parser's unique-id rule).
Expired indicators at ship time: 0 (all expiries are in the future at generation).

## Verification results

* Build-tool self-verification: OpenSSL signature verify over the exact written bytes — PASS.
* `ThreatPackTest` (unit): bundled pack signature verifies; flipped byte fails; wrong
  key fails; real corpus present (≥1000 REAL indicators), all REAL indicators carry
  `sourceReference` + expiry; DESTINATION_IP indicators are CORROBORATING-tier — PASS.
* `ThreatIntelInstrumentedTest` (device): pack loads Verified on-device; corrupted
  bytes produce a visible Unavailable state — PASS (see progress report).
* Villain base-APK indicator recomputed from the actual `villaincaller-debug.apk`
  (SHA-256 `50027f45…`) and the same APK reinstalled on both test devices — installed
  `base.apk` digest confirmed identical via `adb shell sha256sum`.

## Known limitations

1. **Android signer-cert coverage:** the CSCB is predominantly Windows Authenticode
   certificates (documented in each record). Real Android malware-signer fingerprints
   are not publicly redistributable without sample analysis (forbidden here), so the
   Android-signer positive path is exercised by the controlled demo signer only.
2. **Base-APK vs distributed-APK hashes:** sources hash the distributed sample file; a
   split-APK (App Bundle) install can legitimately differ from the store-served
   artifact. Hash coverage is therefore conservative (exact-match only, zero false
   positives by construction, false negatives possible — the research's stated trade-off).
3. **Hash cap:** 7,023 valid MalwareBazaar APK hashes were left out by the 3,000 cap
   (research's investor-pack sizing); the cap is a constant in the build tool.
4. **Network IOC freshness:** IPs decay fast; the 90-day expiry means the network
   corpus goes stale by 2026-11-12 unless the pack is regenerated (deliberate).
5. **Licensing:** redistribution/commercial use of abuse.ch data requires written
   Spamhaus/abuse.ch confirmation before any commercial build — see
   `temp/THREAT_INTELLIGENCE_PROVENANCE.md` §6.
