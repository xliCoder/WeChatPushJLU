// src/main/java/org/example/PushPlusNotifier.java
package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.util.HashMap;
import java.util.Map;

public class PushPlusNotifier {
    public static void send(String title, String content) {
        String token = System.getenv("PUSHPLUS_TOKEN");
        String topic = System.getenv("PUSHPLUS_TOPIC"); // 可选
        if (token == null || token.isEmpty()) {
            System.out.println("[WARN] 未配置 PUSHPLUS_TOKEN，跳过推送。");
            return;
        }
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://www.pushplus.plus/send");
            post.setHeader("Content-Type", "application/json;charset=utf-8");
            Map<String, Object> body = new HashMap<>();
            body.put("token", token);
            body.put("title", title);
            body.put("content", content);
            if (topic != null && !topic.isBlank()) body.put("topic", topic);

            String json = new ObjectMapper().writeValueAsString(body);
            post.setEntity(new StringEntity(json, ContentType.parse("utf-8")));
            var resp = client.execute(post);
            String respBody = EntityUtils.toString(resp.getEntity());
            System.out.println("[PushPlus] 响应: " + respBody);
        } catch (Exception e) {
            System.out.println("[ERROR] PushPlus 调用异常：" + e.getMessage());
        }
    }
}

