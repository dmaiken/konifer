# Konifer Performance

The dashboard reads isolated benchmark and mixed-load history from
[`releases.json`](../history/releases.json) and [`loads.json`](../history/loads.json).
Choose a release/environment, then use the Benchmark latency or Mixed load tab.
For test commands and AWS collection, see the [harness README](../README.md).

Serve the dashboard over HTTP so the browser can fetch history JSON. Opening
[`index.html`](index.html) through `file://` blocks those requests. From the
repository root:

```bash
npm --prefix performance run build
python3 -m http.server 8000
```

Then open <http://localhost:8000/performance/report/>.

Publishing results updates history documents without changing dashboard source
or the GitHub Pages URL. The bare URL opens the newest release's Benchmark
latency tab. Share selections through the URL, for example:

```text
?release=v0.10.0&environment=local-compose-v2&profile=mixed-v1#load
```

The environment description stays visible; expand its panel for hardware and
interpretation notes. Git ignores generated browser modules and Geist fonts
under `performance/dist/`.

The dashboard compares p95 against the prior passing result in the same series.
It labels a change only at differences of at least 5% and 2 ms. Smaller changes
show `Below threshold`; missing baselines show `No prior result`. Failed results
remain visible with unavailable latency and do not enter chart/comparison baselines.

`npm --prefix performance run build -- --without-load` stages a benchmark-only
site for GitHub Pages. The local preview retains both tabs; see
[dashboard builds](../BENCHMARKING.md#dashboard-builds).

## Annotating a release

Add an optional `notes` array to a release or result in
[`releases.json`](../history/releases.json). Release notes appear above the
selected release's results; result notes appear in the matching table row:

```json
{
  "subject": "v0.10.0",
  "notes": ["Native image dependencies were rebuilt from source."],
  "results": [
    {
      "workload": "format.encode",
      "case": "jpg-to-jxl",
      "notes": ["JXL encode p95 changed after the native dependency upgrade; see the release notes."]
    }
  ]
}
```

The example omits other required fields. Regenerating the same run preserves
existing notes.

Load runs and streams accept `notes` in [`loads.json`](../history/loads.json).
Regenerating the same subject/environment/profile preserves those notes too.
