import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class BrowserClient extends Application {
    private static final String DEFAULT_URL = "http://localhost:8080/";

    @Override
    public void start(Stage stage) {
        String targetUrl = getParameters().getRaw().isEmpty()
                ? DEFAULT_URL
                : getParameters().getRaw().get(0);

        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        engine.load(targetUrl);

        Button reloadButton = new Button("🔄 刷新");
        reloadButton.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Microsoft YaHei', sans-serif;");
        reloadButton.setOnAction(event -> engine.reload());

        Label statusLabel = new Label("🌐 连接: " + targetUrl);
        statusLabel.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Microsoft YaHei', sans-serif;");
        engine.locationProperty().addListener((obs, oldVal, newVal) -> statusLabel.setText("🌐 连接: " + newVal));

        ToolBar toolBar = new ToolBar(reloadButton, statusLabel);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Microsoft YaHei', sans-serif;");
        root.setTop(toolBar);
        root.setCenter(webView);

        Scene scene = new Scene(root, 1024, 720);
        stage.setTitle("🧠 NeuroFlex 客户端浏览器");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
