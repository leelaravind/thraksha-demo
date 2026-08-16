# PHASE 13 — RESOURCE PROFILE

Guide reference: §13. Device A (S20 FE, 7.44 GiB RAM). Build: frozen RC2, release.

## Memory — measured

| State | TOTAL PSS | TOTAL RSS | Native Heap | Note |
| --- | --- | --- | --- | --- |
| App running, **model not loaded** | **108,543 kB ≈ 106 MB** | 221,660 kB ≈ 216 MB | 13,520 kB | idle UI, post-launch |
| App running, **model loaded** | **3,058,602 kB ≈ 2.92 GB** | 3,147,448 kB ≈ 3.00 GB | 1,261,500 kB ≈ 1.20 GB | after first inference |
| Delta attributable to the model | **≈ +2.81 GB PSS** | ≈ +2.79 GB | ≈ +1.19 GB | mmap'd 2.41 GiB model |

Source: `dumpsys meminfo com.thraksha.guardian`.

### Assessment

* **Idle footprint (~106 MB) is modest** and appropriate for a security app that is mostly
  waiting.
* **Loaded footprint (~2.92 GB) is large but structural** — it is the on-device LLM, not a
  leak, and the Phase 12 memory-pressure fix keeps it stable rather than growing.
* **Headroom warning:** 2.92 GB is **~39% of Device A's 7.44 GiB**. On a 6 GB device this
  becomes marginal and on 4 GB it is not viable — Android would aggressively kill the
  process. RC2's `minSdk 26` admits far smaller devices than it can actually serve well.
  **Untested below 7.44 GiB.**

## Thermal — measured (light load only)

| Point | `dumpsys thermalservice` |
| --- | --- |
| Session start | **Thermal Status: 0** (NONE) |
| After repeated inference | **Thermal Status: 0** (NONE) |

No throttling observed. ⚠ This covers only a handful of inferences over minutes. **No
sustained-load thermal test was run**, so nothing is established about back-to-back
inference, long scans, or Network Guard running for hours.

## Scan cost — measured

| Metric | Value |
| --- | --- |
| Full scan, 441 packages | **13.6 s** (first launch) / **16.4 s** (post-reboot) |
| Throughput | ≈ **32 packages/s** including APK fingerprinting |
| Stalls / partial states | none |

## Stability — measured

| Check | Result |
| --- | --- |
| Crashes (`FATAL EXCEPTION`) | **0** |
| ANRs | **0** |
| Dropbox entries for the package | **0** |
| Behaviour under malformed input | graceful — garbled prompt still produced a correct plan |
| Behaviour when driven out of the app by faulty tooling | app was left running and healthy; no corruption |

## NOT measured — honest gaps

| Guide §13 item | Status |
| --- | --- |
| **Battery trend / drain** | ⛔ **not measured.** Requires hours of wall-clock use on an uninstrumented, unplugged phone. The device was on USB power throughout, which makes any `batterystats` figure meaningless. |
| Idle-app background drain | ⛔ not measured |
| Network Guard resource cost | ⛔ Network Guard was never activated (see §12 gap) |
| Active-routine resource cost | ⚠ only a ~1 min Meeting run; no sustained measurement |
| Repeated-AI sustained profile | ⛔ suite did not complete |
| Background-service behaviour over time | ⛔ not measured |
| Memory on smaller-RAM devices | ⛔ no such device available |

**Battery is the headline gap.** For an always-available security app, battery cost is one of
the first things a real user judges, and Phase 13 §13 asks for it explicitly. It cannot be
obtained from a USB-tethered session — it needs the §14 pilot.

## Device B — S24 Ultra

⛔ **NOT MEASURED — device not connected.** No newer-SoC memory, thermal or battery data
exists.
