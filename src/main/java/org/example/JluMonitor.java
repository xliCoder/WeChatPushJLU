package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.Iterator;

public class JluMonitor {

    private static final String API_URL = "https://jlu.oapush.com/api/news?page=1";

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("User-Agent", "Mozilla/5.0 (jlu-monitor)")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.out.println("[ERROR] 请求失败，HTTP状态码：" + response.statusCode());
            return;
        }

        // 解析 JSON 返回
        String body = response.body();
        JsonNode data = mapper.readTree(body).get("data");
        StringBuilder concat = new StringBuilder();

        if (data == null) {
            System.out.println("[WARN] 没有找到 data 字段，接口结构可能不同");
            return;
        }

        Iterator<JsonNode> it = data.elements();
        while (it.hasNext()) {
            JsonNode node = it.next();
            String title = node.get("title").asText();
            String date = node.has("publishTime") ? node.get("publishTime").asText() : "";
            concat.append(title).append(date);
        }

        String newHash = sha256(concat.toString());
        File hashFile = new File("last_hash.txt");
        String oldHash = hashFile.exists()
                ? new String(java.nio.file.Files.readAllBytes(hashFile.toPath()))
                : "";

        if (!newHash.equals(oldHash)) {
            System.out.println("[INFO] 检测到实质更新，调用智谱AI生成摘要...");
            String summary = ChatGPTClient.summarize(body);
            PushPlusNotifier.send("吉林大学通知更新", summary);
            try (FileWriter fw = new FileWriter(hashFile)) {
                fw.write(newHash);
            }
        } else {
            System.out.println("[INFO] 暂无新公告。");
        }
    }

    private static String sha256(String s) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(s.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
