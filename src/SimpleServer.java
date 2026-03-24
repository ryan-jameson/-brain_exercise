import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class SimpleServer {
    private static final int PORT = 8080;
    private static final String PUBLIC_DIR = "public";
    private static final String CGI_DIR = "cgi-bin";
    private static final Path LOG_FILE = Path.of("server_log.txt");

    private static JTextArea logArea;
    private static JLabel requestCountLabel;
    private static JLabel connectionCountLabel;
    private static final AtomicInteger totalRequests = new AtomicInteger();
    private static final AtomicInteger activeConnections = new AtomicInteger();

    public static void main(String[] args) {
        boolean headless = args.length > 0 && "headless".equalsIgnoreCase(args[0]);
        if (!headless) {
            SwingUtilities.invokeLater(SimpleServer::initUi);
        }
        File baseDir = resolveBaseDir();
        log("[Server] Base directory: " + baseDir.getAbsolutePath());
        log("[Server] Listening on port " + PORT + "...");

    Thread serverThread = new Thread(() -> runServer(baseDir), "server-thread");
    serverThread.start();
    }

    private static void initUi() {
        JFrame frame = new JFrame("SimpleServer 控制台");
        logArea = new JTextArea(20, 80);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        requestCountLabel = new JLabel("请求数: 0");
        connectionCountLabel = new JLabel("连接数: 0");
        JButton exportButton = new JButton("导出日志");
        exportButton.addActionListener(event -> exportLogs(frame));

        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statsPanel.add(requestCountLabel);
        statsPanel.add(connectionCountLabel);
        statsPanel.add(exportButton);

        frame.setLayout(new BorderLayout());
        frame.add(statsPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(logArea), BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void runServer(File baseDir) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("[Server] Listening on port " + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                log("[Server] Connection from " + client.getInetAddress());

                // 异步处理，使得主线程可以继续 accept，并且 UI 能够刷新显示 activeConnections
                new Thread(() -> {
                    activeConnections.incrementAndGet();
                    updateStats();
                    try {
                        handleClient(client, baseDir);
                    } catch (Exception e) {
                        log("[Run Error] " + e.getMessage());
                    } finally {
                        activeConnections.decrementAndGet();
                        updateStats();
                        try {
                            if (!client.isClosed()) client.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            log("[Server] Error: " + e.getMessage());
        }
    }

    private static void handleClient(Socket client, File baseDir) {
        try (Socket socket = client;
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {

              String requestLine = reader.readLine();
              // log("[Request] " + requestLine);
              if (requestLine == null || requestLine.isBlank()) {
                  return;
              }
              int current = totalRequests.incrementAndGet();
              updateStats();
              log("[Server] " + LocalDateTime.now() + " " + requestLine);

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendSimpleResponse(output, 400, "Bad Request", "Invalid request line");
                return;
            }

            String method = parts[0].toUpperCase(Locale.ROOT);
            String rawPath = parts[1];
            String[] pathParts = rawPath.split("\\?", 2);
            String path = URLDecoder.decode(pathParts[0], StandardCharsets.UTF_8);
            String query = pathParts.length > 1 ? pathParts[1] : "";

            Map<String, String> headers = readHeaders(reader);
            String body = "";
            if ("POST".equals(method)) {
                body = readRequestBody(reader, headers);
            }

            if (!"GET".equals(method) && !"POST".equals(method)) {
                sendSimpleResponse(output, 405, "Method Not Allowed", "Only GET/POST supported");
                return;
            }

            if ("/".equals(path) || path.isBlank()) {
                path = "/index.html";
            }

            if (path.startsWith("/" + CGI_DIR + "/")) {
                handleCgi(path.substring(1 + CGI_DIR.length() + 1), query, body, baseDir, output);
            } else {
                if ("POST".equals(method)) {
                    sendSimpleResponse(output, 405, "Method Not Allowed", "POST only supported for CGI");
                    return;
                }
                handleStatic(path, baseDir, output);
            }
        } catch (IOException e) {
            log("[Server] Client error: " + e.getMessage());
        }
    }

    private static Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static String readRequestBody(BufferedReader reader, Map<String, String> headers) throws IOException {
        String lengthValue = headers.getOrDefault("content-length", "0");
        int length;
        try {
            length = Integer.parseInt(lengthValue);
        } catch (NumberFormatException e) {
            length = 0;
        }
        if (length <= 0) {
            return "";
        }
        char[] body = new char[length];
        int read = reader.read(body);
        if (read <= 0) {
            return "";
        }
        return new String(body, 0, read);
    }

    private static void log(String message) {
        String line = message + System.lineSeparator();
        System.out.print(line);
        appendLogFile(line);
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> logArea.append(line));
        }
    }

    private static void updateStats() {
        if (requestCountLabel == null || connectionCountLabel == null) {
            return;
        }
        int requestCount = totalRequests.get();
        int connectionCount = activeConnections.get();
        SwingUtilities.invokeLater(() -> {
            requestCountLabel.setText("请求数: " + requestCount);
            connectionCountLabel.setText("连接数: " + connectionCount);
        });
    }

    private static void exportLogs(JFrame frame) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出日志");
        chooser.setSelectedFile(new File("server_log_export.txt"));
        int result = chooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path target = chooser.getSelectedFile().toPath();
            try {
                Files.copy(LOG_FILE, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log("[Server] Logs exported to: " + target.toAbsolutePath());
            } catch (IOException e) {
                log("[Server] Export failed: " + e.getMessage());
            }
        }
    }

    private static void appendLogFile(String line) {
        try {
            Files.writeString(LOG_FILE, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[Server] Failed to write log file: " + e.getMessage());
        }
    }

    private static void handleStatic(String path, File baseDir, OutputStream output) throws IOException {
        Path filePath = Path.of(baseDir.getAbsolutePath(), PUBLIC_DIR, path.replace("/", File.separator));
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendSimpleResponse(output, 404, "Not Found", "File not found");
            return;
        }

        byte[] data = Files.readAllBytes(filePath);
        String header = "HTTP/1.0 200 OK\r\n" +
                "Content-Type: " + guessContentType(filePath) + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(data);
        output.flush();
    }

    private static void handleCgi(String scriptName, String query, String body, File baseDir, OutputStream output) throws IOException {
        File scriptFile = resolveScript(baseDir, scriptName);
        if (scriptFile == null || !scriptFile.exists()) {
            sendSimpleResponse(output, 404, "Not Found", "CGI script not found");
            return;
        }
        String arg = extractArg(query, body);
        Process process = startCgiProcess(scriptFile, arg);
        if (body != null && !body.isBlank()) {
            process.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }
        process.getOutputStream().close();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream processOut = process.getInputStream()) {
            processOut.transferTo(buffer);
        }

        byte[] payload = buffer.toByteArray();
        String header = "HTTP/1.0 200 OK\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: " + payload.length + "\r\n" +
                "\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(payload);
        output.flush();
    }

    private static File resolveScript(File baseDir, String scriptName) {
        File direct = new File(baseDir, CGI_DIR + File.separator + scriptName);
        if (direct.exists()) {
            return direct;
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            File bat = new File(baseDir, CGI_DIR + File.separator + scriptName + ".bat");
            if (bat.exists()) {
                return bat;
            }
        } else {
            File sh = new File(baseDir, CGI_DIR + File.separator + scriptName + ".sh");
            if (sh.exists()) {
                return sh;
            }
        }
        return direct;
    }

    private static Process startCgiProcess(File scriptFile, String arg) throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String scriptPath = scriptFile.getAbsolutePath();
        if (os.contains("win") && scriptPath.toLowerCase(Locale.ROOT).endsWith(".bat")) {
            if (arg != null && !arg.isBlank()) {
                return Runtime.getRuntime().exec(new String[] { "cmd.exe", "/c", scriptPath, arg });
            }
            return Runtime.getRuntime().exec(new String[] { "cmd.exe", "/c", scriptPath });
        }
        if (arg != null && !arg.isBlank()) {
            return Runtime.getRuntime().exec(new String[] { scriptPath, arg });
        }
        return Runtime.getRuntime().exec(scriptPath);
    }

    private static String extractArg(String query, String body) {
        String candidate = extractQueryValue(query, "topic");
        if (!candidate.isBlank()) {
            return candidate;
        }
        candidate = extractQueryValue(body, "topic");
        if (!candidate.isBlank()) {
            return candidate;
        }
        return extractJsonValue(body, "topic");
    }

    private static String extractQueryValue(String source, String key) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String[] pairs = source.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && key.equalsIgnoreCase(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static String extractJsonValue(String body, String key) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String patternText = "\\\"?" + Pattern.quote(key) + "\\\"?\\s*:\\s*\\\"?([A-Za-z0-9_-]+)\\\"?";
        Pattern pattern = Pattern.compile(patternText);
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static void sendSimpleResponse(OutputStream output, int code, String message, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.0 " + code + " " + message + "\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(data);
        output.flush();
    }

    private static String guessContentType(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html; charset=UTF-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        String detected = Files.probeContentType(filePath);
        return detected != null ? detected : "application/octet-stream";
    }

    private static File resolveBaseDir() {
        File current = new File(System.getProperty("user.dir"));
        if (isProjectRoot(current)) {
            return current;
        }
        File parent = current.getParentFile();
        if (parent != null && isProjectRoot(parent)) {
            return parent;
        }
        return current;
    }

    private static boolean isProjectRoot(File dir) {
        return new File(dir, PUBLIC_DIR).exists() && new File(dir, CGI_DIR).exists();
    }
}
