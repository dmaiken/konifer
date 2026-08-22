# Konifer Performance

The interactive performance dashboard renders isolated benchmark and mixed-load
history from [`../history/releases.json`](../history/releases.json) and
[`../history/loads.json`](../history/loads.json). It separates the two measurement
styles into Benchmark latency and Mixed load tabs under one release-aware page.

The dashboard must be served over HTTP because browsers do not allow its JSON
request when [`index.html`](index.html) is opened directly with `file://`.
Compile the TypeScript-backed browser modules and, from the repository root,
preview the dashboard with:

```bash
npm --prefix performance run build
python3 -m http.server 8000
```

Then open <http://localhost:8000/performance/report/>.

The HTML, CSS, and TypeScript are stable source files. Publishing a release
updates only the underlying history documents; it does not regenerate HTML or
change the GitHub Pages URL. The bare URL automatically selects the newest
release and opens Benchmark latency. Selections are shareable, for example:

```text
?release=v0.10.0&environment=local-compose-v2&profile=mixed-v1#load
```

The selected environment description is always visible, while hardware and
interpretation details are expandable. The generated browser modules and
locally served Geist font assets live under `performance/dist/` and are ignored
by Git.

The dashboard retains all measurements in history, but labels a p95 change only
when it is both at least 5% and at least 2 ms. Smaller movements are displayed
as `No significant change` instead of an attention-grabbing percentage.

## Annotating a release

Add an optional `notes` array to a release or to one of its results in
[`../history/releases.json`](../history/releases.json). Release notes appear at
the top of the dashboard when that release is latest. Result notes appear with
the corresponding headline card and table row. For example:

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

The other required release and result fields are omitted from the example.
Notes are preserved if the same run is regenerated.

Mixed-load runs and individual traffic streams accept the same optional
`notes` array in [`../history/loads.json`](../history/loads.json). These notes
are also preserved when the same subject, environment, and profile are
regenerated.
