# WaterTracker.cs
/**
 * 💧 Water Tracker – Hydration Dashboard with Charts (C# Edition)
 * Features: add intake, bar chart, stats, goal, persistence, colored output
 * Requires: .NET 6.0+
 */

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;

class WaterTracker
{
    // ─── Colors ────────────────────────────────────────────────────────────

    private static readonly string Reset = "\u001B[0m";
    private static readonly string Bright = "\u001B[1m";
    private static readonly string Dim = "\u001B[2m";
    private static readonly string Red = "\u001B[31m";
    private static readonly string Green = "\u001B[32m";
    private static readonly string Yellow = "\u001B[33m";
    private static readonly string Blue = "\u001B[34m";
    private static readonly string Magenta = "\u001B[35m";
    private static readonly string Cyan = "\u001B[36m";

    private static string C(string text, string color) => color + text + Reset;

    // ─── Data Classes ──────────────────────────────────────────────────────

    public class Entry
    {
        [JsonPropertyName("date")]
        public string Date { get; set; } = "";
        [JsonPropertyName("amount")]
        public int Amount { get; set; }
        [JsonPropertyName("timestamp")]
        public string Timestamp { get; set; } = "";
    }

    public class Data
    {
        [JsonPropertyName("goal")]
        public int Goal { get; set; } = 2000;
        [JsonPropertyName("entries")]
        public List<Entry> Entries { get; set; } = new();
    }

    // ─── Config ────────────────────────────────────────────────────────────

    private const int DefaultGoal = 2000;
    private const int MaxAmount = 10000;
    private static readonly string DataDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
        ".water_tracker"
    );
    private static readonly string DataFile = Path.Combine(DataDir, "data.json");

    // ─── State ─────────────────────────────────────────────────────────────

    private int goal;
    private List<Entry> entries;

    public WaterTracker()
    {
        Directory.CreateDirectory(DataDir);
        Load();
    }

    // ─── Persistence ──────────────────────────────────────────────────────

    private void Load()
    {
        if (!File.Exists(DataFile))
        {
            goal = DefaultGoal;
            entries = new List<Entry>();
            return;
        }
        try
        {
            string json = File.ReadAllText(DataFile);
            var data = JsonSerializer.Deserialize<Data>(json);
            if (data != null)
            {
                goal = data.Goal > 0 ? data.Goal : DefaultGoal;
                entries = data.Entries ?? new List<Entry>();
            }
            else
            {
                goal = DefaultGoal;
                entries = new List<Entry>();
            }
        }
        catch
        {
            goal = DefaultGoal;
            entries = new List<Entry>();
        }
    }

    private void Save()
    {
        var data = new Data { Goal = goal, Entries = entries };
        string json = JsonSerializer.Serialize(data, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(DataFile, json);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private string Today() => DateTime.Now.ToString("yyyy-MM-dd");
    private string Timestamp() => DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");

    private List<Entry> GetTodayEntries()
    {
        string today = Today();
        return entries.Where(e => e.Date == today).ToList();
    }

    private int GetTodayTotal() => GetTodayEntries().Sum(e => e.Amount);

    private List<(string Date, int Total)> GetLastNDays(int n)
    {
        var now = DateTime.Now;
        var result = new List<(string, int)>();
        for (int i = n - 1; i >= 0; i--)
        {
            var d = now.AddDays(-i);
            string dateStr = d.ToString("yyyy-MM-dd");
            int total = entries.Where(e => e.Date == dateStr).Sum(e => e.Amount);
            result.Add((dateStr, total));
        }
        return result;
    }

    private string ProgressBar(int current, int goal, int width = 20)
    {
        if (goal <= 0) return "⚠️  Goal not set";
        double ratio = Math.Min((double)current / goal, 1.0);
        int filled = (int)(ratio * width);
        string bar = new string('█', filled) + new string('░', width - filled);
        return $"[{bar}] {ratio * 100.0:F1}%";
    }

    private string Ask(string prompt)
    {
        Console.Write(prompt);
        return Console.ReadLine()?.Trim() ?? "";
    }

    private int AskInt(string prompt)
    {
        while (true)
        {
            if (int.TryParse(Ask(prompt), out int val))
                return val;
            Console.WriteLine(C("❌ Please enter a number.", Red));
        }
    }

    private bool AskConfirm(string prompt)
    {
        string ans = Ask(prompt + " (yes/no): ").ToLower();
        return ans == "yes";
    }

    // ─── Core Actions ─────────────────────────────────────────────────────

    private void AddEntry(int amount)
    {
        if (amount <= 0)
        {
            Console.WriteLine(C("❌ Amount must be positive!", Red));
            return;
        }
        if (amount > MaxAmount)
        {
            Console.WriteLine(C($"⚠️  That's a lot! Max {MaxAmount} ml.", Yellow));
            if (!AskConfirm("Continue anyway?")) return;
        }
        entries.Add(new Entry { Date = Today(), Amount = amount, Timestamp = Timestamp() });
        Save();
        int todayTotal = GetTodayTotal();
        Console.WriteLine(C($"✅ Added {amount}ml (Total today: {todayTotal}ml)", Green));
        if (todayTotal >= goal)
        {
            Console.WriteLine(C("🎉 Goal achieved! Stay hydrated! 💪", Cyan));
        }
    }

    private void ShowToday()
    {
        int todayTotal = GetTodayTotal();
        var todayEntries = GetTodayEntries();
        Console.WriteLine("\n" + C(new string('═', 50), Dim));
        Console.WriteLine(C("💧 TODAY'S HYDRATION", Bright + Cyan));
        Console.WriteLine(C(new string('═', 50), Dim));
        Console.WriteLine($"  Goal:      {C($"{goal}ml", Cyan)}");
        Console.WriteLine($"  Consumed:  {C($"{todayTotal}ml", Green)}");
        int remaining = Math.Max(goal - todayTotal, 0);
        Console.WriteLine($"  Remaining: {C($"{remaining}ml", Yellow)}");
        Console.WriteLine($"  Progress:  {ProgressBar(todayTotal, goal)}");
        Console.WriteLine(C(new string('═', 50), Dim));
        if (todayEntries.Count == 0)
        {
            Console.WriteLine(C("  No entries yet today. Drink up! 💧", Dim));
        }
        else
        {
            Console.WriteLine("  Entries:");
            for (int i = 0; i < todayEntries.Count; i++)
            {
                var e = todayEntries[i];
                string ts = e.Timestamp.Length >= 16 ? e.Timestamp[11..16] : "—";
                Console.WriteLine($"    {i+1}. {ts} → {C($"{e.Amount}ml", Green)}");
            }
        }
    }

    private void ShowChart(int days)
    {
        var history = GetLastNDays(days);
        int maxVal = history.Count > 0 ? history.Max(h => h.Total) : 0;
        if (maxVal == 0)
        {
            Console.WriteLine(C("No data to chart.", Yellow));
            return;
        }
        int chartWidth = Math.Min(40, Math.Max(10, maxVal / 50 + 1));
        double scale = (double)maxVal / chartWidth;
        Console.WriteLine($"\n{C($"📈 Hydration Chart (last {days} days)", Bright + Cyan)}");
        foreach (var (date, total) in history)
        {
            int barLen = (int)(total / scale);
            string bar = new string('█', barLen) + new string('░', chartWidth - barLen);
            string dateStr = date[5..10];
            string totalStr = $"{total,6}ml";
            Console.WriteLine($"  {dateStr} {bar} {totalStr}");
        }
    }

    private void ShowStats()
    {
        if (entries.Count == 0)
        {
            Console.WriteLine(C("📭 No data yet. Start tracking!", Yellow));
            return;
        }
        int total = entries.Sum(e => e.Amount);
        int count = entries.Count;
        double avg = (double)total / count;
        int maxEntry = entries.Max(e => e.Amount);
        int minEntry = entries.Min(e => e.Amount);
        int daysTracked = entries.Select(e => e.Date).Distinct().Count();
        Console.WriteLine("\n📊 STATISTICS");
        Console.WriteLine(C(new string('─', 30), Dim));
        Console.WriteLine($"  Total Consumed: {total}ml");
        Console.WriteLine($"  Total Entries:  {count}");
        Console.WriteLine($"  Days Tracked:   {daysTracked}");
        Console.WriteLine($"  Average per Day: {avg:F1}ml");
        Console.WriteLine($"  Max Entry:      {maxEntry}ml");
        Console.WriteLine($"  Min Entry:      {minEntry}ml");
        Console.WriteLine($"  Daily Goal:     {goal}ml");
    }

    private void SetGoal(int newGoal)
    {
        if (newGoal <= 0)
        {
            Console.WriteLine(C("❌ Goal must be positive!", Red));
            return;
        }
        goal = newGoal;
        Save();
        Console.WriteLine(C($"✅ Daily goal set to {goal}ml", Green));
    }

    private void ClearData()
    {
        if (!AskConfirm("⚠️  Delete ALL data? This cannot be undone!")) return;
        entries.Clear();
        goal = DefaultGoal;
        Save();
        Console.WriteLine(C("🗑️  All data cleared.", Yellow));
    }

    // ─── Menu ─────────────────────────────────────────────────────────────

    private void ShowMenu()
    {
        int todayTotal = GetTodayTotal();
        string progress = ProgressBar(todayTotal, goal);
        Console.WriteLine("\n" + C(new string('═', 50), Cyan));
        Console.WriteLine(C("💧 WATER TRACKER – Hydration Dashboard", Bright + Cyan));
        Console.WriteLine(C(new string('═', 50), Cyan));
        Console.WriteLine($"  Today: {todayTotal}ml / {goal}ml  {progress}");
        Console.WriteLine(C(new string('─', 50), Dim));
        Console.WriteLine("  1. 💧 Add water intake");
        Console.WriteLine("  2. 📊 Today's progress");
        Console.WriteLine("  3. 📈 Show chart (last 7 days)");
        Console.WriteLine("  4. 📊 Statistics");
        Console.WriteLine($"  5. 🎯 Set daily goal (current: {goal}ml)");
        Console.WriteLine("  6. 🗑️  Clear all data");
        Console.WriteLine("  0. 🚪 Exit");
        Console.WriteLine(C(new string('═', 50), Cyan));
    }

    public void Run()
    {
        Console.Clear();
        Console.WriteLine(C("\n💧 Water Tracker – Hydration Dashboard", Bright + Cyan));
        Console.WriteLine(C("Visualize your hydration journey!", Dim));

        while (true)
        {
            ShowMenu();
            string choice = Ask("Your choice: ");
            switch (choice)
            {
                case "1":
                    int amount = AskInt("Amount in ml: ");
                    AddEntry(amount);
                    break;
                case "2":
                    ShowToday();
                    break;
                case "3":
                    ShowChart(7);
                    break;
                case "4":
                    ShowStats();
                    break;
                case "5":
                    int newGoal = AskInt("New daily goal (ml): ");
                    SetGoal(newGoal);
                    break;
                case "6":
                    ClearData();
                    break;
                case "0":
                    Console.WriteLine(C("👋 Stay hydrated! Goodbye!", Cyan));
                    return;
                default:
                    Console.WriteLine(C("❌ Invalid choice.", Red));
                    break;
            }
            if (choice != "0")
            {
                Console.Write("\nPress Enter to continue...");
                Console.ReadLine();
            }
        }
    }

    public static void Main()
    {
        try
        {
            var app = new WaterTracker();
            app.Run();
        }
        catch (Exception ex)
        {
            Console.WriteLine(C($"❌ Unexpected error: {ex.Message}", Red));
            Environment.Exit(1);
        }
    }
}
