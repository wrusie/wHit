<div align="center">

  # ⚔️ wHit - PvP Combo System
  
  **Lightweight. Optimized. Satisfying.**
  
  ![Java](https://img.shields.io/badge/Java-21%2B-ed8b00?style=for-the-badge&logo=java&logoColor=white)
  ![Paper](https://img.shields.io/badge/API-Paper%201.21-F7CF0D?style=for-the-badge&logo=papermc&logoColor=white)
  ![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)
  ![Version](https://img.shields.io/badge/Version-1.0-green?style=for-the-badge)

</div>

---

## 🚀 Overview

**wHit** is a highly optimized PvP enhancement plugin designed for Practice and Survival servers. It provides satisfying audio feedback and visual indicators when players perform combos.

Unlike other heavy plugins, **wHit** is designed with performance in mind, ensuring 0% lag impact on your server tick rate.

## ✨ Features

* **⚡ Optimized Performance:** Uses `O(1)` HashMap lookups and efficient memory management.
* **🔊 Satisfying Sounds:** Different sounds for normal combos and critical hits.
* **🎯 Smart Detection:** Combo streak starts after the 2nd hit to prevent spam noise.
* **🎨 Hex Color Support:** Full RGB support (e.g., `&#FFA500`) for Action Bar messages.
* **⏱️ Auto-Reset:** Streak resets automatically if no hit occurs within 1 second.
* **🌍 Localization:** Fully configurable messages via `messages.yml`.

## 📥 Installation

1.  Download the latest `.jar` from the [**Releases**](https://github.com/wrusie/wHit/releases) tab.
2.  Drop the file into your server's `plugins` folder.
3.  Restart your server.
4.  Edit `config.yml` and `messages.yml` to your liking.
5.  Run `/whit reload` to apply changes.

## ⚙️ Configuration

<details>
<summary>📄 config.yml (Click to view)</summary>

```yaml
sounds:
  combo-hit:
    sound: "ENTITY_EXPERIENCE_ORB_PICKUP"
    volume: 1.0
    pitch: 1.5
  
  crit-hit:
    sound: "ENTITY_ARROW_HIT_PLAYER"
    volume: 1.0
    pitch: 0.5
```
</details>

<details> <summary>💬 messages.yml (Click to view)</summary>

```yaml
reload-success: "&#00FF00&lwHit &8» &fConfiguration files have been &#00FF00&nsuccessfully&f reloaded."
no-permission: "&c&lwHit &8» &cYou do not have permission to use this command!"
command-usage: "&e&lwHit &8» &7Usage: &f/whit reload"
actionbar-format: "&#FFA500&lCOMBO: &e&l%combo%"
```
</details>
