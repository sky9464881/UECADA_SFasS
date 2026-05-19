# Model Files

FastAPI loads the default fault classification model from:

```text
ai-api/app/models/model.pkl
```

You can override the path and version with environment variables:

```bash
FAULT_MODEL_PATH=app/models/model.pkl
<<<<<<< HEAD
FAULT_MODEL_VERSION=spectrogram-pca-rf-v1
FAULT_MODEL_INPUT_TYPE=spectrogram
FAULT_MODEL_SPECTROGRAM_SIZE=64
=======
FAULT_MODEL_VERSION=spectrogram-pca-rf-v2
FAULT_MODEL_INPUT_TYPE=spectrogram
FAULT_MODEL_SPECTROGRAM_SIZE=64
FAULT_MODEL_SPECTROGRAM_LOG_TRANSFORM=false
FAULT_MODEL_SPECTROGRAM_MAX_NORMALIZATION=false
FAULT_MODEL_SPECTROGRAM_MAX_NORMALIZATION_EPS=0.00000001
>>>>>>> feature/develop_before
FAULT_MODEL_STFT_NPERSEG=256
FAULT_MODEL_STFT_NOVERLAP=128
```

<<<<<<< HEAD
The current model expects a flattened 64x64 spectrogram vector. Prefer storing the model as a `joblib` artifact dictionary:
=======
The current model expects a flattened 64x64 spectrogram vector. The v2 artifact carries its own preprocessing metadata, including per-window max normalization. Prefer storing the model as a `joblib` artifact dictionary:
>>>>>>> feature/develop_before

```python
artifact = {
    "model": pipeline,
<<<<<<< HEAD
    "model_version": "spectrogram-pca-rf-v1",
    "input_type": "spectrogram",
=======
    "model_version": "spectrogram-pca-rf-v2",
    "model_input_type": "spectrogram_64x64_flattened",
>>>>>>> feature/develop_before
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
<<<<<<< HEAD
=======
    "post_spectrogram_preprocess": {
        "log_transform": False,
        "per_window_max_normalization": True,
        "per_window_max_normalization_eps": 1e-8,
    },
>>>>>>> feature/develop_before
    "class_names": list(pipeline.classes_),
}
```

FastAPI still supports a plain sklearn pipeline, but the artifact form is easier to replace safely because the preprocessing contract travels with the model file.
