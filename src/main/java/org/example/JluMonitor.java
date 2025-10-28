package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class JluMonitor {

    private static final String LIST_URL = "https://jlu.oapush.com/"; // 或具体公告页
    private static final String HASH_FILE = "last_hash.txt";
    private static final String SEEN_FILE = "seen_links.txt";

    public static void main(String[] args) {
        try {
            Document doc = Jsoup.connect(LIST_URL)
                    .userAgent("Mozilla/5.0 (jlu-monitor)")
                    .timeout(10000)
                    .get();

            // 尝试多种可能的选择器
            Elements elems = doc.select("ul.news-list li a, .news-list a, li.notice-item a");
            List<NewsItem> items = new ArrayList<>();

            for (Element a : elems) {
                String title = a.text().trim();
                String href = a.absUrl("href");
                if (title.isEmpty() || href.isEmpty()) continue;

                // 尝试找到日期
                String date = "";
                Element parent = a.parent();
                if (parent != null) {
                    Element dateElem = parent.selectFirst("span.date, time, .date");
                    if (dateElem != null) date = dateElem.text().trim();
                }

                items.add(new NewsItem(title, href, date));
            }

            // 按链接排序、去重
            LinkedHashMap<String, NewsItem> map = new LinkedHashMap<>();
            for (NewsItem it : items) {
                if (!map.containsKey(it.href())) {
                    map.put(it.href(), it);
                }
            }
            List<NewsItem> cleanList = new ArrayList<>(map.values());

            // 构建内容串用于哈希
            StringBuilder sb = new StringBuilder();
            for (NewsItem it : cleanList) {
                sb.append(it.title()).append("|").append(it.href()).append("|").append(it.date()).append("\n");
            }

            String currentHash = sha256(sb.toString());
            String oldHash = readFile(HASH_FILE);

            if (!currentHash.equals(oldHash)) {
                System.out.println("[INFO] 检测到可能更新，处理...");
                String summary = ChatGPTClient.summarize(sb.toString());
                PushPlusNotifier.send("吉林大学通知更新", summary);
                writeFile(HASH_FILE, currentHash);
            } else {
                System.out.println("[INFO] 暂无新公告。");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String readFile(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeFile(String path, String content) {
        try (FileWriter fw = new FileWriter(path)) {
            fw.write(content);
        } catch (IOException ignored) {}
    }

    record NewsItem(String title, String href, String date) {}
}

