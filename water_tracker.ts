# water_tracker.ts
/**
 * 💧 Water Tracker – Hydration Dashboard with Charts (TypeScript Edition)
 * Fully typed, advanced: add intake, chart, stats, goal, persistence
 */

import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import * as readline from 'readline';

// ─── Types ──────────────────────────────────────────────────────────────────

interface Entry {
    date: string;
    amount: number;
    timestamp: string;
}

interface Data {
    goal: number;
    entries: Entry[];
}

// ─── Colors ──────────────────────────────────────────────────────────────────

const colors = {
    reset: '\x1b[0m',
    bright: '\x1b[1m',
    dim: '\x1b[2m',
    red: '\x1b[31m',
    green: '\x1b[32m',
    yellow: '\x1b[33m',
    blue: '\x1b[34m',
    magenta: '\x1b[35m',
    cyan: '\x1b[36m',
};

const c = (str: string, color: string): string => `${color}${str}${colors.reset}`;

// ─── Config ──────────────────────────────────────────────────────────────────

const CONFIG = {
    dataDir: path.join(os.homedir(), '.water_tracker'),
    dataFile: 'data.json',
    defaultGoal: 2000,
    maxAmount: 10000,
};

// ─── Data Manager ──────────────────────────────────────────────────────────

class WaterTracker {
    private rl: readline.Interface;
    private goal: number;
    private entries: Entry[];

    constructor() {
        this.rl = readline.createInterface({ input: process.stdin, output: process.stdout });
        const data = this._load();
        this.goal = data.goal || CONFIG.defaultGoal;
        this.entries = data.entries || [];
    }

    private _getDataPath(): string {
        if (!fs.existsSync(CONFIG.dataDir)) fs.mkdirSync(CONFIG.dataDir, { recursive: true });
        return path.join(CONFIG.dataDir, CONFIG.dataFile);
    }

    private _load(): Data {
        const filePath = this._getDataPath();
        if (fs.existsSync(filePath)) {
            try {
                return JSON.parse(fs.readFileSync(filePath, 'utf8'));
            } catch (_) {
                return { goal: CONFIG.defaultGoal, entries: [] };
            }
        }
        return { goal: CONFIG.defaultGoal, entries: [] };
    }

    private _save(): void {
        const filePath = this._getDataPath();
        fs.writeFileSync(filePath, JSON.stringify({ goal: this.goal, entries: this.entries }, null, 2));
    }

    private _today(): string {
        return new Date().toISOString().split('T')[0];
    }

    private _getTodayEntries(): Entry[] {
        const today = this._today();
        return this.entries.filter(e => e.date === today);
    }

    private _getTodayTotal(): number {
        return this._getTodayEntries().reduce((sum, e) => sum + e.amount, 0);
    }

    private _getLastNDays(n: number = 7): { date: string; total: number }[] {
        const today = new Date();
        const result: { date: string; total: number }[] = [];
        for (let i = n - 1; i >= 0; i--) {
            const d = new Date(today);
            d.setDate(d.getDate() - i);
            const dateStr = d.toISOString().split('T')[0];
            const total = this.entries.filter(e => e.date === dateStr).reduce((s, e) => s + e.amount, 0);
            result.push({ date: dateStr, total });
        }
        return result;
    }

    private _progressBar(current: number, goal: number, width: number = 20): string {
        if (goal <= 0) return '⚠️  Goal not set';
        const ratio = Math.min(current / goal, 1);
        const filled = Math.floor(ratio * width);
        return `[${'█'.repeat(filled)}${'░'.repeat(width - filled)}] ${(ratio * 100).toFixed(1)}%`;
    }

    private _ask(prompt: string): Promise<string> {
        return new Promise(resolve => this.rl.question(prompt, resolve));
    }

    private async _askInt(prompt: string): Promise<number> {
        while (true) {
            const ans = await this._ask(prompt);
            const num = parseInt(ans.trim());
            if (!isNaN(num)) return num;
            console.log(c('❌ Please enter a number.', colors.red));
        }
    }

    private async _askConfirm(prompt: string): Promise<boolean> {
        const ans = await this._ask(prompt + ' (yes/no): ');
        return ans.trim().toLowerCase() === 'yes';
    }

    // ─── Core Actions ─────────────────────────────────────────────────────

    async addEntry(amount: number): Promise<void> {
        if (amount <= 0) {
            console.log(c('❌ Amount must be positive!', colors.red));
            return;
        }
        if (amount > CONFIG.maxAmount) {
            console.log(c(`⚠️  That's a lot! Max ${CONFIG.maxAmount} ml.`, colors.yellow));
            if (!await this._askConfirm('Continue anyway?')) return;
        }
        this.entries.push({
            date: this._today(),
            amount,
            timestamp: new Date().toISOString()
        });
        this._save();
        const todayTotal = this._getTodayTotal();
        console.log(c(`✅ Added ${amount}ml (Total today: ${todayTotal}ml)`, colors.green));
        if (todayTotal >= this.goal) {
            console.log(c('🎉 Goal achieved! Stay hydrated! 💪', colors.cyan));
        }
    }

    showToday(): void {
        const todayTotal = this._getTodayTotal();
        const entries = this._getTodayEntries();
        console.log('\n' + c('═'.repeat(50), colors.dim));
        console.log(c('💧 TODAY\'S HYDRATION', colors.bright + colors.cyan));
        console.log(c('═'.repeat(50), colors.dim));
        console.log(`  Goal:      ${c(this.goal + 'ml', colors.cyan)}`);
        console.log(`  Consumed:  ${c(todayTotal + 'ml', colors.green)}`);
        console.log(`  Remaining: ${c(Math.max(this.goal - todayTotal, 0) + 'ml', colors.yellow)}`);
        console.log(`  Progress:  ${this._progressBar(todayTotal, this.goal)}`);
        console.log(c('═'.repeat(50), colors.dim));
        if (entries.length) {
            console.log('  Entries:');
            entries.forEach((e, i) => {
                const ts = e.timestamp ? e.timestamp.slice(11, 16) : '—';
                console.log(`    ${i+1}. ${ts} → ${c(e.amount + 'ml', colors.green)}`);
            });
        } else {
            console.log(c('  No entries yet today. Drink up! 💧', colors.dim));
        }
    }

    showChart(days: number = 7): void {
        const history = this._getLastNDays(days);
        const maxVal = Math.max(...history.map(d => d.total), 0);
        if (maxVal === 0) {
            console.log(c('No data to chart.', colors.yellow));
            return;
        }
        const chartWidth = Math.min(40, Math.max(10, Math.floor(maxVal / 50) + 1));
        const scale = maxVal / chartWidth;
        console.log(`\n${c(`📈 Hydration Chart (last ${days} days)`, colors.bright + colors.cyan)}`);
        history.forEach(item => {
            const barLen = Math.floor(item.total / scale);
            const bar = '█'.repeat(barLen) + '░'.repeat(chartWidth - barLen);
            const dateStr = item.date.slice(5, 10);
            const totalStr = `${item.total}ml`.padStart(6);
            console.log(`  ${dateStr} ${bar} ${totalStr}`);
        });
    }

    showStats(): void {
        if (!this.entries.length) {
            console.log(c('📭 No data yet. Start tracking!', colors.yellow));
            return;
        }
        const total = this.entries.reduce((s, e) => s + e.amount, 0);
        const count = this.entries.length;
        const avg = total / count;
        const maxEntry = Math.max(...this.entries.map(e => e.amount));
        const minEntry = Math.min(...this.entries.map(e => e.amount));
        const daysTracked = new Set(this.entries.map(e => e.date)).size;
        console.log('\n📊 STATISTICS');
        console.log(c('─'.repeat(30), colors.dim));
        console.log(`  Total Consumed: ${total}ml`);
        console.log(`  Total Entries:  ${count}`);
        console.log(`  Days Tracked:   ${daysTracked}`);
        console.log(`  Average per Day: ${avg.toFixed(1)}ml`);
        console.log(`  Max Entry:      ${maxEntry}ml`);
        console.log(`  Min Entry:      ${minEntry}ml`);
        console.log(`  Daily Goal:     ${this.goal}ml`);
    }

    async setGoal(goal: number): Promise<void> {
        if (goal <= 0) {
            console.log(c('❌ Goal must be positive!', colors.red));
            return;
        }
        this.goal = goal;
        this._save();
        console.log(c(`✅ Daily goal set to ${goal}ml`, colors.green));
    }

    async clearData(): Promise<void> {
        if (!await this._askConfirm('⚠️  Delete ALL data? This cannot be undone!')) return;
        this.entries = [];
        this.goal = CONFIG.defaultGoal;
        this._save();
        console.log(c('🗑️  All data cleared.', colors.yellow));
    }

    // ─── Menu ─────────────────────────────────────────────────────────────

    private async _showMenu(): Promise<void> {
        const todayTotal = this._getTodayTotal();
        const progress = this._progressBar(todayTotal, this.goal);
        console.log('\n' + c('═'.repeat(50), colors.cyan));
        console.log(c('💧 WATER TRACKER – Hydration Dashboard', colors.bright + colors.cyan));
        console.log(c('═'.repeat(50), colors.cyan));
        console.log(`  Today: ${todayTotal}ml / ${this.goal}ml  ${progress}`);
        console.log(c('─'.repeat(50), colors.dim));
        console.log('  1. 💧 Add water intake');
        console.log('  2. 📊 Today\'s progress');
        console.log('  3. 📈 Show chart (last 7 days)');
        console.log('  4. 📊 Statistics');
        console.log(`  5. 🎯 Set daily goal (current: ${this.goal}ml)`);
        console.log('  6. 🗑️  Clear all data');
        console.log('  0. 🚪 Exit');
        console.log(c('═'.repeat(50), colors.cyan));
    }

    async run(): Promise<void> {
        console.clear();
        console.log(c('\n💧 Water Tracker – Hydration Dashboard', colors.bright + colors.cyan));
        console.log(c('Visualize your hydration journey!', colors.dim));

        while (true) {
            await this._showMenu();
            const choice = await this._ask('Your choice: ');
            switch (choice.trim()) {
                case '1': {
                    const amount = await this._askInt('Amount in ml: ');
                    await this.addEntry(amount);
                    break;
                }
                case '2': this.showToday(); break;
                case '3': this.showChart(); break;
                case '4': this.showStats(); break;
                case '5': {
                    const goal = await this._askInt('New daily goal (ml): ');
                    await this.setGoal(goal);
                    break;
                }
                case '6': await this.clearData(); break;
                case '0':
                    console.log(c('👋 Stay hydrated! Goodbye!', colors.cyan));
                    this.rl.close();
                    return;
                default:
                    console.log(c('❌ Invalid choice.', colors.red));
            }
            if (choice !== '0') {
                console.log('\nPress Enter to continue...');
                await this._ask('');
            }
        }
    }
}

// ─── Main ────────────────────────────────────────────────────────────────────

const main = async (): Promise<void> => {
    try {
        const app = new WaterTracker();
        await app.run();
    } catch (e: any) {
        console.error(c(`❌ Unexpected error: ${e.message}`, colors.red));
        process.exit(1);
    }
};

main();
