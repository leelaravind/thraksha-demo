#!/usr/bin/env python3
"""
build_threatpack.py — Phase 8 developer-side threat-intelligence pipeline.

DEVELOPER TOOL. Never compiled into the APK. The Android app consumes only the compact
signed output (threatpack.json/.sig/.key); no feed-ingestion code exists at runtime.

Pipeline (guide §31):
    source metadata -> parse -> normalize -> validate -> deduplicate -> apply expiry
    -> assign provenance -> merge controlled demo indicators -> generate ThreatPack
    -> sign (RSA-2048/SHA-256, existing key) -> verify -> statistics.

Sources (metadata/IOCs ONLY — no binaries are ever downloaded by this tool):
  * ThreatFox (abuse.ch) full JSON export, filtered to Android (apk.*) families:
    APK SHA-256 hashes and ip:port network indicators, with family, confidence,
    first/last seen and a per-IOC reference URL.
  * MalwareBazaar (abuse.ch) full CSV export, filtered to file_type "apk":
    APK SHA-256 hashes with family (signature) and first_seen.
  * MalwareBazaar Code Signing Certificate Blocklist (CSCB): manually vetted
    malicious code-signing certificate SHA-256 thumbprints (predominantly Windows
    Authenticode — shipped with that caveat documented; a blocklisted signing
    certificate appearing as an APK signer would still be reportable signal).

Licensing: abuse.ch data is free to query under fair use; REDISTRIBUTION IN A
COMMERCIAL PRODUCT REQUIRES WRITTEN CONFIRMATION from Spamhaus/abuse.ch. This pack is
a curated subset for a PRIVATE, NON-COMMERCIAL investor demo build. See
temp/THREAT_INTELLIGENCE_PROVENANCE.md.

Expiry policy (per the Phase 8 research):
  * apk_sha256 / cert_sha256 : acquisition + 24 months (long-lived artifacts)
  * DESTINATION_IP           : acquisition + 90 days (fast-decaying)
  * pack as a whole          : acquisition + 180 days (stale pack -> UI STALE state)

Usage:
  python tools/build_threatpack.py --intel-dir <dir with raw exports> [--dry-run]
"""

import argparse
import csv
import hashlib
import ipaddress
import json
import re
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
PRIVATE_KEY = REPO / "keys" / "threatpack_private.pem"
VILLAIN_APK = REPO / "villaincaller" / "build" / "outputs" / "apk" / "debug" / "villaincaller-debug.apk"
CORPUS_DIR = REPO / "temp" / "threat_corpus"

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

PACK_VERSION = 3
CAP_APK_HASHES = 3000
CAP_CERTS = 500
CAP_IPS = 1000

ACQUIRED = "2026-08-14"  # acquisition date (UTC) of the raw exports
EXP_HASH_MONTHS = 24
EXP_IP_DAYS = 90
EXP_PACK_DAYS = 180


def iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_source_ts(raw: str | None) -> str | None:
    """Normalize a source 'YYYY-MM-DD HH:MM:SS' timestamp to ISO-8601 Z, or None."""
    if not raw:
        return None
    raw = raw.strip()
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M:%S UTC", "%Y-%m-%dT%H:%M:%SZ"):
        try:
            return iso(datetime.strptime(raw, fmt).replace(tzinfo=timezone.utc))
        except ValueError:
            continue
    return None


def is_public_routable(ip: str) -> bool:
    try:
        addr = ipaddress.IPv4Address(ip)
    except ipaddress.AddressValueError:
        return False
    return not (
        addr.is_private or addr.is_loopback or addr.is_link_local or addr.is_multicast
        or addr.is_reserved or addr.is_unspecified
        # documentation ranges (TEST-NET-1/2/3) — the demo indicator space; never
        # shippable as "real" intelligence:
        or addr in ipaddress.IPv4Network("192.0.2.0/24")
        or addr in ipaddress.IPv4Network("198.51.100.0/24")
        or addr in ipaddress.IPv4Network("203.0.113.0/24")
        or addr in ipaddress.IPv4Network("100.64.0.0/10")
    )


def clean(cell: str) -> str:
    return cell.strip().strip('"').strip()


def load_threatfox(path: Path, stats: dict) -> tuple[list[dict], list[dict], int]:
    """Returns (apk hash indicators, destination-ip indicators, archived-not-shipped count)."""
    records = json.loads(path.read_text(encoding="utf-8"))
    hashes, ips = {}, {}
    archived = 0
    acquired_dt = datetime.strptime(ACQUIRED, "%Y-%m-%d").replace(tzinfo=timezone.utc)

    for r in records:
        ioc_type = r.get("ioc_type")
        ioc_id = r.get("id") or hashlib.sha256(
            (str(r.get("ioc_value")) + str(ioc_type)).encode()).hexdigest()[:10]
        family = r.get("malware_printable") or r.get("malware") or None
        confidence = r.get("confidence_level")
        first_seen = parse_source_ts(r.get("first_seen_utc"))
        last_seen = parse_source_ts(r.get("last_seen_utc"))
        ref = r.get("reference") or f"https://threatfox.abuse.ch/ioc/{r.get('id')}/"

        if ioc_type == "sha256_hash":
            value = clean(str(r.get("ioc_value"))).lower()
            if not SHA256_RE.match(value):
                stats["rejected_malformed"] += 1
                continue
            hashes[value] = {
                "id": f"tf-{r.get('id')}",
                "type": "BASE_APK_SHA256",
                "value": value,
                "classification": "REAL_WORLD_THREAT",
                "severity": "HIGH",
                "description": (
                    f"Android malware APK SHA-256 ({family or 'family unspecified'}); "
                    f"src confidence {confidence}. Metadata-only."
                ),
                "source": "abuse.ch ThreatFox",
                "enabled": True,
                "weight": 95,
                "sourceReference": ref,
                "family": family,
                "firstSeen": first_seen,
                "lastSeen": last_seen,
                "expiresAt": iso(acquired_dt + timedelta(days=30 * EXP_HASH_MONTHS)),
                "tier": "STRONG",
            }
        elif ioc_type == "ip:port":
            ip = clean(str(r.get("ioc_value"))).split(":")[0]
            if not is_public_routable(ip):
                stats["rejected_nonroutable"] += 1
                continue
            # keep the first (dedup by IP)
            ips.setdefault(ip, {
                "id": f"tfip-{r.get('id')}",
                "type": "DESTINATION_IP",
                "value": ip,
                "classification": "REAL_WORLD_NETWORK_IOC",
                "severity": "HIGH",
                "description": (
                    f"C2/distribution endpoint tied to Android malware "
                    f"({family or 'family unspecified'}). Decaying network indicator."
                ),
                "source": "abuse.ch ThreatFox",
                "enabled": True,
                "weight": 70,
                "sourceReference": ref,
                "family": family,
                "firstSeen": first_seen,
                "lastSeen": last_seen,
                "expiresAt": iso(acquired_dt + timedelta(days=EXP_IP_DAYS)),
                "tier": "CORROBORATING",
            })
        else:
            # domain / url / md5 / sha1: archived in temp/threat_corpus, NOT shipped —
            # no engine in this build evaluates them; shipping would be count padding.
            archived += 1

    return list(hashes.values()), list(ips.values()), archived


def load_malwarebazaar(path: Path, stats: dict) -> list[dict]:
    acquired_dt = datetime.strptime(ACQUIRED, "%Y-%m-%d").replace(tzinfo=timezone.utc)
    out = {}
    with path.open(encoding="utf-8") as f:
        for row in csv.reader(f):
            if len(row) < 9:
                stats["rejected_malformed"] += 1
                continue
            first_seen = parse_source_ts(clean(row[0]))
            value = clean(row[1]).lower()
            family = clean(row[8])
            if family in ("n/a", ""):
                family = None
            if not SHA256_RE.match(value):
                stats["rejected_malformed"] += 1
                continue
            out[value] = {
                "id": f"mb-{value[:12]}",
                "type": "BASE_APK_SHA256",
                "value": value,
                "classification": "REAL_WORLD_THREAT",
                "severity": "HIGH",
                "description": (
                    f"Confirmed Android malware APK SHA-256 "
                    f"({family or 'family unspecified'}). Metadata-only."
                ),
                "source": "abuse.ch MalwareBazaar",
                "enabled": True,
                "weight": 95,
                "sourceReference": f"https://bazaar.abuse.ch/sample/{value}/",
                "family": family,
                "firstSeen": first_seen,
                "lastSeen": None,
                "expiresAt": iso(acquired_dt + timedelta(days=30 * EXP_HASH_MONTHS)),
                "tier": "STRONG",
            }
    return list(out.values())


def load_cscb(path: Path, stats: dict) -> list[dict]:
    acquired_dt = datetime.strptime(ACQUIRED, "%Y-%m-%d").replace(tzinfo=timezone.utc)
    out = {}
    with path.open(encoding="utf-8") as f:
        rows = [r for r in csv.reader(l for l in f if not l.startswith("#")) if r]
    header = [clean(c) for c in rows[0]]
    for row in rows[1:]:
        if len(row) != len(header):
            stats["rejected_malformed"] += 1
            continue
        rec = dict(zip(header, (clean(c) for c in row)))
        thumb = rec.get("thumbprint", "").lower().replace(":", "")
        algo = rec.get("thumbprint_algorithm", "").upper()
        reason = rec.get("Reason") or rec.get("cscb_reason") or None
        subject = rec.get("subject_cn")
        if algo not in ("SHA256", "SHA-256") or not SHA256_RE.match(thumb):
            stats["rejected_malformed"] += 1
            continue
        out[thumb] = {
            "id": f"cscb-{thumb[:12]}",
            "type": "SIGNING_CERT_SHA256",
            "value": thumb,
            "classification": "REAL_WORLD_THREAT",
            "severity": "HIGH",
            "description": (
                f"Vetted malicious code-signing certificate (CN '{subject}', "
                f"reason {reason or 'unspecified'}); CSCB is predominantly Windows "
                f"Authenticode — reportable if seen as an APK signer."
            ),
            "source": "abuse.ch MalwareBazaar CSCB",
            "enabled": True,
            "weight": 90,
            "sourceReference": "https://bazaar.abuse.ch/export/csv/cscb/",
            "family": reason,
            "firstSeen": parse_source_ts(rec.get("time_stamp_utc")),
            "lastSeen": None,
            "expiresAt": iso(acquired_dt + timedelta(days=30 * EXP_HASH_MONTHS)),
            "tier": "STRONG",
        }
    return list(out.values())


def demo_indicators(existing_pack: dict) -> list[dict]:
    """Preserve the controlled demo indicators exactly; re-verify the villain APK hash."""
    demo = [i for i in existing_pack["indicators"] if i["id"].startswith("demo-")]
    if VILLAIN_APK.exists():
        digest = hashlib.sha256(VILLAIN_APK.read_bytes()).hexdigest()
        for ind in demo:
            if ind["id"] == "demo-villain-base-apk" and ind["value"] != digest:
                print(f"WARNING: villaincaller APK digest changed "
                      f"({ind['value'][:12]}… -> {digest[:12]}…); updating indicator. "
                      f"REINSTALL villaincaller on all devices.")
                ind["value"] = digest
    else:
        print("NOTE: villaincaller-debug.apk not built; keeping existing demo hash.")
    return demo


def validate(indicators: list[dict]) -> None:
    """Mirror of the on-device ThreatPackParser rules — fail the BUILD, not the device."""
    ids = [i["id"] for i in indicators]
    assert len(ids) == len(set(ids)), "duplicate indicator ids"
    for i in indicators:
        assert i["id"], "blank id"
        assert 0 <= i["weight"] <= 100, f"{i['id']}: bad weight"
        if i["type"] in ("BASE_APK_SHA256", "SIGNING_CERT_SHA256"):
            assert SHA256_RE.match(i["value"]), f"{i['id']}: bad sha256"
        if i["type"] == "DESTINATION_IP":
            ipaddress.IPv4Address(i["value"])
        if i["classification"].startswith("REAL_"):
            assert i.get("sourceReference"), f"{i['id']}: REAL_* without sourceReference"
        natural = [j for j in indicators
                   if j["type"] == i["type"] and j["value"] == i["value"]]
        assert len(natural) == 1, f"duplicate (type,value): {i['type']} {i['value'][:20]}"


def sign(json_path: Path, sig_path: Path, pub_path: Path) -> None:
    raw_sig = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", str(PRIVATE_KEY), str(json_path)],
        check=True, capture_output=True).stdout
    import base64
    sig_path.write_text(base64.b64encode(raw_sig).decode(), newline="")
    der = subprocess.run(
        ["openssl", "rsa", "-in", str(PRIVATE_KEY), "-pubout", "-outform", "DER"],
        check=True, capture_output=True).stdout
    pub_path.write_text(base64.b64encode(der).decode(), newline="")

    # verify exactly the way the app will
    pem = subprocess.run(
        ["openssl", "rsa", "-in", str(PRIVATE_KEY), "-pubout"],
        check=True, capture_output=True).stdout
    import tempfile, os
    with tempfile.TemporaryDirectory() as td:
        pem_f = Path(td) / "pub.pem"
        sig_f = Path(td) / "sig.bin"
        pem_f.write_bytes(pem)
        sig_f.write_bytes(raw_sig)
        subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(pem_f),
             "-signature", str(sig_f), str(json_path)],
            check=True, capture_output=True)
    print("Signature verified over the exact output bytes.")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--intel-dir", required=True, type=Path)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    stats = {"rejected_malformed": 0, "rejected_nonroutable": 0}
    acquired_dt = datetime.strptime(ACQUIRED, "%Y-%m-%d").replace(tzinfo=timezone.utc)

    existing = json.loads((ASSETS / "threatpack.json").read_text(encoding="utf-8"))
    demo = demo_indicators(existing)

    tf_hashes, tf_ips, tf_archived = load_threatfox(
        args.intel_dir / "threatfox_android.json", stats)
    mb_hashes = load_malwarebazaar(args.intel_dir / "mb_apk.csv", stats)
    certs = load_cscb(args.intel_dir / "cscb.csv", stats)

    # Deduplicate hashes across sources: ThreatFox wins (richer provenance), then MB
    # most-recent-first up to the cap.
    tf_values = {h["value"] for h in tf_hashes}
    mb_unique = [h for h in mb_hashes if h["value"] not in tf_values]
    mb_unique.sort(key=lambda h: h.get("firstSeen") or "", reverse=True)
    dup_cross = len(mb_hashes) - len(mb_unique)
    hashes = (tf_hashes + mb_unique)[:CAP_APK_HASHES]
    certs = certs[:CAP_CERTS]
    ips = tf_ips[:CAP_IPS]

    indicators = demo + hashes + certs + ips
    validate(indicators)

    pack = {
        "schemaVersion": 1,
        "packVersion": PACK_VERSION,
        "generatedAt": iso(datetime.now(timezone.utc)),
        "expiresAt": iso(acquired_dt + timedelta(days=EXP_PACK_DAYS)),
        "indicators": indicators,
    }
    # compact but line-per-indicator for reviewability; drop null optional fields
    for ind in pack["indicators"]:
        for k in list(ind.keys()):
            if ind[k] is None:
                del ind[k]

    out = json.dumps(pack, indent=None, separators=(",", ":"), ensure_ascii=False)
    print(f"--- corpus statistics ---")
    print(f"ThreatFox android IOCs: {len(tf_hashes)} apk hashes, {len(tf_ips)} usable IPs, "
          f"{tf_archived} archived (domain/url/md5/sha1 — no evaluator, not shipped)")
    print(f"MalwareBazaar apk hashes: {len(mb_hashes)} ({dup_cross} duplicate with ThreatFox)")
    print(f"CSCB certificates: {len(certs)}")
    print(f"Rejected malformed: {stats['rejected_malformed']}, "
          f"non-routable/reserved IPs: {stats['rejected_nonroutable']}")
    print(f"Shipped: {len(hashes)} APK hashes + {len(certs)} certs + {len(ips)} IPs "
          f"+ {len(demo)} demo = {len(indicators)} indicators")
    print(f"Pack v{PACK_VERSION}, expires {pack['expiresAt']}, "
          f"size {len(out.encode('utf-8'))} bytes")

    if args.dry_run:
        print("(dry run — nothing written)")
        return

    json_path = ASSETS / "threatpack.json"
    json_path.write_text(out, encoding="utf-8", newline="")
    sign(json_path, ASSETS / "threatpack.sig", ASSETS / "threatpack_public.key")
    print(f"Wrote {json_path} + signature + public key. Rebuild the app:")
    print("  ./gradlew :app:assembleDebug")


if __name__ == "__main__":
    main()
