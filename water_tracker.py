# water_tracker.py
#!/usr/bin/env python3
"""
💧 Water Tracker – Hydration Dashboard with Charts (Python Edition)
Features: add intake, view bar chart, stats, goal setting, JSON persistence, colored output
"""

import json
import os
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import List, Dict, Optional

try:
    from rich.console import Console
    from rich.table import Table
    from rich.panel import Panel
    from rich.prompt import Prompt, IntPrompt, Confirm
    from rich import box
    from rich.progress import Progress, BarColumn, TextColumn
    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False
    print("⚠️  Install 'rich' for enhanced UI: pip install rich")


# ─── Colors ──────────────────────────────────────────────────────────────────

def c(text: str, color: str) -> str:
    colors = {
        "reset": "\033[0m", "bright": "\033[1m", "dim": "\033[2m",
        "red": "\033[31m", "green": "\033[32m", "yellow": "\033[33m",
        "blue": "\033[34m", "magenta": "\033[35m", "cyan": "\033[36m"
    }
    return f"{colors.get(color, '')}{text}{colors['reset']}"


# ─── Data Manager ──────────────────────────────────────────────────────────

class WaterTracker:
    DATA_DIR = Path.home() / ".water_tracker"
    DATA_FILE = DATA_DIR / "data.json"
    DEFAULT_GOAL = 2000

    def __init__(self):
        self.console = Console() if RICH_AVAILABLE else None
        self.data = self._load()
        self.goal = self.data.get("goal", self.DEFAULT_GOAL)
        self.entries: List[Dict] = self.data.get("entries", [])

    def _load(self) -> Dict:
        if self.DATA_FILE.exists():
            try:
                with open(self.DATA_FILE, 'r') as f:
                    return json.load(f)
            except Exception:
                return {"goal": self.DEFAULT_GOAL, "entries": []}
        return {"goal": self.DEFAULT_GOAL, "entries": []}

    def _save(self) -> None:
        self.DATA_DIR.mkdir(parents=True, exist_ok=True)
        with open(self.DATA_FILE, 'w') as f:
            json.dump({"goal": self.goal, "entries": self.entries}, f, indent=2)

    def _today(self) -> str:
        return datetime.now().strftime("%Y-%m-%d")

    def _get_today_entries(self) -> List[Dict]:
        today = self._today()
        return [e for e in self.entries if e.get("date") == today]

    def _get_today_total(self) -> int:
        return sum(e.get("amount", 0) for e in self._get_today_entries())

    def _get_last_n_days(self, n: int = 7) -> List[Dict]:
        """Return list of (date, total) for the last n days."""
        today = datetime.now().date()
        result = []
        for i in range(n):
            d = today - timedelta(days=i)
            date_str = d.strftime("%Y-%m-%d")
            total = sum(e.get("amount", 0) for e in self.entries if e.get("date") == date_str)
            result.append({"date": date_str, "total": total})
        result.reverse()
        return result

    def add_entry(self, amount: int) -> None:
        if amount <= 0:
            print(c("❌ Amount must be positive!", "red"))
            return
        if amount > 10000:
            print(c("⚠️  That's a lot! Max 10000 ml.", "yellow"))
            if self.console and not Confirm.ask("Continue anyway?"):
                return

        entry = {
            "date": self._today(),
            "amount": amount,
            "timestamp": datetime.now().isoformat()
        }
        self.entries.append(entry)
        self._save()

        today_total = self._get_today_total()
        if self.console:
            self.console.print(f"[green]✅ Added {amount}ml (Total today: {today_total}ml)[/green]")
            if today_total >= self.goal:
                self.console.print("[bold cyan]🎉 Goal achieved! Stay hydrated! 💪[/bold cyan]")
        else:
            print(c(f"✅ Added {amount}ml (Total today: {today_total}ml)", "green"))
            if today_total >= self.goal:
                print(c("🎉 Goal achieved! Stay hydrated! 💪", "cyan"))

    def show_today(self) -> None:
        today_total = self._get_today_total()
        entries = self._get_today_entries()

        if self.console:
            panel = Panel(
                f"[bold]💧 Today's Hydration[/bold]\n"
                f"  Goal: {self.goal}ml\n"
                f"  Consumed: {today_total}ml\n"
                f"  Remaining: {max(self.goal - today_total, 0)}ml\n"
                f"  Progress: {self._progress_bar(today_total, self.goal)}",
                title="📊 Daily Progress",
                border_style="cyan"
            )
            self.console.print(panel)
            if entries:
                table = Table(title="Today's Entries", box=box.ROUNDED)
                table.add_column("#", style="dim")
                table.add_column("Time", style="cyan")
                table.add_column("Amount", style="green", justify="right")
                for i, e in enumerate(entries, 1):
                    ts = e.get("timestamp", "")[11:16] if "T" in e.get("timestamp", "") else "—"
                    table.add_row(str(i), ts, f"{e['amount']}ml")
                self.console.print(table)
            else:
                self.console.print("[dim]No entries yet today. Drink up! 💧[/dim]")
        else:
            print("\n" + "="*50)
            print(c("💧 TODAY'S HYDRATION", "bright"))
            print("="*50)
            print(f"  Goal:      {self.goal}ml")
            print(f"  Consumed:  {today_total}ml")
            print(f"  Remaining: {max(self.goal - today_total, 0)}ml")
            print(f"  Progress:  {self._progress_bar(today_total, self.goal)}")
            print("="*50)
            if entries:
                print("  Entries:")
                for i, e in enumerate(entries, 1):
                    ts = e.get("timestamp", "")[11:16] if "T" in e.get("timestamp", "") else "—"
                    print(f"    {i}. {ts} → {e['amount']}ml")
            else:
                print("  No entries yet today.")

    def show_chart(self, days: int = 7) -> None:
        """Display an ASCII bar chart of the last N days."""
        history = self._get_last_n_days(days)
        if not history:
            print(c("No data to chart.", "yellow"))
            return

        max_val = max((d["total"] for d in history), default=0)
        if max_val == 0:
            print(c("No data to chart.", "yellow"))
            return

        # Determine bar width (max 40 chars)
        chart_width = min(40, max(10, int(max_val / 50) + 1))
        scale = max_val / chart_width

        if self.console:
            self.console.print(f"\n[bold cyan]📈 Hydration Chart (last {days} days)[/bold cyan]")
            for item in history:
                bar_len = int(item["total"] / scale)
                bar = "█" * bar_len + "░" * (chart_width - bar_len)
                date_str = item["date"][5:10]  # MM-DD
                total_str = f"{item['total']}ml"
                self.console.print(f"  {date_str} {bar} {total_str:>6}")
        else:
            print(f"\n📈 Hydration Chart (last {days} days)")
            for item in history:
                bar_len = int(item["total"] / scale)
                bar = "█" * bar_len + "░" * (chart_width - bar_len)
                date_str = item["date"][5:10]
                total_str = f"{item['total']}ml"
                print(f"  {date_str} {bar} {total_str:>6}")

    def show_stats(self) -> None:
        if not self.entries:
            print(c("📭 No data yet. Start tracking!", "yellow"))
            return

        total = sum(e.get("amount", 0) for e in self.entries)
        count = len(self.entries)
        avg = total / count if count else 0
        max_entry = max((e.get("amount", 0) for e in self.entries), default=0)
        min_entry = min((e.get("amount", 0) for e in self.entries), default=0)
        days_tracked = len(set(e.get("date") for e in self.entries))

        if self.console:
            table = Table(title="📊 Statistics", box=box.ROUNDED)
            table.add_column("Metric", style="cyan")
            table.add_column("Value", style="green")
            table.add_row("Total Consumed", f"{total}ml")
            table.add_row("Total Entries", str(count))
            table.add_row("Days Tracked", str(days_tracked))
            table.add_row("Average per Day", f"{avg:.1f}ml")
            table.add_row("Max Entry", f"{max_entry}ml")
            table.add_row("Min Entry", f"{min_entry}ml")
            table.add_row("Daily Goal", f"{self.goal}ml")
            self.console.print(table)
        else:
            print("\n📊 STATISTICS")
            print(c("─"*30, "dim"))
            print(f"  Total Consumed: {total}ml")
            print(f"  Total Entries:  {count}")
            print(f"  Days Tracked:   {days_tracked}")
            print(f"  Average per Day: {avg:.1f}ml")
            print(f"  Max Entry:      {max_entry}ml")
            print(f"  Min Entry:      {min_entry}ml")
            print(f"  Daily Goal:     {self.goal}ml")

    def set_goal(self, goal: int) -> None:
        if goal <= 0:
            print(c("❌ Goal must be positive!", "red"))
            return
        self.goal = goal
        self._save()
        print(c(f"✅ Daily goal set to {goal}ml", "green"))

    def clear_data(self) -> None:
        if self.console:
            if not Confirm.ask("⚠️  Delete ALL data? This cannot be undone!"):
                return
        else:
            if input("⚠️  Delete ALL data? (yes/no): ").strip().lower() != "yes":
                return
        self.entries = []
        self.goal = self.DEFAULT_GOAL
        self._save()
        print(c("🗑️  All data cleared.", "yellow"))

    def _progress_bar(self, current: int, goal: int, width: int = 20) -> str:
        if goal <= 0:
            return "⚠️  Goal not set"
        ratio = min(current / goal, 1.0)
        filled = int(ratio * width)
        bar = "█" * filled + "░" * (width - filled)
        return f"[{bar}] {ratio*100:.1f}%"

    def run(self) -> None:
        if self.console:
            self.console.print(Panel.fit("[bold cyan]💧 Water Tracker – Hydration Dashboard[/bold cyan]", border_style="cyan"))
        else:
            print(c("\n💧 Water Tracker – Hydration Dashboard", "bright"))
            print(c("Visualize your hydration journey!", "dim"))

        while True:
            self._show_menu()
            choice = self._get_choice()
            if choice == "1":
                amount = self._get_amount()
                if amount:
                    self.add_entry(amount)
            elif choice == "2":
                self.show_today()
            elif choice == "3":
                self.show_chart()
            elif choice == "4":
                self.show_stats()
            elif choice == "5":
                goal = self._get_goal()
                if goal:
                    self.set_goal(goal)
            elif choice == "6":
                self.clear_data()
            elif choice == "0":
                print(c("👋 Stay hydrated! Goodbye!", "cyan"))
                break
            else:
                print(c("❌ Invalid choice.", "red"))

            if choice != "0":
                if self.console:
                    self.console.print("\n[dim]Press Enter to continue...[/dim]")
                    input()
                else:
                    input("\nPress Enter to continue...")

    def _show_menu(self) -> None:
        today_total = self._get_today_total()
        progress = self._progress_bar(today_total, self.goal)
        if self.console:
            menu = f"""
[bold cyan]💧 Main Menu[/bold cyan]
  Today: {today_total}ml / {self.goal}ml  {progress}

  [1] 💧 Add water intake
  [2] 📊 Today's progress
  [3] 📈 Show chart (last 7 days)
  [4] 📊 Statistics
  [5] 🎯 Set daily goal (current: {self.goal}ml)
  [6] 🗑️  Clear all data
  [0] 🚪 Exit
"""
            self.console.print(Panel(menu, border_style="blue"))
        else:
            print("\n" + "-"*50)
            print(f"💧 Today: {today_total}ml / {self.goal}ml  {progress}")
            print("-"*50)
            print("  1. 💧 Add water intake")
            print("  2. 📊 Today's progress")
            print("  3. 📈 Show chart (last 7 days)")
            print("  4. 📊 Statistics")
            print(f"  5. 🎯 Set daily goal (current: {self.goal}ml)")
            print("  6. 🗑️  Clear all data")
            print("  0. 🚪 Exit")
            print("-"*50)

    def _get_choice(self) -> str:
        if self.console:
            return Prompt.ask("Your choice", choices=["0","1","2","3","4","5","6"])
        return input("Your choice: ").strip()

    def _get_amount(self) -> Optional[int]:
        if self.console:
            return IntPrompt.ask("Amount in ml")
        try:
            return int(input("Amount in ml: ").strip())
        except ValueError:
            print(c("❌ Please enter a number.", "red"))
            return None

    def _get_goal(self) -> Optional[int]:
        if self.console:
            return IntPrompt.ask("New daily goal in ml")
        try:
            return int(input("New daily goal (ml): ").strip())
        except ValueError:
            print(c("❌ Please enter a number.", "red"))
            return None


def main():
    try:
        app = WaterTracker()
        app.run()
    except KeyboardInterrupt:
        print("\n👋 Goodbye!")
        sys.exit(0)
    except Exception as e:
        print(c(f"❌ Unexpected error: {e}", "red"))
        sys.exit(1)

if __name__ == "__main__":
    main()
