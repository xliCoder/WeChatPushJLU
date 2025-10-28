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

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://open.bigmodel.cn/api/paas/v4/chat/completions");
            post.setHeader("Authorization", "Bearer " + apiKey);
            post.setHeader("Content-Type", "application/json");

            // 构造 JSON 请求体
            Map<String, Object> body = new HashMap<>();
            body.put("model", "glm-4.6");
            body.put("temperature", 0.4);
            body.put("max_tokens", 2048);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一个网页摘要助手，请用简洁中文总结网站更新的主要内容。"),
                    Map.of("role", "user", "content", content)
            ));

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(body);
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            var resp = client.execute(post);
            String respBody = EntityUtils.toString(resp.getEntity());
            int code = resp.getCode();

            if (code != 200) {
                System.out.println("Error: 智谱AI 请求失败: " + respBody);
                return "⚠️ 智谱AI 请求失败：" + code;
            }

            // 简单提取返回文本
            int idx = respBody.indexOf("\"content\":\"");
            if (idx > 0) {
                String sub = respBody.substring(idx + 11);
                return sub.split("\"")[0]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"");
            } else {
                return "⚠️ 未能解析智谱AI返回内容：" + respBody;
            }

        } catch (Exception e) {
            System.out.println("[ERROR] 智谱AI 调用异常：" + e.getMessage());
            return "⚠️ 智谱AI 调用异常：" + e.getClass().getSimpleName();
        }
    }
}
