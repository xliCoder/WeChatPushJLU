package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class JluMonitor {
    private static final String URL = "https://jlu.oapush.com/";
    private static final String HASH_FILE = "last_hash.txt";

    public static void main(String[] args) {
        try {
            // 抓取网页
            String html = WebFetcher.fetch(URL);
            String newHash = md5(html);
            String oldHash = readLastHash();

            boolean updated = !newHash.equals(oldHash);
            String summary;

            if (updated) {
                System.out.println("[INFO] 检测到更新，开始生成摘要...");
                summary = ChatGPTClient.summarize(html.substring(0, Math.min(html.length(), 2000)));
                saveHash(newHash);
            } else {
                System.out.println("[INFO] 暂无更新，但仍推送通知...");
                summary = "📢 吉林大学通知助手：今日暂无新通知，但系统运行正常。";
            }

            // 推送摘要（无论是否更新）
            PushPlusNotifier.send("吉林大学通知更新", summary);
            System.out.println("[SUCCESS] 已推送摘要内容:\n" + summary);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String md5(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String readLastHash() {
        File f = new File(HASH_FILE);
        if (!f.exists()) return "";
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        } catch (IOException e) {
            return "";
        }
    }

    private static void saveHash(String hash) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(HASH_FILE))) {
            bw.write(hash);
        }
    }
}


