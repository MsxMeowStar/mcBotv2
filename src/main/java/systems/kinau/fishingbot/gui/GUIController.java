package systems.kinau.fishingbot.gui;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import lombok.Getter;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.Main;
import systems.kinau.fishingbot.event.EventHandler;
import systems.kinau.fishingbot.event.Listener;
import systems.kinau.fishingbot.event.custom.BotStartEvent;
import systems.kinau.fishingbot.gui.config.ConfigGUI;
import systems.kinau.fishingbot.modules.command.executor.ConsoleCommandExecutor;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class GUIController implements Listener {

    @FXML private TextField commandlineTextField;
    @FXML private Button startStopButton;
    @FXML private Button configButton;
    @FXML private ImageView skinPreview;
    @FXML private Label usernamePreview;

    @Getter private final List<String> lastCommands;
    @Getter private int currLastCommandIndex;

    public GUIController() {
        this.lastCommands = new ArrayList<>();
    }

    @FXML
    public void initialize(URL location, ResourceBundle resources) {
    }

    public void exit(Event e) {
        Platform.exit();
    }

    public void deleteAllData(Event e) {
    }

    public void github(Event e) {
        openWebpage("https://github.com/MrKinau/FishingBot");
    }

    public void issues(Event e) {
        openWebpage("https://github.com/MrKinau/FishingBot/issues");
    }

    public void discord(Event e) {
        openWebpage("https://discord.gg/xHpCDYf");
    }

    public void openConfig(Event e) {
        String file;
        if (FishingBot.getInstance().getCurrentBot() == null)
            file = new File(FishingBot.getExecutionDirectory(), "config.json").getPath();
        else
            file = FishingBot.getInstance().getCurrentBot().getConfig().getPath();
        openFile(file);
    }

    public void openLogsDir(Event e) {
        String file;
        if (FishingBot.getInstance().getCurrentBot() == null)
            file = new File(FishingBot.getExecutionDirectory(), "logs/").getPath();
        else
            file = FishingBot.getInstance().getCurrentBot().getLogsFolder().getPath();
        openFile(file);
    }

    public void openLog(Event e) {
        String file;
        if (FishingBot.getInstance().getCurrentBot() == null)
            file = new File(FishingBot.getExecutionDirectory(), "logs/log0.log").getPath();
        else
            file = FishingBot.getInstance().getCurrentBot().getLogsFolder().getPath() + "/log0.log";
        openFile(file);
    }

    public void commandlineSend(Event e) {
        if (getLastCommands().isEmpty() || !getLastCommands().get(getLastCommands().size() - 1).equals(commandlineTextField.getText())) {
            getLastCommands().add(commandlineTextField.getText());
            if (getLastCommands().size() > 1000)
                getLastCommands().remove(0);
        }
        currLastCommandIndex = 0;
        runCommand(commandlineTextField.getText());
        commandlineTextField.setText("");
    }

    public void consoleKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.UP) {
            if (getLastCommands().size() > currLastCommandIndex) {
                currLastCommandIndex++;
                commandlineTextField.setText(getLastCommands().get(getLastCommands().size() - currLastCommandIndex));
                commandlineTextField.end();
            }
        } else if (e.getCode() == KeyCode.DOWN) {
            if (currLastCommandIndex > 0) {
                currLastCommandIndex--;
                if (currLastCommandIndex == 0)
                    commandlineTextField.setText("");
                else
                    commandlineTextField.setText(getLastCommands().get(getLastCommands().size() - currLastCommandIndex));
                commandlineTextField.end();
            }
        }
    }

    private void runCommand(String text) {
        if (FishingBot.getInstance().getCurrentBot() == null) return;
        FishingBot.getInstance().getCurrentBot().runCommand(text, true, new ConsoleCommandExecutor());
    }

    private void openFile(String fileUrl) {
        if (Desktop.isDesktopSupported()) {
            new Thread(() -> {
                try {
                    Desktop.getDesktop().open(new File(fileUrl));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }, "openFile").start();
        }
    }

    public static void openWebpage(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            new Thread(() -> {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            }, "openWebPage").start();
        }
    }

    public void startStop(Event e) {
        if (FishingBot.getInstance().getCurrentBot() == null) {
            startStopButton.setText(FishingBot.getI18n().t("ui-button-stop"));
            new Thread(() -> FishingBot.getInstance().startBot()).start();
        } else {
            startStopButton.setDisable(true);
            startStopButton.setText(FishingBot.getI18n().t("ui-button-start"));
            FishingBot.getInstance().stopBot(true);
        }
    }

    public void updateStartStop() {
        Platform.runLater(() -> {
            if (FishingBot.getInstance().getCurrentBot() == null) {
                startStopButton.setText(FishingBot.getI18n().t("ui-button-start"));
            } else {
                startStopButton.setDisable(true);
                startStopButton.setText(FishingBot.getI18n().t("ui-button-stop"));

            }
        });
    }

    public void enableStartStop() {
        Platform.runLater(() -> {
            startStopButton.setDisable(false);
        });
    }

    public void openConfigGUI(Event e) {
        Stage primaryStage = (Stage) configButton.getScene().getWindow();
        new ConfigGUI(primaryStage);
    }

    public void openAbout(Event e) {
        Dialogs.showAboutWindow((Stage) configButton.getScene().getWindow());
    }

    public void setImage(String uuid) {
        Image noSkinImage = new Image(Main.class.getClassLoader().getResourceAsStream("img/general/noskin.png"));
        Platform.runLater(() -> {
            skinPreview.setFitHeight(180);
            skinPreview.setVisible(true);
            skinPreview.setImage(noSkinImage);
        });
        // 移除外部皮肤获取功能，始终显示默认的 noskin.png
        // skinPreview.setImage(noSkinImage); // 已经在 Platform.runLater 中设置
    }

    public void setAccountName(String name) {
        Platform.runLater(() -> usernamePreview.setText(name));
    }

    @EventHandler
    public void onBotStart(BotStartEvent event) {
        Platform.runLater(() -> {
            deleteAllData(null);
        });
    }

}
