package com.shopwise;

import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic bag-of-words embedding for tests: texts that share words end up close.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 768;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var results = new java.util.ArrayList<Embedding>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            results.add(new Embedding(vectorFor(inputs.get(i)), i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    static float[] vectorFor(String text) {
        var vector = new float[DIMENSIONS];
        var cleaned = text.toLowerCase(Locale.ROOT).replaceAll("^search_(query|document): ", "");
        for (String word : cleaned.split("[^a-z0-9]+")) {
            if (word.length() > 2) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            vector[0] = 1f;
            return vector;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
