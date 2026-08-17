# water_tracker.rs
/**
 * 💧 Water Tracker – Hydration Dashboard with Charts (Rust Edition)
 * Features: add intake, bar chart, stats, goal, persistence, colored output
 * Dependencies: serde, serde_json, chrono, colored
 */

use chrono::{DateTime, Local, Duration};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::io::{self, Write, BufRead};
use std::path::PathBuf;
use colored::*;

// ─── Types ──────────────────────────────────────────────────────────────────

#[derive(Debug, Serialize, Deserialize, Clone)]
struct Entry {
    date: String,
    amount: u32,
    timestamp: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct Data {
    goal: u32,
    entries: Vec<Entry>,
}

// ─── Config ──────────────────────────────────────────────────────────────────

const DEFAULT_GOAL: u32 = 2000;
const MAX_AMOUNT: u32 = 10000;

// ─── Data Manager ──────────────────────────────────────────────────────────

struct WaterTracker {
    goal: u32,
    entries: Vec<Entry>,
    file_path: PathBuf,
}

impl WaterTracker {
    fn new() -> Self {
        let home = std::env::var("HOME").or_else(|_| std::env::var("USERPROFILE")).unwrap_or_else(|_| ".".to_string());
        let dir = PathBuf::from(home).join(".water_tracker");
        fs::create_dir_all(&dir).unwrap();
        let file_path = dir.join("data.json");
        let mut wt = WaterTracker { goal: DEFAULT_GOAL, entries: Vec::new(), file_path };
        wt.load();
        wt
    }

    fn load(&mut self) {
        if let Ok(raw) = fs::read_to_string(&self.file_path) {
            if let Ok(data) = serde_json::from_str::<Data>(&raw) {
                self.goal = if data.goal > 0 { data.goal } else { DEFAULT_GOAL };
                self.entries = data.entries;
                return;
            }
        }
        self.goal = DEFAULT_GOAL;
        self.entries = Vec::new();
    }

    fn save(&self) {
        let data = Data { goal: self.goal, entries: self.entries.clone() };
        let raw = serde_json::to_string_pretty(&data).unwrap();
        let _ = fs::write(&self.file_path, raw);
    }

    fn today(&self) -> String {
        Local::now().format("%Y-%m-%d").to_string()
    }

    fn get_today_entries(&self) -> Vec<Entry> {
        let today = self.today();
        self.entries.iter().filter(|e| e.date == today).cloned().collect()
    }

    fn get_today_total(&self) -> u32 {
        self.get_today_entries().iter().map(|e| e.amount).sum()
    }

    fn get_last_n_days(&self, n: usize) -> Vec<(String, u32)> {
        let now = Local::now();
        let mut result = Vec::new();
        for i in (0..n).rev() {
            let d = now - Duration::days(i as i64);
            let date_str = d.format("%Y-%m-%d").to_string();
            let total = self.entries.iter().filter(|e| e.date == date_str).map(|e| e.amount).sum();
            result.push((date_str, total));
        }
        result
    }

    fn progress_bar(&self, current: u32, goal: u32, width: usize) -> String {
        if goal == 0 {
            return "⚠️  Goal not set".to_string();
        }
        let ratio = (current as f64 / goal as f64).min(1.0);
        let filled = (ratio * width as f64) as usize;
        let bar = "█".repeat(filled) + &"░".repeat(width - filled);
        format!("[{}] {:.1}%", bar, ratio * 100.0)
    }

    fn ask(&self, prompt: &str) -> String {
        print!("{}", prompt);
        io::stdout().flush().unwrap();
        let mut line = String::new();
        io::stdin().read_line(&mut line).unwrap();
        line.trim().to_string()
    }

    fn ask_int(&self, prompt: &str) -> Result<u32, Box<dyn std::error::Error>> {
        let ans = self.ask(prompt);
        Ok(ans.parse()?)
    }

    fn ask_confirm(&self, prompt: &str) -> bool {
        let ans = self.ask(&format!("{} (yes/no): ", prompt));
        ans.to_lowercase() == "yes"
    }

    // ─── Core Actions ──────────────────────────────────────────────────────

    fn add_entry(&mut self, amount: u32) -> Result<(), Box<dyn std::error::Error>> {
        if amount == 0 {
            return Err("Amount must be positive!".into());
        }
        if amount > MAX_AMOUNT {
            println!("{}", format!("⚠️  That's a lot! Max {} ml.", MAX_AMOUNT).yellow());
            if !self.ask_confirm("Continue anyway?") {
                return Ok(());
            }
        }
        let entry = Entry {
            date: self.today(),
            amount,
            timestamp: Local::now().to_rfc3339(),
        };
        self.entries.push(entry);
        self.save();
        let today_total = self.get_today_total();
        println!("{}", format!("✅ Added {}ml (Total today: {}ml)", amount, today_total).green());
        if today_total >= self.goal {
            println!("{}", "🎉 Goal achieved! Stay hydrated! 💪".cyan());
        }
        Ok(())
    }

    fn show_today(&self) {
        let today_total = self.get_today_total();
        let entries = self.get_today_entries();
        println!("\n{}", "═".repeat(50).dimmed());
        println!("{}", "💧 TODAY'S HYDRATION".bright().cyan());
        println!("{}", "═".repeat(50).dimmed());
        println!("  Goal:      {}", format!("{}ml", self.goal).cyan());
        println!("  Consumed:  {}", format!("{}ml", today_total).green());
        let remaining = if self.goal > today_total { self.goal - today_total } else { 0 };
        println!("  Remaining: {}", format!("{}ml", remaining).yellow());
        println!("  Progress:  {}", self.progress_bar(today_total, self.goal, 20));
        println!("{}", "═".repeat(50).dimmed());
        if entries.is_empty() {
            println!("{}", "  No entries yet today. Drink up! 💧".dimmed());
        } else {
            println!("  Entries:");
            for (i, e) in entries.iter().enumerate() {
                let ts = if e.timestamp.len() >= 16 { &e.timestamp[11..16] } else { "—" };
                println!("    {}. {} → {}", i+1, ts, format!("{}ml", e.amount).green());
            }
        }
    }

    fn show_chart(&self, days: usize) {
        let history = self.get_last_n_days(days);
        let max_val = history.iter().map(|(_, total)| *total).max().unwrap_or(0);
        if max_val == 0 {
            println!("{}", "No data to chart.".yellow());
            return;
        }
        let chart_width = 40.min(10.max(max_val / 50 + 1) as usize);
        let scale = max_val as f64 / chart_width as f64;
        println!("\n{}", format!("📈 Hydration Chart (last {} days)", days).bright().cyan());
        for (date, total) in history {
            let bar_len = (total as f64 / scale) as usize;
            let bar = "█".repeat(bar_len) + &"░".repeat(chart_width - bar_len);
            let date_str = &date[5..10];
            let total_str = format!("{:>6}ml", total);
            println!("  {} {} {}", date_str, bar, total_str);
        }
    }

    fn show_stats(&self) {
        if self.entries.is_empty() {
            println!("{}", "📭 No data yet. Start tracking!".yellow());
            return;
        }
        let total: u32 = self.entries.iter().map(|e| e.amount).sum();
        let count = self.entries.len();
        let avg = total as f64 / count as f64;
        let max_entry = self.entries.iter().map(|e| e.amount).max().unwrap_or(0);
        let min_entry = self.entries.iter().map(|e| e.amount).min().unwrap_or(0);
        let mut days = std::collections::HashSet::new();
        for e in &self.entries {
            days.insert(&e.date);
        }
        let days_tracked = days.len();
        println!("\n📊 STATISTICS");
        println!("{}", "─".repeat(30).dimmed());
        println!("  Total Consumed: {}ml", total);
        println!("  Total Entries:  {}", count);
        println!("  Days Tracked:   {}", days_tracked);
        println!("  Average per Day: {:.1}ml", avg);
        println!("  Max Entry:      {}ml", max_entry);
        println!("  Min Entry:      {}ml", min_entry);
        println!("  Daily Goal:     {}ml", self.goal);
    }

    fn set_goal(&mut self, goal: u32) -> Result<(), Box<dyn std::error::Error>> {
        if goal == 0 {
            return Err("Goal must be positive!".into());
        }
        self.goal = goal;
        self.save();
        println!("{}", format!("✅ Daily goal set to {}ml", goal).green());
        Ok(())
    }

    fn clear_data(&mut self) {
        if !self.ask_confirm("⚠️  Delete ALL data? This cannot be undone!") {
            return;
        }
        self.entries = Vec::new();
        self.goal = DEFAULT_GOAL;
        self.save();
        println!("{}", "🗑️  All data cleared.".yellow());
    }

    // ─── Menu ──────────────────────────────────────────────────────────────

    fn show_menu(&self) {
        let today_total = self.get_today_total();
        let progress = self.progress_bar(today_total, self.goal, 20);
        println!("\n{}", "═".repeat(50).cyan());
        println!("{}", "💧 WATER TRACKER – Hydration Dashboard".bright().cyan());
        println!("{}", "═".repeat(50).cyan());
        println!("  Today: {}ml / {}ml  {}", today_total, self.goal, progress);
        println!("{}", "─".repeat(50).dimmed());
        println!("  1. 💧 Add water intake");
        println!("  2. 📊 Today's progress");
        println!("  3. 📈 Show chart (last 7 days)");
        println!("  4. 📊 Statistics");
        println!("  5. 🎯 Set daily goal (current: {}ml)", self.goal);
        println!("  6. 🗑️  Clear all data");
        println!("  0. 🚪 Exit");
        println!("{}", "═".repeat(50).cyan());
    }

    fn run(&mut self) {
        println!("{}", "\n💧 Water Tracker – Hydration Dashboard".bright().cyan());
        println!("{}", "Visualize your hydration journey!".dimmed());

        loop {
            self.show_menu();
            let choice = self.ask("Your choice: ");
            match choice.as_str() {
                "1" => {
                    match self.ask_int("Amount in ml: ") {
                        Ok(amount) => {
                            if let Err(e) = self.add_entry(amount) {
                                println!("{}", format!("❌ {}", e).red());
                            }
                        }
                        Err(_) => println!("{}", "❌ Please enter a number.".red()),
                    }
                }
                "2" => self.show_today(),
                "3" => self.show_chart(7),
                "4" => self.show_stats(),
                "5" => {
                    match self.ask_int("New daily goal (ml): ") {
                        Ok(goal) => {
                            if let Err(e) = self.set_goal(goal) {
                                println!("{}", format!("❌ {}", e).red());
                            }
                        }
                        Err(_) => println!("{}", "❌ Please enter a number.".red()),
                    }
                }
                "6" => self.clear_data(),
                "0" => {
                    println!("{}", "👋 Stay hydrated! Goodbye!".cyan());
                    break;
                }
                _ => println!("{}", "❌ Invalid choice.".red()),
            }
            if choice != "0" {
                print!("\nPress Enter to continue...");
                io::stdout().flush().unwrap();
                let mut _dummy = String::new();
                io::stdin().read_line(&mut _dummy).unwrap();
            }
        }
    }
}

// ─── Main ────────────────────────────────────────────────────────────────────

fn main() {
    let mut app = WaterTracker::new();
    app.run();
}
