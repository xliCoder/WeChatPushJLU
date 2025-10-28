package org.example;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

public class ChatGPTClient {

    public static String summarize(String content) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("[WARN] 未配置 OPENAI_API_KEY，将返回本地摘要。");
            return "📢 本地提示：未配置 OpenAI API Key，暂不生成智能摘要。";
        }

        String url = "https://api.openai.com/v1/chat/completions";
        String json = """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [
                    {"role": "system", "content": "你是一个简明的网页摘要生成助手，请用中文简要说明网页更新要点。"},
                    {"role": "user", "content": "%s"}
                  ]
                }
                """.formatted(content);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Authorization", "Bearer " + apiKey);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                String body = EntityUtils.toString(resp.getEntity());
                int statusCode = resp.getCode();
                if (statusCode != 200) {
                    System.out.println("[ERROR] OpenAI 请求失败: " + body);
                    return "⚠️ OpenAI 请求失败：" + statusCode;
                }

                // 简单提取返回的内容
                int idx = body.indexOf("\"content\":\"");
                if (idx > 0) {
                    String sub = body.substring(idx + 11);
                    return sub.split("\"")[0]
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"");
                } else {
                    return "⚠️ 未能解析返回内容：" + body;
                }
            }

        } catch (Exception e) {
            System.out.println("[ERROR] OpenAI 调用异常：" + e.getMessage());
            return "⚠️ OpenAI 调用异常：" + e.getClass().getSimpleName();
        }
    }
}

