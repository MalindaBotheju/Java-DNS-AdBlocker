# 🛡️ Java DNS Ad-Blocker (Pi-Hole Clone)

A localized, multithreaded DNS Server built in Java with a Swing GUI dashboard that blocks network-level ads, trackers, and malicious telemetry code across your entire Linux operating system.

## 🚀 How It Works
1. **Network Redirection:** A custom Bash startup script configures Linux `iptables` to capture all system-wide outbound DNS requests destined for port `53` and transparently redirects them to a local custom port (`8053`).
2. **Dynamic Ingestion:** Upon startup, the core Java engine pulls down the official live host definitions list from the StevenBlack unified blocklist repository.
3. **High-Performance Resolution:** Operating via a `DatagramSocket`, the server spawns decoupled execution threads to parse incoming packet queries against an internal `HashSet`.
    * **If Blocked:** Returns a `127.0.0.1` loopback response directly to the system.
    * **If Allowed:** Proxies the request cleanly upstream to Google DNS (`8.8.8.8`).
4. **Graceful Teardown:** Closing the GUI alerts a shutdown sequence that reverses system network variables (`resolvectl revert`) back to default ISP configurations instantly.

## 🛠️ Architecture Stack
* **Language/Platform:** Java 21+, Maven
* **Networking Layer:** UDP Datagram Sockets, `iptables` NAT Tables, `systemd-resolved` engine
* **GUI Engine:** Java Swing

## 📦 Quick Start (Run Pre-compiled Release)
If you don't want to build from source code, you can run the application directly:
1. Navigate to the **Releases** tab on this repository and download the `JavaDNSAdBlocker-1.0-SNAPSHOT-jar-with-dependencies.jar` along with the `start-adblocker.sh` script into the same folder.
2. Open a terminal in that directory and execute:
   ```bash
   chmod +x start-adblocker.sh
   sudo ./start-adblocker.sh

## ⚙️ Build & Run from Source Code
Ensure your runtime environment uses Linux (Ubuntu/Debian supported) and has Java 21+ installed along with Maven capabilities.

Clone and compile the binary layout:
```bash
mvn clean package
```

Run the deployment automation wrapper with root privileges:
```bash
chmod +x start-adblocker.sh
```
```bash
sudo ./start-adblocker.sh
```

You officially have a bulletproof repository ready for your resume. Incredible job finishing this build!
