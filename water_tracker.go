# water_tracker.go
/**
 * 💧 Water Tracker – Hydration Dashboard with Charts (Go Edition)
 * Features: add intake, bar chart, stats, goal, persistence, colored output
 */

package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// ─── Types ──────────────────────────────────────────────────────────────────

type Entry struct {
	Date      string `json:"date"`
	Amount    int    `json:"amount"`
	Timestamp string `json:"timestamp"`
}

type Data struct {
	Goal    int     `json:"goal"`
	Entries []Entry `json:"entries"`
}

// ─── Config ──────────────────────────────────────────────────────────────────

const (
	defaultGoal = 2000
	maxAmount   = 10000
)

// ─── Colors ──────────────────────────────────────────────────────────────────

const (
	reset  = "\x1b[0m"
	bright = "\x1b[1m"
	dim    = "\x1b[2m"
	red    = "\x1b[31m"
	green  = "\x1b[32m"
	yellow = "\x1b[33m"
	blue   = "\x1b[34m"
	magenta = "\x1b[35m"
	cyan   = "\x1b[36m"
)

func c(str, color string) string {
	return color + str + reset
}

// ─── Data Manager ──────────────────────────────────────────────────────────

type WaterTracker struct {
	goal    int
	entries []Entry
	file    string
	reader  *bufio.Reader
}

func NewWaterTracker() *WaterTracker {
	home, _ := os.UserHomeDir()
	dir := filepath.Join(home, ".water_tracker")
	os.MkdirAll(dir, 0755)
	file := filepath.Join(dir, "data.json")
	w := &WaterTracker{file: file, reader: bufio.NewReader(os.Stdin)}
	w.load()
	return w
}

func (w *WaterTracker) load() {
	if _, err := os.Stat(w.file); os.IsNotExist(err) {
		w.goal = defaultGoal
		w.entries = []Entry{}
		return
	}
	raw, err := os.ReadFile(w.file)
	if err != nil {
		w.goal = defaultGoal
		w.entries = []Entry{}
		return
	}
	var data Data
	if err := json.Unmarshal(raw, &data); err != nil {
		w.goal = defaultGoal
		w.entries = []Entry{}
		return
	}
	w.goal = data.Goal
	if w.goal <= 0 {
		w.goal = defaultGoal
	}
	w.entries = data.Entries
	if w.entries == nil {
		w.entries = []Entry{}
	}
}

func (w *WaterTracker) save() {
	data := Data{Goal: w.goal, Entries: w.entries}
	raw, _ := json.MarshalIndent(data, "", "  ")
	os.WriteFile(w.file, raw, 0644)
}

func (w *WaterTracker) today() string {
	return time.Now().Format("2006-01-02")
}

func (w *WaterTracker) getTodayEntries() []Entry {
	today := w.today()
	var res []Entry
	for _, e := range w.entries {
		if e.Date == today {
			res = append(res, e)
		}
	}
	return res
}

func (w *WaterTracker) getTodayTotal() int {
	total := 0
	for _, e := range w.getTodayEntries() {
		total += e.Amount
	}
	return total
}

func (w *WaterTracker) getLastNDays(n int) []struct{ Date string; Total int } {
	now := time.Now()
	res := make([]struct{ Date string; Total int }, n)
	for i := n - 1; i >= 0; i-- {
		d := now.AddDate(0, 0, -i)
		dateStr := d.Format("2006-01-02")
		total := 0
		for _, e := range w.entries {
			if e.Date == dateStr {
				total += e.Amount
			}
		}
		res = append(res, struct{ Date string; Total int }{dateStr, total})
	}
	return res
}

func (w *WaterTracker) progressBar(current, goal, width int) string {
	if goal <= 0 {
		return "⚠️  Goal not set"
	}
	ratio := float64(current) / float64(goal)
	if ratio > 1.0 {
		ratio = 1.0
	}
	filled := int(math.Floor(ratio * float64(width)))
	bar := strings.Repeat("█", filled) + strings.Repeat("░", width-filled)
	return fmt.Sprintf("[%s] %.1f%%", bar, ratio*100)
}

func (w *WaterTracker) ask(prompt string) string {
	fmt.Print(prompt)
	line, _ := w.reader.ReadString('\n')
	return strings.TrimSpace(line)
}

func (w *WaterTracker) askInt(prompt string) (int, error) {
	ans := w.ask(prompt)
	return strconv.Atoi(ans)
}

func (w *WaterTracker) askConfirm(prompt string) bool {
	ans := w.ask(prompt + " (yes/no): ")
	return strings.ToLower(ans) == "yes"
}

// ─── Core Actions ──────────────────────────────────────────────────────────

func (w *WaterTracker) AddEntry(amount int) error {
	if amount <= 0 {
		return fmt.Errorf("amount must be positive")
	}
	if amount > maxAmount {
		fmt.Println(c("⚠️  That's a lot! Max "+strconv.Itoa(maxAmount)+" ml.", yellow))
		if !w.askConfirm("Continue anyway?") {
			return nil
		}
	}
	w.entries = append(w.entries, Entry{
		Date:      w.today(),
		Amount:    amount,
		Timestamp: time.Now().Format(time.RFC3339),
	})
	w.save()
	todayTotal := w.getTodayTotal()
	fmt.Printf(c("✅ Added %dml (Total today: %dml)\n", green), amount, todayTotal)
	if todayTotal >= w.goal {
		fmt.Println(c("🎉 Goal achieved! Stay hydrated! 💪", cyan))
	}
	return nil
}

func (w *WaterTracker) ShowToday() {
	todayTotal := w.getTodayTotal()
	entries := w.getTodayEntries()
	fmt.Println("\n" + c(strings.Repeat("═", 50), dim))
	fmt.Println(c("💧 TODAY'S HYDRATION", bright+cyan))
	fmt.Println(c(strings.Repeat("═", 50), dim))
	fmt.Printf("  Goal:      %s\n", c(strconv.Itoa(w.goal)+"ml", cyan))
	fmt.Printf("  Consumed:  %s\n", c(strconv.Itoa(todayTotal)+"ml", green))
	remaining := w.goal - todayTotal
	if remaining < 0 {
		remaining = 0
	}
	fmt.Printf("  Remaining: %s\n", c(strconv.Itoa(remaining)+"ml", yellow))
	fmt.Printf("  Progress:  %s\n", w.progressBar(todayTotal, w.goal, 20))
	fmt.Println(c(strings.Repeat("═", 50), dim))
	if len(entries) > 0 {
		fmt.Println("  Entries:")
		for i, e := range entries {
			ts := "—"
			if len(e.Timestamp) >= 16 {
				ts = e.Timestamp[11:16]
			}
			fmt.Printf("    %d. %s → %s\n", i+1, ts, c(strconv.Itoa(e.Amount)+"ml", green))
		}
	} else {
		fmt.Println(c("  No entries yet today. Drink up! 💧", dim))
	}
}

func (w *WaterTracker) ShowChart(days int) {
	history := w.getLastNDays(days)
	maxVal := 0
	for _, h := range history {
		if h.Total > maxVal {
			maxVal = h.Total
		}
	}
	if maxVal == 0 {
		fmt.Println(c("No data to chart.", yellow))
		return
	}
	chartWidth := 40
	if maxVal < 100 {
		chartWidth = 20
	} else if maxVal < 500 {
		chartWidth = 30
	}
	scale := float64(maxVal) / float64(chartWidth)
	fmt.Printf("\n%s\n", c("📈 Hydration Chart (last "+strconv.Itoa(days)+" days)", bright+cyan))
	for _, h := range history {
		barLen := int(math.Floor(float64(h.Total) / scale))
		if barLen < 0 {
			barLen = 0
		}
		bar := strings.Repeat("█", barLen) + strings.Repeat("░", chartWidth-barLen)
		dateStr := h.Date[5:10]
		totalStr := fmt.Sprintf("%dml", h.Total)
		fmt.Printf("  %s %s %6s\n", dateStr, bar, totalStr)
	}
}

func (w *WaterTracker) ShowStats() {
	if len(w.entries) == 0 {
		fmt.Println(c("📭 No data yet. Start tracking!", yellow))
		return
	}
	total := 0
	maxEntry := 0
	minEntry := int(^uint(0) >> 1)
	for _, e := range w.entries {
		total += e.Amount
		if e.Amount > maxEntry {
			maxEntry = e.Amount
		}
		if e.Amount < minEntry {
			minEntry = e.Amount
		}
	}
	count := len(w.entries)
	avg := float64(total) / float64(count)
	daysTracked := make(map[string]bool)
	for _, e := range w.entries {
		daysTracked[e.Date] = true
	}
	fmt.Println("\n📊 STATISTICS")
	fmt.Println(c(strings.Repeat("─", 30), dim))
	fmt.Printf("  Total Consumed: %dml\n", total)
	fmt.Printf("  Total Entries:  %d\n", count)
	fmt.Printf("  Days Tracked:   %d\n", len(daysTracked))
	fmt.Printf("  Average per Day: %.1fml\n", avg)
	fmt.Printf("  Max Entry:      %dml\n", maxEntry)
	fmt.Printf("  Min Entry:      %dml\n", minEntry)
	fmt.Printf("  Daily Goal:     %dml\n", w.goal)
}

func (w *WaterTracker) SetGoal(goal int) error {
	if goal <= 0 {
		return fmt.Errorf("goal must be positive")
	}
	w.goal = goal
	w.save()
	fmt.Printf(c("✅ Daily goal set to %dml\n", green), goal)
	return nil
}

func (w *WaterTracker) ClearData() {
	if !w.askConfirm("⚠️  Delete ALL data? This cannot be undone!") {
		return
	}
	w.entries = []Entry{}
	w.goal = defaultGoal
	w.save()
	fmt.Println(c("🗑️  All data cleared.", yellow))
}

// ─── Menu ──────────────────────────────────────────────────────────────────

func (w *WaterTracker) showMenu() {
	todayTotal := w.getTodayTotal()
	progress := w.progressBar(todayTotal, w.goal, 20)
	fmt.Println("\n" + c(strings.Repeat("═", 50), cyan))
	fmt.Println(c("💧 WATER TRACKER – Hydration Dashboard", bright+cyan))
	fmt.Println(c(strings.Repeat("═", 50), cyan))
	fmt.Printf("  Today: %dml / %dml  %s\n", todayTotal, w.goal, progress)
	fmt.Println(c(strings.Repeat("─", 50), dim))
	fmt.Println("  1. 💧 Add water intake")
	fmt.Println("  2. 📊 Today's progress")
	fmt.Println("  3. 📈 Show chart (last 7 days)")
	fmt.Println("  4. 📊 Statistics")
	fmt.Printf("  5. 🎯 Set daily goal (current: %dml)\n", w.goal)
	fmt.Println("  6. 🗑️  Clear all data")
	fmt.Println("  0. 🚪 Exit")
	fmt.Println(c(strings.Repeat("═", 50), cyan))
}

func (w *WaterTracker) Run() {
	fmt.Print("\033[H\033[2J")
	fmt.Println(c("\n💧 Water Tracker – Hydration Dashboard", bright+cyan))
	fmt.Println(c("Visualize your hydration journey!", dim))

	for {
		w.showMenu()
		choice := w.ask("Your choice: ")
		switch choice {
		case "1":
			amount, err := w.askInt("Amount in ml: ")
			if err != nil {
				fmt.Println(c("❌ Please enter a number.", red))
			} else {
				w.AddEntry(amount)
			}
		case "2":
			w.ShowToday()
		case "3":
			w.ShowChart(7)
		case "4":
			w.ShowStats()
		case "5":
			goal, err := w.askInt("New daily goal (ml): ")
			if err != nil {
				fmt.Println(c("❌ Please enter a number.", red))
			} else {
				w.SetGoal(goal)
			}
		case "6":
			w.ClearData()
		case "0":
			fmt.Println(c("👋 Stay hydrated! Goodbye!", cyan))
			return
		default:
			fmt.Println(c("❌ Invalid choice.", red))
		}
		if choice != "0" {
			fmt.Print("\nPress Enter to continue...")
			w.reader.ReadString('\n')
		}
	}
}

func main() {
	app := NewWaterTracker()
	app.Run()
}
