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
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            return "⚠️ 未配置 OPENAI_API_KEY。";
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://api.openai.com/v1/chat/completions");
            post.setHeader("Authorization", "Bearer " + apiKey);
            post.setHeader("Content-Type", "application/json");

            // 用 Jackson 构造 JSON body（避免字符串转义问题）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一个简明的网页摘要生成助手，请用中文简要说明网页更新要点。"),
                    Map.of("role", "user", "content", content)
            ));

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(requestBody);

            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            var resp = client.execute(post);
            String body = EntityUtils.toString(resp.getEntity());
            int code = resp.getCode();

            if (code != 200) {
                System.out.println("Error:  OpenAI 请求失败: " + body);
                return "⚠️ OpenAI 请求失败：" + code;
            }

            // 提取 message.content
            int idx = body.indexOf("\"content\":\"");
            if (idx > 0) {
                String sub = body.substring(idx + 11);
                return sub.split("\"")[0]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"");
            } else {
                return "⚠️ 未能解析返回内容：" + body;
            }

        } catch (Exception e) {
            System.out.println("[ERROR] OpenAI 调用异常：" + e.getMessage());
            return "⚠️ OpenAI 调用异常：" + e.getClass().getSimpleName();
        }
    }
}


