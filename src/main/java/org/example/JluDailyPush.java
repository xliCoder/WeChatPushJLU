// src/main/java/org/example/JluDailyPush.java
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

    // 可配置的候选选择器（尽量覆盖常见公告列表）
    private static final String[] CANDIDATE_SELECTORS = new String[]{
            ".news-list a", "ul li a", ".list a", "a[href*=/detail/]", "a[href*=?id=]"
    };

    public static void main(String[] args) {
        try {
            // 1) 抓当天列表（站点习惯仅显示当天）
            Document doc = Jsoup.connect(LIST_URL)
                    .userAgent("Mozilla/5.0 (jlu-monitor/1.0)")
                    .timeout(10000)
                    .get();

            List<Item> items = extractItems(doc);

            String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE); // 仅用于标题展示
            String plainList = items.isEmpty()
                    ? "今日页面未发现可识别的条目（可能今天确实无更新或选择器需要微调）。"
                    : items.stream()
                    .map(i -> "• " + i.title + "\n" + i.href)
                    .collect(Collectors.joining("\n\n"));

            // 2) 调智谱做一个简要摘要（失败就回退到原文列表）
            String aiInput = items.isEmpty()
                    ? "今日未抓到任何条目，请用一句话说明“暂无更新”。"
                    : "请用简洁中文概括以下今日新增条目要点，每条不超过20字，保留关键信息：\n\n" + plainList;

            String summary = ChatGPTClient.summarize(aiInput);
            if (summary == null || summary.startsWith("⚠️") || summary.startsWith("📌")) {
                summary = "【自动摘要不可用】直接列出抓取结果：\n\n" + plainList;
            }

            String title = "吉林大学今日更新 · " + today;
            String content = summary + "\n\n—— 系统时间点推送（14:00/20:00）";
            PushPlusNotifier.send(title, content);
            System.out.println("[SUCCESS] 已推送 " + items.size() + " 条（不做对比，按时推送）。");
        } catch (Exception e) {
            e.printStackTrace();
            PushPlusNotifier.send("吉林大学今日更新（异常）", "⚠️ 运行异常：" + e.getMessage());
        }
    }

    private static List<Item> extractItems(Document doc) {
        // 多选择器兜底；去重；过滤“更多/查看”等短标题
        LinkedHashMap<String, Item> map = new LinkedHashMap<>();
        for (String sel : CANDIDATE_SELECTORS) {
            for (Element a : doc.select(sel)) {
                String href = a.absUrl("href");
                String title = a.text().replaceAll("\\s+", " ").trim();
                if (href.isBlank() || title.isBlank() || title.length() < 4) continue;
                if (title.contains("更多") || title.contains("查看") || title.matches("^[\\p{Punct}\\d\\s]+$")) continue;
                map.putIfAbsent(href, new Item(title, href));
            }
            if (!map.isEmpty()) break; // 任何一个选择器命中就用
        }
        return new ArrayList<>(map.values());
    }

    static class Item {
        final String title;
        final String href;
        Item(String t, String h) { this.title = t; this.href = h; }
    }
}

