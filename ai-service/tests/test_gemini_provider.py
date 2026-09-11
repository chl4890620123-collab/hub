from app.providers.gemini import GeminiProvider


def test_gemini_38_body_omits_deprecated_sampling_parameters():
    body = GeminiProvider.request_body("hello")
    config = body["generationConfig"]
    assert config == {"responseMimeType": "application/json"}
    assert "temperature" not in config
    assert "top_p" not in config
    assert "top_k" not in config
    assert "candidate_count" not in config
