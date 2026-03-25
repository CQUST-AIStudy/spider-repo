package com.tap.backend.rag;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DashScopeEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingClient.class);
    private static final String ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

    private final RagProperties props;
    private final Gson gson = new Gson();
    private final OkHttpClient httpClient;

    public DashScopeEmbeddingClient(RagProperties props) {
        this.props = props;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用 DashScope embedding API，将 query 文本转为向量。
     */
    public List<Float> embedQuery(String text) {
        if (props.dashscope() == null || props.dashscope().apiKey() == null || props.dashscope().apiKey().isBlank()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY is empty");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", props.dashscope().embeddingModel());
        body.addProperty("dimensions", props.dashscope().embeddingDimensions());

        JsonArray input = new JsonArray();
        input.add(text);
        body.add("input", input);

        RequestBody reqBody = RequestBody.create(
                body.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer " + props.dashscope().apiKey())
                .addHeader("Content-Type", "application/json")
                .post(reqBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "unknown";
                log.error("[DashScope Embedding] API error: {} {}", response.code(), errBody);
                throw new RuntimeException("DashScope embedding API failed: " + response.code());
            }

            String respStr = response.body().string();
            JsonObject respJson = JsonParser.parseString(respStr).getAsJsonObject();
            JsonArray data = respJson.getAsJsonArray("data");
            JsonArray embedding = data.get(0).getAsJsonObject().getAsJsonArray("embedding");

            List<Float> result = new ArrayList<>(embedding.size());
            for (int i = 0; i < embedding.size(); i++) {
                result.add(embedding.get(i).getAsFloat());
            }
            return result;
        } catch (IOException e) {
            log.error("[DashScope Embedding] request failed", e);
            throw new RuntimeException("DashScope embedding request failed", e);
        }
    }
}
