import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

public class TaskGenerator {
    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
            String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "memory";
            String json;
            switch (mode) {
                case "focus":
                    json = buildFocusTask();
                    break;
                case "memory":
                default:
                    json = buildMemoryTask();
                    break;
            }
            System.out.println(json);
        } catch (Exception e) {
            System.out.println(errorJson("TaskGenerator error: " + e.getMessage()));
        }
    }

    private static String buildMemoryTask() {
        int length = 6 + RANDOM.nextInt(3);
        StringBuilder sequence = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sequence.append(RANDOM.nextInt(10));
            if (i < length - 1) {
                sequence.append(' ');
            }
        }
        return "{" +
                "\"type\":\"memory\"," +
                "\"createdAt\":\"" + LocalDateTime.now() + "\"," +
                "\"prompt\":\"记住下面的数字序列\"," +
                "\"sequence\":\"" + sequence + "\"" +
                "}";
    }

    private static String buildFocusTask() {
        char target = (char) ('A' + RANDOM.nextInt(26));
        StringBuilder distractors = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            char c = (char) ('A' + RANDOM.nextInt(26));
            distractors.append(c);
            if (i < 19) {
                distractors.append(' ');
            }
        }
        return "{" +
                "\"type\":\"focus\"," +
                "\"createdAt\":\"" + LocalDateTime.now() + "\"," +
                "\"prompt\":\"在干扰项中找到目标字符\"," +
                "\"target\":\"" + target + "\"," +
                "\"distractors\":\"" + distractors + "\"" +
                "}";
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
