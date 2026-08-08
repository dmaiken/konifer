# Konifer Performance

The interactive performance dashboard renders release history from
[`../history/releases.json`](../history/releases.json). It includes headline
workflow charts, a selectable p50/p95 release history, and exact result tables.

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
updates only the underlying history document; it does not regenerate HTML.
The generated browser modules live under `performance/dist/` and are ignored
by Git.
