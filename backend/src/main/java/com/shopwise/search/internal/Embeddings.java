package com.shopwise.search.internal;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Wraps the embedding model. nomic-embed-text expects task prefixes on its inputs.
 */
@Component
class Embeddings {

    private static final Logger log = LoggerFactory.getLogger(Embeddings.class);

    private final EmbeddingModel model;

    Embeddings(EmbeddingModel model) {
        this.model = model;
    }

    float[] document(String text) {
        return model.embed("search_document: " + text);
    }

    Optional<float[]> query(String text) {
        try {
            return Optional.of(model.embed("search_query: " + text));
        }
        catch (RuntimeException e) {
            log.warn("Embedding model unavailable, using full-text search only: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static String toLiteral(float[] vector) {
        var sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
