import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WebServer {
    private static final int PORT = 8080;
    private static final String PUBLIC_DIR = "public";
    private static final String CGI_PREFIX = "/cgi-bin/";

    public static void main(String[] args) throws IOException {
        File baseDir = resolveBaseDir();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("HTTP/1.0 server started on port " + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                handleClient(client, baseDir);
            }
        }
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
        return new File(dir, PUBLIC_DIR).exists() && new File(dir, "cgi-bin").exists();
    }

    private static void handleClient(Socket client, File baseDir) {
        try (Socket socket = client;
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendSimpleResponse(output, 400, "Bad Request", "Invalid request line");
                return;
            }

            String method = parts[0].toUpperCase(Locale.ROOT);
            String rawPath = parts[1];
            String[] targetParts = rawPath.split("\\?", 2);
            String path = URLDecoder.decode(targetParts[0], StandardCharsets.UTF_8);
            String query = targetParts.length > 1 ? targetParts[1] : "";

            Map<String, String> headers = readHeaders(reader);
            String requestBody = "";
            if ("POST".equals(method)) {
                requestBody = readRequestBody(reader, headers);
            }

            if (path.startsWith(CGI_PREFIX)) {
                handleCgi(path, query, baseDir, output, requestBody);
            } else {
                handleStatic(path, baseDir, output);
            }
        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
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
        if (length > 0) {
            char[] body = new char[length];
            int read = reader.read(body);
            if (read > 0) {
                return new String(body, 0, read);
            }
        }
        return "";
    }

    private static void handleStatic(String path, File baseDir, OutputStream output) throws IOException {
        if ("/".equals(path)) {
            path = "/index.html";
        }
        Path filePath = Path.of(baseDir.getAbsolutePath(), PUBLIC_DIR, path.replace("/", File.separator));
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendSimpleResponse(output, 404, "Not Found", "Resource not found");
            return;
        }

        byte[] data = Files.readAllBytes(filePath);
        String contentType = guessContentType(filePath);
        String header = "HTTP/1.0 200 OK\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(data);
        output.flush();
    }

    private static void handleCgi(String path, String query, File baseDir, OutputStream output, String requestBody) throws IOException {
        String scriptName = path.substring(CGI_PREFIX.length());
        if (scriptName.isBlank()) {
            sendSimpleResponse(output, 400, "Bad Request", "Missing CGI script");
            return;
        }
        File scriptFile = resolveScript(baseDir, scriptName);
        if (scriptFile == null || !scriptFile.exists()) {
            System.err.println("CGI script not found: " + scriptName + " (baseDir=" + baseDir.getAbsolutePath() + ")");
            sendSimpleResponse(output, 404, "Not Found", "CGI script not found");
            return;
        }

        String arg = extractArg(query);
        Process process = startCgiProcess(scriptFile, arg);
        if (requestBody != null && !requestBody.isEmpty()) {
            process.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));
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
        File direct = new File(baseDir, "cgi-bin" + File.separator + scriptName);
        if (direct.exists()) {
            return direct;
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            File bat = new File(baseDir, "cgi-bin" + File.separator + scriptName + ".bat");
            if (bat.exists()) {
                return bat;
            }
        } else {
            File sh = new File(baseDir, "cgi-bin" + File.separator + scriptName + ".sh");
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

    private static String extractArg(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0];
                String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                if ("type".equalsIgnoreCase(key) || "mode".equalsIgnoreCase(key)) {
                    return value;
                }
            }
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
        if (fileName.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        String detected = Files.probeContentType(filePath);
        return detected != null ? detected : "application/octet-stream";
    }
}
