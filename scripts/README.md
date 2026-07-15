# Scripts

## Download SigLIP2 Models

`download-siglip2-models.sh` downloads only the runtime model artifacts Konifer
needs for SigLIP2 rule evaluation. It does not install Python dependencies and
does not download the original safetensors model.

```bash
scripts/download-siglip2-models.sh
```

By default this writes:

```text
models/siglip2-base-patch16-224/
  vision_model.onnx
  text_model.onnx
  tokenizer.json
```

Use `HF_TOKEN` if you need higher Hugging Face rate limits:

```bash
HF_TOKEN=... scripts/download-siglip2-models.sh
```

Use a pinned revision for reproducible local/prod model packs:

```bash
scripts/download-siglip2-models.sh \
  --revision ba1f3b0843f24bc5417d38e19c37b287d719b2f4
```

Mount the generated directory into Docker read-only:

```yaml
volumes:
  - ./models/siglip2-base-patch16-224:/app/models/siglip2-base-patch16-224:ro
```

## Extract SigLIP2 Calibration

`extract-siglip2-calibration.py` writes the learned `logitScale` and `logitBias`
parameters needed when Konifer uses split SigLIP/SigLIP2 text and vision ONNX
models. This is not needed for normal local setup if the calibration values are
hardcoded in the service.

Create a virtual environment (if desired):

```bash
python3 -m venv venv
source venv/bin/activate
```

Install dependencies (this may take a while - these are large dependencies):

```bash
pip install transformers torch safetensors
```

From Hugging Face (model is ~1.5 GB):

```bash
python3 extract-siglip2-calibration.py \
  --model-id google/siglip2-base-patch16-224 \
  --output siglip2-calibration.json
```

From a local `model.safetensors`:

```bash
python3 scripts/extract-siglip2-calibration.py \
  --safetensors ./model.safetensors \
  --output siglip2-calibration.json
```

The output file contains the raw learned `logitScale` value. Konifer applies
`exp(logitScale)` at scoring time.
