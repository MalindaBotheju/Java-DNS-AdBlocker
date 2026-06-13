package org.example;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import javax.swing.*;
import java.awt.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final Set<String> blockedDomains = new HashSet<>();
    private static volatile boolean adBlockEnabled = true;

    // --- NEW: A counter to track blocked ads in real-time ---
    private static final AtomicInteger blockedCount = new AtomicInteger(0);
    private static JLabel counterLabel;

    private static void loadBlocklist() {
        System.out.println("⏳ Downloading latest blocklist from StevenBlack...");
        try {
            java.net.URL url = new java.net.URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts");
            java.util.Scanner scanner = new java.util.Scanner(url.openStream());

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("#") || line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                if (parts.length >= 2 && parts[0].equals("0.0.0.0")) {
                    String domain = parts[1] + ".";
                    if (!domain.equals("0.0.0.0.") && !domain.equals("localhost.")) {
                        blockedDomains.add(domain);
                    }
                }
            }
            scanner.close();
            System.out.println("✅ Loaded " + blockedDomains.size() + " domains into the blocklist!");
        } catch (Exception e) {
            System.err.println("❌ Failed to download blocklist: " + e.getMessage());
        }
    }

    // --- UPGRADED: The Visual Dashboard ---
    private static void createUI() {
        JFrame frame = new JFrame("Java Pi-Hole Dashboard");
        frame.setSize(500, 400); // Larger default size
        frame.setMinimumSize(new Dimension(450, 350));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(240, 244, 248)); // Light, clean background

        // Use BoxLayout to stack elements vertically in the center
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);

        // 1. Title
        JLabel titleLabel = new JLabel("DNS Ad-Blocker");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 2. Status Indicator
        JLabel statusLabel = new JLabel("🛡️ ACTIVE");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        statusLabel.setForeground(new Color(34, 139, 34)); // Forest Green
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 3. Live Counter
        counterLabel = new JLabel("Ads Blocked: 0");
        counterLabel.setFont(new Font("SansSerif", Font.PLAIN, 22));
        counterLabel.setForeground(Color.DARK_GRAY);
        counterLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 4. Large Toggle Button
        JButton toggleButton = new JButton("Pause Ad-Blocking");
        toggleButton.setFont(new Font("SansSerif", Font.BOLD, 20));
        toggleButton.setFocusPainted(false);
        toggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggleButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        toggleButton.setMaximumSize(new Dimension(300, 60)); // Make it chunky and clickable

        // Button Click Logic
        toggleButton.addActionListener(e -> {
            adBlockEnabled = !adBlockEnabled;
            if (adBlockEnabled) {
                statusLabel.setText("🛡️ ACTIVE");
                statusLabel.setForeground(new Color(34, 139, 34));
                toggleButton.setText("Pause Ad-Blocking");
            } else {
                statusLabel.setText("⚠️ PAUSED");
                statusLabel.setForeground(new Color(220, 53, 69)); // Crimson Red
                toggleButton.setText("Enable Ad-Blocking");
            }
        });

        // Assemble the UI with spacing
        mainPanel.add(Box.createVerticalGlue()); // Pushes everything to the center
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Space
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Space
        mainPanel.add(counterLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 40))); // Bigger space before button
        mainPanel.add(toggleButton);
        mainPanel.add(Box.createVerticalGlue()); // Pushes everything to the center

        frame.add(mainPanel, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        // 1. Start the DNS server FIRST so it can route the download request
        new Thread(Main::runDnsServer).start();

        // 2. NOW download the blocklist
        loadBlocklist();

        // 3. Show the UI
        SwingUtilities.invokeLater(Main::createUI);
    }

    private static void runDnsServer() {
        ExecutorService threadPool = Executors.newFixedThreadPool(50);
        byte[] buffer = new byte[512];

        try (DatagramSocket socket = new DatagramSocket(8053)) {
            System.out.println("🚀 Multithreaded DNS Ad-Blocker started on port 8053...");

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                final byte[] requestData = Arrays.copyOf(packet.getData(), packet.getLength());
                final InetAddress clientAddress = packet.getAddress();
                final int clientPort = packet.getPort();

                threadPool.submit(() -> {
                    try {
                        Message dnsMessage = new Message(requestData);

                        if (dnsMessage.getQuestion() != null) {
                            String requestedDomain = dnsMessage.getQuestion().getName().toString();

                            if (adBlockEnabled && blockedDomains.contains(requestedDomain)) {
                                System.out.println("🛑 BLOCKED: " + requestedDomain);

                                // --- NEW: Update the UI Counter ---
                                int currentCount = blockedCount.incrementAndGet();
                                if (counterLabel != null) {
                                    SwingUtilities.invokeLater(() -> counterLabel.setText("Ads Blocked: " + currentCount));
                                }

                                Message responseMessage = new Message(dnsMessage.getHeader().getID());
                                responseMessage.getHeader().setFlag(Flags.QR);
                                responseMessage.addRecord(dnsMessage.getQuestion(), Section.QUESTION);

                                Record blockRecord = new ARecord(
                                        dnsMessage.getQuestion().getName(),
                                        DClass.IN,
                                        300,
                                        InetAddress.getByName("0.0.0.0")
                                );
                                responseMessage.addRecord(blockRecord, Section.ANSWER);

                                byte[] responseBytes = responseMessage.toWire();
                                DatagramPacket replyPacket = new DatagramPacket(
                                        responseBytes, responseBytes.length, clientAddress, clientPort
                                );
                                socket.send(replyPacket);

                            } else {
                                DatagramSocket forwardSocket = new DatagramSocket();
                                InetAddress googleDNS = InetAddress.getByName("8.8.8.8");

                                DatagramPacket forwardPacket = new DatagramPacket(requestData, requestData.length, googleDNS, 53);
                                forwardSocket.send(forwardPacket);

                                byte[] responseBuffer = new byte[512];
                                DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
                                forwardSocket.receive(responsePacket);

                                DatagramPacket replyPacket = new DatagramPacket(
                                        responsePacket.getData(), responsePacket.getLength(), clientAddress, clientPort
                                );
                                socket.send(replyPacket);
                                forwardSocket.close();
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error processing query.");
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("❌ Server Error: " + e.getMessage());
        }
    }
}