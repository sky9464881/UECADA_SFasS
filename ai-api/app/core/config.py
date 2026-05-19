from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "Smart Factory Vibration AI API"

    # Database
    database_url: str = "mysql+pymysql://uecada_user:uecada1234@localhost:8600/uecada"

    # Fault model
    fault_model_path: str = "app/models/model.pkl"
<<<<<<< HEAD
    fault_model_version: str = "spectrogram-pca-rf-v1"
    fault_model_input_type: str = "spectrogram"
    fault_model_spectrogram_size: int = 64
=======
    fault_model_version: str = "spectrogram-pca-rf-v2"
    fault_model_input_type: str = "spectrogram"
    fault_model_spectrogram_size: int = 64
    fault_model_spectrogram_log_transform: bool = False
    fault_model_spectrogram_max_normalization: bool = False
    fault_model_spectrogram_max_normalization_eps: float = 1e-8
>>>>>>> feature/develop_before
    fault_model_stft_window: str = "hann"
    fault_model_stft_nperseg: int = 256
    fault_model_stft_noverlap: int = 128
    fault_model_stft_scaling: str = "spectrum"
    fault_model_stft_mode: str = "magnitude"
    fault_model_stft_detrend: bool = False
    fft_max_bins: int = 2048
<<<<<<< HEAD
=======
    raw_window_save_interval_minutes: int = 10
>>>>>>> feature/develop_before


settings = Settings()
