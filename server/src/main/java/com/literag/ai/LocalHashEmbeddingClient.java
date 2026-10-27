package com.literag.ai;

import com.literag.entity.ModelConfig;
import com.literag.vector.VectorUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 零依赖的本地 embedding：字符 / 词粒度特征哈希（feature hashing）。
 * 属于词面相似度模型，不依赖任何外部服务，用于离线演示与冒烟测试；
 * 生产环境请切换到 OpenAI 兼容接口或 Ollama 的语义向量模型。
 */
@Component
public class LocalHashEmbeddingClient implements EmbeddingClient {

    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]+|[a-z0-9_]+");
    private static final int DEFAULT_DIM = 256;

    @Override
    public String provider() {
        return ModelConfig.PROVIDER_LOCAL;
    }

    @Override
    public List<float[]> embed(List<String> texts, ModelConfig cfg) {
        int dim = cfg.getDimension() != null && cfg.getDimension() > 0 ? cfg.getDimension() : DEFAULT_DIM;
        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(embedOne(text, dim));
        }
        return out;
    }

    private float[] embedOne(String text, int dim) {
        float[] vec = new float[dim];
        if (text == null || text.isBlank()) {
            return vec;
        }
        Map<String, Double> features = new HashMap<>();
        Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String token = m.group();
            if (isHan(token)) {
                for (int i = 0; i < token.length(); i++) {
                    features.merge(String.valueOf(token.charAt(i)), 1.0, Double::sum);
                    if (i + 1 < token.length()) {
                        features.merge(token.substring(i, i + 2), 1.6, Double::sum);
                    }
                }
            } else if (token.length() > 1) {
                features.merge(token, 1.0, Double::sum);
            }
        }
        features.forEach((feature, tf) -> {
            int h = fnv(feature);
            int index = (h & 0x7fffffff) % dim;
            float sign = ((h >>> 7) & 1) == 0 ? 1f : -1f;
            vec[index] += (float) (sign * (1 + Math.log(tf)));
        });
        return VectorUtils.normalize(vec);
    }

    private boolean isHan(String s) {
        return s.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private int fnv(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }
}
