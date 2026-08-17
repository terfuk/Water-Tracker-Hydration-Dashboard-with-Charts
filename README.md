💧 Water Tracker – Hydration Dashboard with Charts
"Visualize your hydration journey – track daily intake, see trends, and hit your goals with beautiful ASCII charts!"

📋 Table of Contents
✨ Features

📁 Repository Structure

🚀 Quick Start

💻 Language Implementations

📊 Data Format

🤝 Contributing

📄 License

✨ Features
Feature	Description
💧 Log Daily Intake	Add your water consumption in ml with automatic timestamp
📈 ASCII Bar Chart	See a beautiful horizontal bar chart of your last 7 days
📊 Statistics	View total, average, min, max, and days tracked
🎯 Daily Goal	Set and track a personalized hydration target (default 2000 ml)
💾 Persistence	All data saved locally in JSON format
🎨 Colorful CLI	Enhanced terminal output with ANSI colors and emojis
⚡ Cross‑Platform	Works on Windows, macOS, and Linux
📁 Repository Structure
text
water-tracker-charts/
├── README.md
├── python/
│   └── water_tracker.py
├── javascript/
│   └── water_tracker.js
├── typescript/
│   └── water_tracker.ts
├── go/
│   └── water_tracker.go
├── rust/
│   └── water_tracker.rs
├── cpp/
│   └── water_tracker.cpp
├── java/
│   └── WaterTracker.java
└── csharp/
    └── WaterTracker.cs
🚀 Quick Start
Prerequisites
Each language requires its respective runtime/compiler (see individual sections)

Clone & Run
bash
git clone https://github.com/yourusername/water-tracker-charts.git
cd water-tracker-charts
# Navigate to your language folder and run
💻 Language Implementations
1. 🐍 Python
bash
cd python
pip install rich
python water_tracker.py
Requires: Python 3.8+

2. 🟨 JavaScript (Node.js)
bash
cd javascript
node water_tracker.js
Requires: Node.js 16+

3. 🟦 TypeScript
bash
cd typescript
npm install -g ts-node
ts-node water_tracker.ts
Requires: Node.js 16+, TypeScript

4. 🟩 Go
bash
cd go
go run water_tracker.go
Requires: Go 1.18+

5. 🦀 Rust
bash
cd rust
cargo run
Requires: Rust 1.70+ (dependencies: serde, serde_json, chrono, colored)

6. ⚙️ C++
bash
cd cpp
g++ -std=c++17 water_tracker.cpp -o water_tracker
./water_tracker
Requires: C++17 compiler

7. ☕ Java
bash
cd java
javac WaterTracker.java
java WaterTracker
Requires: JDK 17+

8. 🔷 C#
bash
cd csharp
dotnet run
Requires: .NET 6.0+

📊 Data Format
All implementations use a unified JSON schema:

json
{
  "goal": 2000,
  "entries": [
    {
      "date": "2026-08-17",
      "amount": 250,
      "timestamp": "2026-08-17T08:30:00Z"
    }
  ]
}
Data is stored in the user's home directory under .water_tracker/data.json.

🤝 Contributing
Contributions are welcome! Please:

Fork the repository

Create a feature branch

Commit your changes

Open a Pull Request

📄 License
MIT © 2026 Water Tracker Team
