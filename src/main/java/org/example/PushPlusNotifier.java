package org.example;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;

public class PushPlusNotifier {
    private static final String TOKEN = System.getenv("PUSHPLUS_TOKEN");
    private static final String TOPIC = System.getenv("PUSHPLUS_TOPIC");

    public static void send(String title, String content) throws Exception {
        if (TOKEN == null || TOKEN.isEmpty()) {
            System.out.println("[WARN] 未配置 PUSHPLUS_TOKEN，跳过推送。");
            return;
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://www.pushplus.plus/send/");
            String json = "{"
                    + "\"token\":\"" + TOKEN + "\","
                    + "\"title\":\"" + escape(title) + "\","
                    + "\"content\":\"" + escape(content) + "\","
                    + "\"topic\":\"" + (TOPIC == null ? "" : TOPIC) + "\""
                    + "}";

            post.setEntity(EntityBuilder.create()
                    .setText(json)
                    .setContentType(ContentType.APPLICATION_JSON)
                    .build());

            var response = client.execute(post);
            System.out.println("[PushPlus] 响应: " + EntityUtils.toString(response.getEntity()));
        }
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
