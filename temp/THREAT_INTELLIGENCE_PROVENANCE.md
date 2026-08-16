# PHASE 8 — THREAT-INTELLIGENCE PROVENANCE

**Acquisition date:** 2026-08-14 (UTC)
**Acquired by:** developer-side tooling only (`tools/build_threatpack.py`); the Android
app contains no feed-ingestion code and performs no runtime network lookups.
**Research basis:** `temp/Thraksha Threat Intelligence_ Sourcing, Licensing, Schema, and I.pdf`
(and its commissioning brief `temp/DOC-20260813-WA0027.pdf`). The research designates
abuse.ch (MalwareBazaar / ThreatFox / URLhaus) as the only reputable free-to-query
family covering Android with the indicator types Thraksha matches, and rules out
VirusTotal (ToS) and AndroZoo (no redistribution, academic gating).

---

## 1. Sources actually used

| Source | Endpoint | Acquisition method | Date | Auth |
|---|---|---|---|---|
| abuse.ch ThreatFox | `https://threatfox.abuse.ch/export/json/full/` | full JSON export (HTTP GET) | 2026-08-14 | none required for the export at acquisition time |
| abuse.ch MalwareBazaar | `https://bazaar.abuse.ch/export/csv/full/` | full CSV metadata export (HTTP GET) | 2026-08-14 | none required |
| abuse.ch MalwareBazaar CSCB | `https://bazaar.abuse.ch/export/csv/cscb/` | CSV export (HTTP GET) | 2026-08-14 | none required |
| abuse.ch URLhaus | `https://urlhaus.abuse.ch/downloads/csv_online/` | CSV export (HTTP GET) | 2026-08-14 | none required |

**What was downloaded: metadata only.** No malware sample, APK, archive or payload was
downloaded at any point. The MalwareBazaar *sample* endpoints (ZIPs, password
`infected`) were never touched; only hash/metadata CSV rows.

Curated intermediate inputs are archived under `temp/threat_corpus/`
(`threatfox_android.json`, `mb_apk.csv`, `cscb.csv`) so the shipped pack can be
regenerated and audited. The raw full dumps (≈219 MB / ≈3.5 MB zipped) are not kept in
the repo.

## 2. Indicator types used → shipped pack mapping

| Source data | ThreatPack type | Tier | Count shipped | Expiry |
|---|---|---|---|---|
| ThreatFox `sha256_hash` for `apk.*` families / Android tag | `BASE_APK_SHA256` | STRONG | 377 | 2028-08-08 (24 months) |
| MalwareBazaar rows with `file_type = apk` (deduplicated against ThreatFox, most recent first) | `BASE_APK_SHA256` | STRONG | 2,623 (cap: 3,000 total hashes) | 2028-08-08 (24 months) |
| MalwareBazaar CSCB SHA-256 thumbprints | `SIGNING_CERT_SHA256` | STRONG | 408 | 2028-08-08 (24 months) |
| ThreatFox `ip:port` for `apk.*` families | `DESTINATION_IP` (IPv4, port stripped; evaluated by the live Network Guard pillar) | CORROBORATING | 670 | 2026-11-12 (90 days) |
| ThreatFox `domain` / `url` / `md5` / `sha1` (346 records) | **NOT SHIPPED** | — | 0 | — |
| URLhaus URLs | **NOT SHIPPED** | — | 0 | — |
| Package names | **NOT SHIPPED** (none available from a legitimate source) | — | 0 real (1 demo) | — |

Reasons for the NOT-SHIPPED rows:

* **Domains/URLs**: no engine in this build evaluates DOMAIN/URL indicators (they are
  schema-representable FUTURE types). Shipping thousands of never-evaluated records
  would inflate the indicator count without adding one detection — count padding, not
  intelligence. They remain archived in `temp/threat_corpus/` for the future pillar.
* **Package names**: the research classifies package names as weak, spoofable
  corroboration, and no reputable source publishes a standalone Android package-name
  blocklist. Rather than fabricate one, the corpus ships zero real package-name
  indicators (targets are not quotas). The only PACKAGE_NAME record remains the
  controlled `demo-villain-package` indicator.
* **MD5/SHA-1**: weaker digests of the same samples; SHA-256 already shipped.

## 3. Per-indicator provenance carried in the pack

Every real-world indicator record carries, inside the signed pack itself:

* `classification`: `REAL_WORLD_THREAT` or `REAL_WORLD_NETWORK_IOC` — the parser
  **refuses the whole pack** if any `REAL_*` record lacks a `sourceReference`;
* `source`: `abuse.ch ThreatFox` / `abuse.ch MalwareBazaar` / `abuse.ch MalwareBazaar CSCB`;
* `sourceReference`: per-record origin URL (ThreatFox IOC reference URL /
  `https://bazaar.abuse.ch/sample/<sha256>/` / the CSCB export URL);
* `family`: the malware family exactly as labelled by the source (e.g. the source's
  `malware_printable` such as "ERMAC", "Hydra", "SpyNote"), never invented;
* `firstSeen` / `lastSeen`: source timestamps, normalized to ISO-8601 UTC;
* `expiresAt`: deterministic per-type expiry assigned at ingestion;
* `tier`: `STRONG` (digests) or `CORROBORATING` (network destinations).

## 4. Transformations performed

1. Filtered ThreatFox to records whose `malware` begins `apk.` or carrying the
   `android` tag (1,420 of 103,059 IOCs).
2. Filtered MalwareBazaar to `file_type == "apk"` rows (9,654 of 1,121,003).
3. Normalized digests to lowercase 64-hex; validated with `^[0-9a-f]{64}$`.
4. `ip:port` → IPv4 only; **rejected** private/loopback/link-local/multicast/reserved
   and documentation (TEST-NET) ranges so no real-world indicator can collide with
   local traffic or the controlled demo space (0 rejects occurred; the corpus was
   already routable).
5. Deduplication by natural key `(type, value)`; ThreatFox preferred over MalwareBazaar
   on collision (richer provenance; 8 cross-source duplicates).
6. Caps applied: 3,000 APK hashes (MalwareBazaar most-recent-first after all ThreatFox),
   500 certificates, 1,000 IPs.
7. Weight mapping (deterministic, documented): APK hash 95, vetted signer 90,
   network IP 70. Source-declared confidence is preserved verbatim in the description
   text; it is NOT presented as a probability.

## 5. Counts

| Stage | Count |
|---|---|
| ThreatFox Android IOCs considered | 1,420 |
| MalwareBazaar APK rows considered | 9,654 |
| CSCB certificate rows accepted | 408 (all SHA-256-thumbprint rows) |
| Rejected malformed | 0 |
| Rejected non-routable/reserved IPs | 0 |
| Cross-source duplicate hashes merged | 8 |
| Archived, not shipped (domain/url/md5/sha1) | 346 ThreatFox + all URLhaus |
| **Shipped real indicators** | **4,078** (3,000 APK + 408 cert + 670 IP) |
| Controlled demo indicators preserved | 8 |
| **Total pack** | **4,086 indicators, 2,175,986 bytes, packVersion 3** |

## 6. Licensing / redistribution caveat (IMPORTANT)

Per the research's A–G analysis: abuse.ch data is free to **query** under fair-use
terms, but the published terms are **silent on redistributing indicators inside a
shipped pack**, and commercial use "may require a paid subscription".

* This pack is used in a **privately side-loaded, non-commercial investor demo
  build**. It is a "curated real-world subset" in exactly the framing the research
  recommends for this stage.
* **Before any commercial distribution or fundraising claim built on shipping this
  data, written confirmation from Spamhaus/abuse.ch is required** (redistribution of
  an embedded subset + commercial use). This is a gating legal task, not a formality.
* Source attribution is carried on every indicator and shown in the UI evidence
  ("source: abuse.ch ThreatFox" etc.), which the research recommends as good practice.

## 7. Expiry rules (deterministic, enforced on-device)

* APK SHA-256 / signer SHA-256: `expiresAt` = acquisition + 24 months.
* DESTINATION_IP: `expiresAt` = acquisition + 90 days (research band 30–90 days;
  ThreatFox itself expires IOCs at 6 months).
* Whole pack: `expiresAt` = acquisition + 180 days → after 2027-02-10 the app shows
  **THREAT INTELLIGENCE STALE**, disables matching, and classifies apps as PARTIAL
  rather than silently claiming a clean scan.
* Expired indicators are **disabled for matching but retained** in the pack/history —
  expiry never deletes provenance.

## 8. CSCB caveat (documented deliberately)

The Code Signing Certificate Blocklist contains manually vetted certificates used to
sign malware — predominantly **Windows Authenticode** certificates. They are shipped
as `SIGNING_CERT_SHA256` indicators with that caveat in each record's description: the
expected Android match rate is ~zero, and that is honest — a match (a blocklisted
signing certificate appearing as an APK signer) would be genuine, reportable signal.
No public, redistributable, vetted **Android**-signer blocklist exists that could be
acquired without downloading samples (forbidden), so Android signer coverage is
limited to the controlled demo signer. Documented as a known limitation.
