import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class DataManager {
    private static final String RECORDS_FILE = "records.txt";

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
            String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "save";
            if ("stats".equals(mode) || "heatmap".equals(mode)) {
                System.out.println(buildHeatmapJson());
            } else if ("save".equals(mode)) {
                System.out.println(saveRecord());
            } else {
                System.out.println(errorJson("Unknown mode: " + mode));
            }
        } catch (Exception e) {
            System.out.println(errorJson("DataManager error: " + e.getMessage()));
        }
    }

    private static String saveRecord() throws IOException {
        String payload = readAllStdIn();
        if (payload.isBlank()) {
            return errorJson("Empty POST body");
        }
        String line = LocalDateTime.now() + "\t" + payload.replace("\n", " ").replace("\r", " ");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(RECORDS_FILE, true))) {
            writer.write(line);
            writer.newLine();
        }
        return "{\"status\":\"ok\",\"savedAt\":\"" + LocalDateTime.now() + "\"}";
    }

    private static String buildHeatmapJson() throws IOException {
        Path path = Path.of(RECORDS_FILE);
        Map<LocalDate, Integer> counts = initLastYear();
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\t", 2);
                    if (parts.length == 0) {
                        continue;
                    }
                    try {
                        LocalDate date = LocalDateTime.parse(parts[0]).toLocalDate();
                        if (counts.containsKey(date)) {
                            counts.put(date, counts.get(date) + 1);
                        }
                    } catch (Exception ignore) {
                        // Skip malformed lines
                    }
                }
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{\"data\":[");
        boolean first = true;
        for (Map.Entry<LocalDate, Integer> entry : counts.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"date\":\"")
                    .append(entry.getKey())
                    .append("\",\"count\":")
                    .append(entry.getValue())
                    .append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    private static Map<LocalDate, Integer> initLastYear() {
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 364; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            counts.put(date, 0);
        }
        return counts;
    }

    private static String readAllStdIn() throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static String errorJson(String message) {
        return "{\"error\":\"" + jsonEscape(message) + "\"}";
    }

    private static String jsonEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
