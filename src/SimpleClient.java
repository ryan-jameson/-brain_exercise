import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Scanner;

public class SimpleClient {
    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final Path LOCAL_RECORDS = Path.of("client_records.txt");

    public static void main(String[] args) {
        String method = "GET";
        String target = args.length > 0 ? args[0] : null;
        String body = "";
        if (args.length > 1 && ("GET".equalsIgnoreCase(args[0]) || "POST".equalsIgnoreCase(args[0]))) {
            method = args[0].toUpperCase();
            target = args[1];
            if (args.length > 2) {
                body = args[2];
            }
        }
        if (target == null || target.isBlank()) {
            try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
                System.out.print("请输入请求路径(例如 /cgi-bin/TaskGenerator 或 /index.html): ");
                target = scanner.nextLine().trim();
                if (target.isBlank()) {
                    target = "/index.html";
                }
            }
        }

        String requestLine;
    if ("POST".equals(method)) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String contentType = body.trim().startsWith("{") ? "application/json" : "application/x-www-form-urlencoded";
        requestLine = "POST " + target + " HTTP/1.0\r\n" +
            "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + bodyBytes.length + "\r\n" +
                    "\r\n" +
                    body;
        } else {
            requestLine = "GET " + target + " HTTP/1.0\r\n\r\n";
        }
        System.out.println("[Client] Sending request: " + requestLine.trim());

        try (Socket socket = new Socket(HOST, PORT);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write(requestLine);
            writer.flush();

            String line;
            System.out.println("[Client] Response:");
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            if (target.contains("TaskGenerator") || target.contains("QuestionSelector")) {
                saveLocalRecord(target);
            }
        } catch (IOException e) {
            System.err.println("[Client] Error: " + e.getMessage());
        }
    }

    private static void saveLocalRecord(String target) {
        String record = LocalDateTime.now() + "\t" + target + System.lineSeparator();
        try {
            Files.writeString(LOCAL_RECORDS, record, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("[Client] Training record saved to client_records.txt");
        } catch (IOException e) {
            System.err.println("[Client] Failed to save record: " + e.getMessage());
        }
    }
}
