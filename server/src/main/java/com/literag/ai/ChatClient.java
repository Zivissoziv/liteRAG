package com.literag.ai;

import com.literag.entity.ModelConfig;

public interface ChatClient {

    String provider();

    String chat(ModelConfig cfg, String system, String user, double temperature);
}
