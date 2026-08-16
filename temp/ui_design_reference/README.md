# Stitch design reference — local implementation aid

Retrieved 2026-08-15 from the approved Stitch project `projects/11183523763892934666`
("Thraksha Guardian Security Hub") via the Stitch MCP server. Nothing here is compiled,
packaged or read at runtime.

```
00_index/     inventory, screen → real-state mapping, implementation status
html/         each approved screen's exported Tailwind HTML (32 files)
screenshots/  stitch_*.png  — the Stitch renders
              s20fe_*.png   — real Samsung S20 FE captures of the implementation
```

## Handling notes

* **Not app content.** No file here is referenced by `app/`, ships in the APK, or is a
  Gradle input. Verified by scanning the built APK.
* **No credentials.** Nothing here contains a Stitch API key, MCP credential or endpoint
  secret. The MCP key is supplied to `tools/stitch_mcp_proxy.py` through the
  `STITCH_API_KEY` environment variable only, and the short-lived signed export URLs used
  to fetch these assets were deliberately kept out of every document in this directory.
* **Third-party content.** The exported HTML embeds `lh3.googleusercontent.com` image URLs
  for Stitch-generated artwork (the TR monogram and app-icon mockups). That artwork is
  **not used** — `ui/components/BrandingComponents.kt` remains the authoritative brand.
* **Committing.** `temp/` is currently untracked and not in `.gitignore`. Decide before
  any commit whether these design exports should be version-controlled or ignored; Phase
  11B committed nothing.

## Rebuilding this directory

Re-run the Stitch MCP retrieval (`list_projects` → `list_screens`) and download each
screen's `htmlCode` and `screenshot` file entries. The proxy under `tools/` exists only
because of an upstream Stitch MCP schema defect and is development-only.
