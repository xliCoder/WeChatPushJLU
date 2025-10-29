package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class JluDailyPush {

    private static final String LIST_URL = "https://jlu.oapush.com/";

    // 候选选择器（可自行添加/删减）
    private static final String[] CANDIDATE_SELECTORS = new String[]{
            ".news-list a", "ul li a", ".list a", "a[href*=/detail/]", "a[href*=?id=]"
    };

    public static void main(String[] args) {
        try {
            System.out.println("============================================");
            System.out.println("[INFO] 启动吉林大学每日抓取任务...");
            System.out.println("[INFO] 目标页面: " + LIST_URL);

            // 1️⃣ 抓取网页
            Document doc = Jsoup.connect(LIST_URL)
                    .userAgent("Mozilla/5.0 (jlu-monitor/1.0)")
                    .timeout(10000)
                    .get();

            // 2️⃣ 提取信息
            List<Item> items = extractItems(doc);
            System.out.println("[INFO] 抓取到的有效条目数量: " + items.size());
            for (int i = 0; i < items.size(); i++) {
                System.out.printf("   #%d  标题: %s%n       链接: %s%n",
                        i + 1, items.get(i).title, items.get(i).href);
            }

            String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            String plainList = items.isEmpty()
                    ? "今日页面未发现可识别的条目（可能网站今天无公告或选择器不匹配）。"
                    : items.stream()
                    .map(i -> "• " + i.title + "\n" + i.href)
                    .collect(Collectors.joining("\n\n"));

            // 3️⃣ 构造大模型输入
            String aiInput = items.isEmpty()
                    ? "今日未抓到任何条目，请用一句话说明“暂无更新”。"
                    : "请用简洁中文概括以下今日新增条目要点，每条不超过20字，保留关键信息：\n\n" + plainList;

            System.out.println("============================================");
            System.out.println("[DEBUG] 发送给智谱AI的 prompt 长度: " + aiInput.length());
            System.out.println("[DEBUG] prompt 预览前 300 字:");
            System.out.println(aiInput.substring(0, Math.min(aiInput.length(), 300)));
            System.out.println("============================================");

            // 4️⃣ 调用智谱AI
            String summary = ChatGPTClient.summarize(aiInput);

            if (summary == null || summary.isBlank() ||
                    summary.startsWith("⚠️") || summary.startsWith("📌")) {
                summary = "【自动摘要不可用】直接列出抓取结果：\n\n" + plainList;
            }

            // 5️⃣ 推送
            String title = "吉林大学今日更新 · " + today;
            String content = summary + "\n\n—— 系统时间点推送（14:00/20:00）";

            PushPlusNotifier.send(title, content);
            System.out.println("[SUCCESS] 已推送 " + items.size() + " 条内容。");

        } catch (Exception e) {
            e.printStackTrace();
            PushPlusNotifier.send("吉林大学今日更新（异常）", "⚠️ 运行异常：" + e.getMessage());
        }
    }

    private static List<Item> extractItems(Document doc) {
        LinkedHashMap<String, Item> map = new LinkedHashMap<>();
        for (String sel : CANDIDATE_SELECTORS) {
            var list = doc.select(sel);
            if (!list.isEmpty()) {
                System.out.println("[INFO] 选择器命中: " + sel + " -> " + list.size() + " 条");
            }
            for (Element a : list) {
                String href = a.absUrl("href");
                String title = a.text().replaceAll("\\s+", " ").trim();
                if (href.isBlank() || title.isBlank() || title.length() < 4) continue;
                if (title.contains("更多") || title.contains("查看") ||
                        title.matches("^[\\p{Punct}\\d\\s]+$")) continue;
                map.putIfAbsent(href, new Item(title, href));
            }
        }
        if (map.isEmpty()) {
            System.out.println("[WARN] 所有候选选择器均未命中内容。");
        }
        return new ArrayList<>(map.values());
    }

    static class Item {
        final String title;
        final String href;
        Item(String t, String h) { this.title = t; this.href = h; }
    }
}


