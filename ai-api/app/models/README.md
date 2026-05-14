# Model Files

FastAPI loads the default fault classification model from:

```text
ai-api/app/models/model.pkl
```

You can override the path and version with environment variables:

```bash
FAULT_MODEL_PATH=app/models/model.pkl
FAULT_MODEL_VERSION=spectrogram-pca-rf-v1
FAULT_MODEL_INPUT_TYPE=spectrogram
FAULT_MODEL_SPECTROGRAM_SIZE=64
FAULT_MODEL_STFT_NPERSEG=256
FAULT_MODEL_STFT_NOVERLAP=128
```

The current model expects a flattened 64x64 spectrogram vector. Prefer storing the model as a `joblib` artifact dictionary:

```python
artifact = {
    "model": pipeline,
    "model_version": "spectrogram-pca-rf-v1",
    "input_type": "spectrogram",
    "sampling_rate": 16000,
    "window_seconds": 2.0,
    "window_size": 32000,
    "spectrogram_shape": [64, 64],
    "stft_params": {
        "window": "hann",
        "nperseg": 256,
        "noverlap": 128,
        "detrend": False,
        "scaling": "spectrum",
        "mode": "magnitude",
    },
    "class_names": list(pipeline.classes_),
}
```

FastAPI still supports a plain sklearn pipeline, but the artifact form is easier to replace safely because the preprocessing contract travels with the model file.
