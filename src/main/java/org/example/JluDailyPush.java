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
        // 1) 先找到“今日通知”这个标题所在的节点（支持 h1/h2/h3 或普通 div 文本）
        Element titleNode = doc.selectFirst("h1:matchesOwn(今日通知), h2:matchesOwn(今日通知), h3:matchesOwn(今日通知)");
        if (titleNode == null) {
            // 兜底：寻找任何含有“今日通知”纯文本的元素
            titleNode = doc.select("*:matchesOwn(^\\s*今日通知\\s*$)").first();
        }

        if (titleNode == null) {
            System.out.println("[WARN] 页面中未找到“今日通知”标题，尝试全局提取但排除侧栏。");
            return extractGloballyButExcludeSide(doc);
        }

        System.out.println("[INFO] 找到“今日通知”标题节点: <" + titleNode.tagName() + "> " + titleNode.text());

        // 2) 找标题所在的“板块容器”：通常是标题的父级或下一个兄弟容器
        Element section = titleNode.parent();
        // 如果父级很杂，优先用“标题后面的同级兄弟容器”作为板块容器
        Element sibling = titleNode.nextElementSibling();
        if (sibling != null && sibling.select("a").size() >= 1) {
            section = sibling;
        }

        // 3) 在这个板块容器下收集所有可能的条目链接
        //    只允许 /detail 或 ?id= 形态，且排除明显的侧边栏/导航容器
        Set<String> banAncestors = Set.of("nav", "aside");
        String[] banClasses = {"sidebar", "side", "left", "menu", "nav", "sider", "drawer"};

        LinkedHashMap<String, Item> map = new LinkedHashMap<>();
        for (Element a : section.select("a[href]")) {
            // 祖先黑名单（标签）
            boolean inBannedTag = a.parents().stream().anyMatch(p -> banAncestors.contains(p.tagName()));
            if (inBannedTag) continue;

            // 祖先黑名单（class 名字里包含 sidebar/left/menu 等）
            boolean inBannedClass = a.parents().stream().anyMatch(p -> {
                String cls = p.className().toLowerCase();
                for (String bad : banClasses) if (cls.contains(bad)) return true;
                return false;
            });
            if (inBannedClass) continue;

            String href = a.absUrl("href");
            String title = a.text().replaceAll("\\s+", " ").trim();

            // URL 规则：只保留详情类链接，避免工具/站外
            if (!(href.contains("/detail") || href.contains("?id="))) continue;

            if (title.isBlank() || title.length() < 4) continue;
            if (title.contains("更多") || title.contains("查看") ||
                    title.matches("^[\\p{Punct}\\d\\s]+$")) continue;

            map.putIfAbsent(href, new Item(title, href));
        }

        if (map.isEmpty()) {
            System.out.println("[WARN] “今日通知”板块未提取到条目，退回到全局提取但排除侧栏。");
            return extractGloballyButExcludeSide(doc);
        }

        System.out.println("[INFO] “今日通知”板块提取到条目数: " + map.size());
        return new ArrayList<>(map.values());
    }

    /** 兜底策略：全局找，但严格排除侧边栏容器，并要求链接像详情页 */
    private static List<Item> extractGloballyButExcludeSide(Document doc) {
        Set<String> banAncestors = Set.of("nav", "aside");
        String[] banClasses = {"sidebar", "side", "left", "menu", "nav", "sider", "drawer"};

        LinkedHashMap<String, Item> map = new LinkedHashMap<>();
        for (Element a : doc.select("a[href]")) {
            boolean inBannedTag = a.parents().stream().anyMatch(p -> banAncestors.contains(p.tagName()));
            if (inBannedTag) continue;

            boolean inBannedClass = a.parents().stream().anyMatch(p -> {
                String cls = p.className().toLowerCase();
                for (String bad : banClasses) if (cls.contains(bad)) return true;
                return false;
            });
            if (inBannedClass) continue;

            String href = a.absUrl("href");
            String title = a.text().replaceAll("\\s+", " ").trim();

            if (!(href.contains("/detail") || href.contains("?id="))) continue;
            if (title.isBlank() || title.length() < 4) continue;
            if (title.contains("更多") || title.contains("查看") ||
                    title.matches("^[\\p{Punct}\\d\\s]+$")) continue;

            map.putIfAbsent(href, new Item(title, href));
        }
        System.out.println("[INFO] 全局兜底提取条目数: " + map.size());
        return new ArrayList<>(map.values());
    }


    static class Item {
        final String title;
        final String href;
        Item(String t, String h) { this.title = t; this.href = h; }
    }
}


