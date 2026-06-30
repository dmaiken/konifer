#!/usr/bin/env python3
"""
Extract SigLIP/SigLIP2 logit calibration parameters for split ONNX deployments.

When text and vision towers are exported separately, the combined model's learned
logit_scale and logit_bias parameters are usually not present in either ONNX file.
This script writes those parameters to a small JSON sidecar file.

Examples:
  python3 scripts/extract-siglip2-calibration.py \
    --model-id google/siglip2-base-patch16-224 \
    --output siglip2-calibration.json

  python3 scripts/extract-siglip2-calibration.py \
    --safetensors ./model.safetensors \
    --output siglip2-calibration.json
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract SigLIP/SigLIP2 logit_scale and logit_bias to JSON.",
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument(
        "--model-id",
        help="Hugging Face model id, for example google/siglip2-base-patch16-224.",
    )
    source.add_argument(
        "--safetensors",
        type=Path,
        help="Path to a local model.safetensors file.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("siglip2-calibration.json"),
        help="Output JSON path. Defaults to ./siglip2-calibration.json.",
    )
    parser.add_argument(
        "--revision",
        help="Optional Hugging Face model revision to load with --model-id.",
    )
    return parser.parse_args()


def scalar(value: Any) -> float:
    if hasattr(value, "detach"):
        value = value.detach()
    if hasattr(value, "cpu"):
        value = value.cpu()
    if hasattr(value, "item"):
        return float(value.item())
    return float(value)


def extract_from_model_id(model_id: str, revision: str | None) -> tuple[float, float]:
    try:
        from transformers import AutoModel
    except ImportError as e:
        raise SystemExit(
            "Missing dependency: transformers. Install with: pip install transformers torch"
        ) from e

    model = AutoModel.from_pretrained(model_id, revision=revision)
    return scalar(model.logit_scale), scalar(model.logit_bias)


def extract_from_safetensors(path: Path) -> tuple[float, float]:
    try:
        from safetensors.torch import load_file
    except ImportError as e:
        raise SystemExit(
            "Missing dependency: safetensors. Install with: pip install safetensors torch"
        ) from e

    state = load_file(str(path))
    missing = [key for key in ("logit_scale", "logit_bias") if key not in state]
    if missing:
        raise SystemExit(
            f"{path} does not contain required parameter(s): {', '.join(missing)}"
        )
    return scalar(state["logit_scale"]), scalar(state["logit_bias"])


def write_output(
    output: Path,
    logit_scale: float,
    logit_bias: float,
    source: dict[str, str | None],
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "format": "konifer-siglip2-calibration-v1",
        "source": source,
        "logitScale": logit_scale,
        "logitBias": logit_bias,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def main() -> None:
    args = parse_args()

    if args.model_id:
        logit_scale, logit_bias = extract_from_model_id(args.model_id, args.revision)
        source = {"type": "huggingface", "modelId": args.model_id, "revision": args.revision}
    else:
        logit_scale, logit_bias = extract_from_safetensors(args.safetensors)
        source = {"type": "safetensors", "path": str(args.safetensors), "revision": None}

    write_output(
        output=args.output,
        logit_scale=logit_scale,
        logit_bias=logit_bias,
        source=source,
    )
    print(f"Wrote {args.output}")
    print(f"logitScale={logit_scale}")
    print(f"logitBias={logit_bias}")


if __name__ == "__main__":
    main()
