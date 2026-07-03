# Scripts

## Extract SigLIP2 Calibration

`extract-siglip2-calibration.py` writes the learned `logitScale` and `logitBias`
parameters needed when Konifer uses split SigLIP/SigLIP2 text and vision ONNX
models.

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
