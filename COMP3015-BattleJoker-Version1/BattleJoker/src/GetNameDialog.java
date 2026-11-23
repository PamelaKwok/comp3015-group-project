import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class GetNameDialog {
    @FXML
    TextField nameField;

    @FXML
    TextField ipField;

    @FXML
    TextField portField;

    @FXML
    Button goButton;

    Stage stage;
    String playername;
    String ipAddress;
    int port;
    boolean confirmed;

    public GetNameDialog() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("getNameUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Battle Joker");
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        ipField.setText("127.0.0.1");
        portField.setText("12345");

        goButton.setOnMouseClicked(this::OnButtonClick);

        stage.showAndWait();
    }

    @FXML
    void OnButtonClick(Event event) {
        playername = nameField.getText().trim();
        ipAddress = ipField.getText().trim();
        String portText = portField.getText().trim();
        if (playername.isEmpty() || ipAddress.isEmpty() || portText.isEmpty()) {
            return;
        }
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            return;
        }
        confirmed = true;
        stage.close();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getPlayername() {
        return playername;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }
}