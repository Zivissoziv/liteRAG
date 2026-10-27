package com.literag.ai;

import com.literag.entity.ModelConfig;

import java.util.List;

public interface EmbeddingClient {

    String provider();

    List<float[]> embed(List<String> texts, ModelConfig cfg);
}
