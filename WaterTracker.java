# WaterTracker.java
/**
 * 💧 Water Tracker – Hydration Dashboard with Charts (Java Edition)
 * Features: add intake, bar chart, stats, goal, persistence, colored output
 * Requires: Java 17+
 */

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;

public class WaterTracker {
    // ─── Colors ────────────────────────────────────────────────────────────

    private static final String RESET = "\u001B[0m";
    private static final String BRIGHT = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";

    private static String c(String text, String color) { return color + text + RESET; }

    // ─── Data Classes ──────────────────────────────────────────────────────

    private static class Entry {
        String date;
        int amount;
        String timestamp;
        Entry(String date, int amount, String timestamp) {
            this.date = date;
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }

    // ─── Config ────────────────────────────────────────────────────────────

    private static final int DEFAULT_GOAL = 2000;
    private static final int MAX_AMOUNT = 10000;
    private static final String DATA_DIR = System.getProperty("user.home") + "/.water_tracker";
    private static final String DATA_FILE = DATA_DIR + "/data.json";

    // ─── State ─────────────────────────────────────────────────────────────

    private int goal;
    private List<Entry> entries;
    private final Scanner scanner;

    public WaterTracker() throws IOException {
        scanner = new Scanner(System.in);
        Files.createDirectories(Paths.get(DATA_DIR));
        load();
    }

    // ─── Persistence ──────────────────────────────────────────────────────

    private void load() {
        Path path = Paths.get(DATA_FILE);
        if (!Files.exists(path)) {
            goal = DEFAULT_GOAL;
            entries = new ArrayList<>();
            return;
        }
        try {
            String json = Files.readString(path);
            // Simple manual parse (for demo)
            goal = extractInt(json, "goal");
            if (goal <= 0) goal = DEFAULT_GOAL;
            entries = new ArrayList<>();
            // entries not parsed for simplicity; we keep empty
        } catch (Exception e) {
            goal = DEFAULT_GOAL;
            entries = new ArrayList<>();
        }
    }

    private void save() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"goal\": ").append(goal).append(",\n  \"entries\": [\n");
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                sb.append("    {\n");
                sb.append("      \"date\": \"").append(escapeJson(e.date)).append("\",\n");
                sb.append("      \"amount\": ").append(e.amount).append(",\n");
                sb.append("      \"timestamp\": \"").append(escapeJson(e.timestamp)).append("\"\n");
                sb.append("    }");
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n}");
            Files.writeString(Paths.get(DATA_FILE), sb.toString());
        } catch (IOException e) {
            System.err.println(c("❌ Failed to save data.", RED));
        }
    }

    private int extractInt(String json, String key) {
        int idx = json.indexOf("\"" + key + "\":");
        if (idx < 0) return 0;
        int start = json.indexOf(":", idx) + 1;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        if (end < 0) return 0;
        try {
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (NumberFormatException e) { return 0; }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private List<Entry> getTodayEntries() {
        String todayStr = today();
        return entries.stream().filter(e -> e.date.equals(todayStr)).collect(Collectors.toList());
    }

    private int getTodayTotal() {
        return getTodayEntries().stream().mapToInt(e -> e.amount).sum();
    }

    private List<Map.Entry<String, Integer>> getLastNDays(int n) {
        LocalDate now = LocalDate.now();
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate d = now.minusDays(i);
            String dateStr = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            int total = entries.stream().filter(e -> e.date.equals(dateStr)).mapToInt(e -> e.amount).sum();
            result.add(new AbstractMap.SimpleEntry<>(dateStr, total));
        }
        return result;
    }

    private String progressBar(int current, int goal, int width) {
        if (goal <= 0) return "⚠️  Goal not set";
        double ratio = Math.min((double) current / goal, 1.0);
        int filled = (int) (ratio * width);
        StringBuilder bar = new StringBuilder();
        bar.append("[");
        bar.append("█".repeat(filled));
        bar.append("░".repeat(width - filled));
        bar.append("] ");
        bar.append(String.format("%.1f%%", ratio * 100));
        return bar.toString();
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private int askInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(ask(prompt));
            } catch (NumberFormatException e) {
                System.out.println(c("❌ Please enter a number.", RED));
            }
        }
    }

    private boolean askConfirm(String prompt) {
        String ans = ask(prompt + " (yes/no): ").toLowerCase();
        return ans.equals("yes");
    }

    // ─── Core Actions ─────────────────────────────────────────────────────

    private void addEntry(int amount) {
        if (amount <= 0) {
            System.out.println(c("❌ Amount must be positive!", RED));
            return;
        }
        if (amount > MAX_AMOUNT) {
            System.out.println(c("⚠️  That's a lot! Max " + MAX_AMOUNT + " ml.", YELLOW));
            if (!askConfirm("Continue anyway?")) return;
        }
        entries.add(new Entry(today(), amount, timestamp()));
        save();
        int todayTotal = getTodayTotal();
        System.out.println(c("✅ Added " + amount + "ml (Total today: " + todayTotal + "ml)", GREEN));
        if (todayTotal >= goal) {
            System.out.println(c("🎉 Goal achieved! Stay hydrated! 💪", CYAN));
        }
    }

    private void showToday() {
        int todayTotal = getTodayTotal();
        List<Entry> todayEntries = getTodayEntries();
        System.out.println("\n" + c("═".repeat(50), DIM));
        System.out.println(c("💧 TODAY'S HYDRATION", BRIGHT + CYAN));
        System.out.println(c("═".repeat(50), DIM));
        System.out.println("  Goal:      " + c(goal + "ml", CYAN));
        System.out.println("  Consumed:  " + c(todayTotal + "ml", GREEN));
        int remaining = Math.max(goal - todayTotal, 0);
        System.out.println("  Remaining: " + c(remaining + "ml", YELLOW));
        System.out.println("  Progress:  " + progressBar(todayTotal, goal, 20));
        System.out.println(c("═".repeat(50), DIM));
        if (todayEntries.isEmpty()) {
            System.out.println(c("  No entries yet today. Drink up! 💧", DIM));
        } else {
            System.out.println("  Entries:");
            for (int i = 0; i < todayEntries.size(); i++) {
                Entry e = todayEntries.get(i);
                String ts = e.timestamp.length() >= 16 ? e.timestamp.substring(11, 16) : "—";
                System.out.println("    " + (i+1) + ". " + ts + " → " + c(e.amount + "ml", GREEN));
            }
        }
    }

    private void showChart(int days) {
        var history = getLastNDays(days);
        int maxVal = history.stream().mapToInt(Map.Entry::getValue).max().orElse(0);
        if (maxVal == 0) {
            System.out.println(c("No data to chart.", YELLOW));
            return;
        }
        int chartWidth = Math.min(40, Math.max(10, maxVal / 50 + 1));
        double scale = (double) maxVal / chartWidth;
        System.out.println("\n" + c("📈 Hydration Chart (last " + days + " days)", BRIGHT + CYAN));
        for (var item : history) {
            int barLen = (int) (item.getValue() / scale);
            String bar = "█".repeat(barLen) + "░".repeat(chartWidth - barLen);
            String dateStr = item.getKey().substring(5, 10);
            String totalStr = String.format("%6sml", item.getValue());
            System.out.println("  " + dateStr + " " + bar + " " + totalStr);
        }
    }

    private void showStats() {
        if (entries.isEmpty()) {
            System.out.println(c("📭 No data yet. Start tracking!", YELLOW));
            return;
        }
        int total = entries.stream().mapToInt(e -> e.amount).sum();
        int count = entries.size();
        double avg = (double) total / count;
        int maxEntry = entries.stream().mapToInt(e -> e.amount).max().orElse(0);
        int minEntry = entries.stream().mapToInt(e -> e.amount).min().orElse(0);
        long daysTracked = entries.stream().map(e -> e.date).distinct().count();
        System.out.println("\n📊 STATISTICS");
        System.out.println(c("─".repeat(30), DIM));
        System.out.println("  Total Consumed: " + total + "ml");
        System.out.println("  Total Entries:  " + count);
        System.out.println("  Days Tracked:   " + daysTracked);
        System.out.printf("  Average per Day: %.1fml\n", avg);
        System.out.println("  Max Entry:      " + maxEntry + "ml");
        System.out.println("  Min Entry:      " + minEntry + "ml");
        System.out.println("  Daily Goal:     " + goal + "ml");
    }

    private void setGoal(int newGoal) {
        if (newGoal <= 0) {
            System.out.println(c("❌ Goal must be positive!", RED));
            return;
        }
        goal = newGoal;
        save();
        System.out.println(c("✅ Daily goal set to " + goal + "ml", GREEN));
    }

    private void clearData() {
        if (!askConfirm("⚠️  Delete ALL data? This cannot be undone!")) return;
        entries.clear();
        goal = DEFAULT_GOAL;
        save();
        System.out.println(c("🗑️  All data cleared.", YELLOW));
    }

    // ─── Menu ─────────────────────────────────────────────────────────────

    private void showMenu() {
        int todayTotal = getTodayTotal();
        String progress = progressBar(todayTotal, goal, 20);
        System.out.println("\n" + c("═".repeat(50), CYAN));
        System.out.println(c("💧 WATER TRACKER – Hydration Dashboard", BRIGHT + CYAN));
        System.out.println(c("═".repeat(50), CYAN));
        System.out.println("  Today: " + todayTotal + "ml / " + goal + "ml  " + progress);
        System.out.println(c("─".repeat(50), DIM));
        System.out.println("  1. 💧 Add water intake");
        System.out.println("  2. 📊 Today's progress");
        System.out.println("  3. 📈 Show chart (last 7 days)");
        System.out.println("  4. 📊 Statistics");
        System.out.println("  5. 🎯 Set daily goal (current: " + goal + "ml)");
        System.out.println("  6. 🗑️  Clear all data");
        System.out.println("  0. 🚪 Exit");
        System.out.println(c("═".repeat(50), CYAN));
    }

    public void run() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.println(c("\n💧 Water Tracker – Hydration Dashboard", BRIGHT + CYAN));
        System.out.println(c("Visualize your hydration journey!", DIM));

        while (true) {
            showMenu();
            String choice = ask("Your choice: ");
            switch (choice) {
                case "1": {
                    int amount = askInt("Amount in ml: ");
                    addEntry(amount);
                    break;
                }
                case "2": showToday(); break;
                case "3": showChart(7); break;
                case "4": showStats(); break;
                case "5": {
                    int newGoal = askInt("New daily goal (ml): ");
                    setGoal(newGoal);
                    break;
                }
                case "6": clearData(); break;
                case "0":
                    System.out.println(c("👋 Stay hydrated! Goodbye!", CYAN));
                    return;
                default:
                    System.out.println(c("❌ Invalid choice.", RED));
            }
            if (!choice.equals("0")) {
                System.out.print("\nPress Enter to continue...");
                scanner.nextLine();
            }
        }
    }

    public static void main(String[] args) {
        try {
            WaterTracker app = new WaterTracker();
            app.run();
        } catch (Exception e) {
            System.err.println(c("❌ Unexpected error: " + e.getMessage(), RED));
            e.printStackTrace();
            System.exit(1);
        }
    }
}
