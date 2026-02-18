# Performance Tests
These currently do not run in any CI pipeline, but they can be valuable for testing out changes before committing them.

## To run
If you're doing local development, you need to build the image:
```bash
docker build -t ghcr.io/dmaiken/konifer:latest .
```

You must start up the performance docker-compose. This can be done by going into the `/runtime` directory and running:
```bash
docker compose up -d
```
This docker-compose file picks up the `:latest` tag, so local changes will overwrite anything pulled down.

Run a performance test like so:

```bash
k6 run -e IMAGE_SIZE=small -e IMAGE_FORMAT=jpg --out web-dashboard=export=report.html eager-variant-upload.js 

```

Where:
- **`IMAGE_SIZE`**: small, medium or large
- **`IMAGE_FORMAT`**: any valid supported image format extension
- 