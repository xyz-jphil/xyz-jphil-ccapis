package xyz.jphil.ccapis.proxy;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/**
 * Manages system tray icon for CCAPI Proxy Server.
 *
 * <p>Provides:
 * <ul>
 *   <li>System tray icon with status indicator</li>
 *   <li>Right-click menu with server information and exit option</li>
 *   <li>Graceful shutdown when exit is clicked</li>
 *   <li>Headless-safe operation (won't fail if system tray not supported)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * var trayManager = new SystemTrayManager(8080);
 * trayManager.show(() -> {
 *     // Cleanup code before exit
 *     proxy.stop();
 *     System.exit(0);
 * });
 * </pre>
 */
public class SystemTrayManager {

    private final int port;
    private TrayIcon trayIcon;
    private boolean isShown = false;

    /**
     * Create system tray manager for proxy on given port
     */
    public SystemTrayManager(int port) {
        this.port = port;
    }

    /**
     * Show system tray icon with exit handler
     *
     * @param onExit Runnable to execute when user clicks Exit (should call proxy.stop() and System.exit(0))
     * @return true if system tray is supported and icon was shown, false otherwise
     */
    public boolean show(Runnable onExit) {
        // Check if system tray is supported
        if (!SystemTray.isSupported()) {
            System.err.println("[SYSTEM TRAY] System tray is not supported on this platform");
            return false;
        }

        // Check if we're running in headless mode
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("[SYSTEM TRAY] Running in headless mode, system tray unavailable");
            return false;
        }

        try {
            // Get the system tray
            var systemTray = SystemTray.getSystemTray();

            // Create tray icon image (simple colored circle)
            var image = createTrayIconImage();

            // Create popup menu
            var popup = new PopupMenu();

            // Add menu items
            var titleItem = new MenuItem("CCAPI Proxy Server");
            titleItem.setEnabled(false); // Make it non-clickable (just info)
            popup.add(titleItem);

            popup.addSeparator();

            var statusItem = new MenuItem("Status: Running");
            statusItem.setEnabled(false);
            popup.add(statusItem);

            var portItem = new MenuItem("Port: " + port);
            portItem.setEnabled(false);
            popup.add(portItem);

            popup.addSeparator();

            // Add exit option
            var exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                System.out.println("\n[SYSTEM TRAY] Exit requested by user");
                if (onExit != null) {
                    onExit.run();
                }
            });
            popup.add(exitItem);

            // Create tray icon
            trayIcon = new TrayIcon(image, "CCAPI Proxy Server (Port: " + port + ")", popup);
            trayIcon.setImageAutoSize(true);

            // Add double-click handler (shows info)
            trayIcon.addActionListener(e -> {
                trayIcon.displayMessage(
                    "CCAPI Proxy Server",
                    "Running on port " + port + "\nRight-click for options",
                    TrayIcon.MessageType.INFO
                );
            });

            // Add to system tray
            systemTray.add(trayIcon);

            isShown = true;
            System.out.println("[SYSTEM TRAY] System tray icon added successfully");

            // Show initial notification
            trayIcon.displayMessage(
                "CCAPI Proxy Server",
                "Server started on port " + port,
                TrayIcon.MessageType.INFO
            );

            return true;

        } catch (AWTException e) {
            System.err.println("[SYSTEM TRAY] Failed to add system tray icon: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Remove the system tray icon
     */
    public void hide() {
        if (isShown && trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
                isShown = false;
                System.out.println("[SYSTEM TRAY] System tray icon removed");
            } catch (Exception e) {
                System.err.println("[SYSTEM TRAY] Error removing tray icon: " + e.getMessage());
            }
        }
    }

    /**
     * Display a notification message
     */
    public void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (isShown && trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }

    /**
     * Create a simple colored circle icon for the system tray
     */
    private BufferedImage createTrayIconImage() {
        // Get tray icon size (usually 16x16 or 32x32 depending on OS)
        var trayIconSize = SystemTray.getSystemTray().getTrayIconSize();
        var size = Math.max(16, Math.min(trayIconSize.width, trayIconSize.height));

        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g2d = image.createGraphics();

        // Enable antialiasing for smoother circle
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw green circle (indicates server is running)
        g2d.setColor(new Color(76, 175, 80)); // Material Green
        g2d.fillOval(2, 2, size - 4, size - 4);

        // Draw white border
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawOval(2, 2, size - 4, size - 4);

        g2d.dispose();
        return image;
    }

    /**
     * Check if system tray is supported on this platform
     */
    public static boolean isSupported() {
        return !GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
    }
}
