// src/main/java/org/example/ChatGPTClient.java
package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatGPTClient {

    public static String summarize(String content) {
        String apiKey = System.getenv("ZHIPUAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            return "⚠️ 未配置 ZHIPUAI_API_KEY。";
        }
        if (content == null || content.trim().isEmpty()) {
            return "📌 没抓到可摘要的内容。";
        }
        String safe = content.length() > 4000 ? content.substring(0, 4000) : content;

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://open.bigmodel.cn/api/paas/v4/chat/completions");
            post.setHeader("Authorization", "Bearer " + apiKey);
            post.setHeader("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<>();
            body.put("model", "GLM-4-Flash-250414"); // ✅ 免费模型
            body.put("temperature", 0.4);
            body.put("max_tokens", 1024);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system",
                            "content", List.of(Map.of("type","text","text",
                                    "你是高校网站更新摘要助手。输出简洁中文要点列表，不要冗余客套。"))),
                    Map.of("role", "user",
                            "content", List.of(Map.of("type","text","text", safe)))
            ));

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(body);
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            var resp = client.execute(post);
            String respBody = EntityUtils.toString(resp.getEntity());
            if (resp.getCode() != 200) {
                System.out.println("Error: 智谱AI 请求失败: " + respBody);
                return "⚠️ 智谱AI 请求失败：" + resp.getCode();
            }

            // 极简抽取（稳定起见可换成 Jackson 真解析）
            int i = respBody.indexOf("\"content\":\"");
            if (i > 0) {
                String sub = respBody.substring(i + 11);
                return sub.split("\"")[0].replace("\\n", "\n").replace("\\\"", "\"");
            }
            return "⚠️ 未能解析智谱AI返回内容。";
        } catch (Exception e) {
            System.out.println("[ERROR] 智谱AI 调用异常：" + e.getMessage());
            return "⚠️ 智谱AI 调用异常：" + e.getClass().getSimpleName();
        }
    }
}
