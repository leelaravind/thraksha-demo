# PHASE 12A — NETWORK EGRESS AUDIT

## Headline: the release APK contains no HTTP stack at all

`strings` over `classes.dex`, `classes2.dex`, `classes3.dex` finds **zero** occurrences of
`okhttp3`, `retrofit2` or `com.google.firebase`. The build declares no analytics,
crash-reporting, advertising or attribution SDK. There is no `WebView` in the manifest or
the code (`meminfo` on the running app reports `WebViews: 0`).

This makes the local-AI "no cloud fallback" claim **structural** rather than a matter of
configuration: there is no client in the APK with which to call a cloud service.

## Complete inventory of network-capable production code

A search of the whole production source set for `HttpURLConnection`, `OkHttp`, `Retrofit`,
`URL(`, `openConnection`, `Socket(`, `DatagramSocket`, `WebView`, `loadUrl`, `ktor` and
`.connect(` returns exactly **one** file:

| Path | Mechanism | Classification |
| --- | --- | --- |
| `services/ThrakshaVpnService.kt` (`java.net.DatagramSocket`, line 189) | Outbound-only UDP forwarder inside the Network Guard tunnel | **REQUIRED — user-initiated, local security functionality** |

### What that one path does

Network Guard is a `VpnService` scoped to a **single** package. When the monitored app
emits a UDP packet, Thraksha reads it off the TUN interface, parses destination metadata,
evaluates it against the signed threat pack, and then either drops it or forwards it to the
destination the monitored app already chose:

```kotlin
DatagramSocket().use { socket ->
    check(protect(socket)) { "VpnService.protect(socket) failed" }
    …
}
```

`VpnService.protect()` keeps the forwarding socket **outside** the tunnel so it cannot loop
back. Thraksha originates no traffic of its own here — it relays the monitored app's packet,
or refuses to. The tunnel exists only while the user has explicitly enabled Network Guard
and granted the Android VPN consent dialog.

## Local AI — verified to have no network path

Searching `app/src/main/java/com/thraksha/guardian/ai/` for any network or download
capability returns a single hit, and it is the **opposite** of an egress path:
`RequestScopeGuard.kt:60` lists `"intent://", "http://", "https://", "content://",
"file://", "market://"` as URI schemes the guard **refuses** in model output.

The model is loaded from a local file and nowhere else:

```kotlin
fun modelFile(context: Context): File =
    File(context.applicationContext.getExternalFilesDir("models"), MODEL_FILE_NAME)
```

`LocalModelRepository` checks `file.exists()` and verifies SHA-256. There is no download
path, no update channel, no remote inference client, and no fallback of any kind. If the
model is missing, the app reports "not installed" and the deterministic routines continue —
it does not reach for a network alternative because none exists.

## Confirmed absent from runtime operation

| Endpoint class | Present? |
| --- | --- |
| Stitch / MCP endpoints | **no** — Stitch appears only in source comments attributing the design system |
| Claude / Anthropic endpoints | **no** |
| Google model-development or model-download endpoints | **no** |
| Telemetry / analytics / crash reporting | **no** |
| Licence or update check | **no** |

## Permissions supporting this

* `INTERNET` — consumed solely by the Network Guard forwarder above.
* `ACCESS_NETWORK_STATE` — used to report guard status honestly rather than claiming
  monitoring without connectivity.

Both are justified in `PHASE12_MANIFEST_PERMISSION_AUDIT.md` §1.

## Residual note

The app holds `INTERNET`, so a future code change could introduce egress without a new
permission prompt. For this candidate the guarantee rests on the audit above — one socket,
in one file, inside a user-enabled VPN service. A future build intended for distribution
should keep this inventory as a regression check.
