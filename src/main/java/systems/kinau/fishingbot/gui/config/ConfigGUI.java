package systems.kinau.fishingbot.gui.config;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gson.JsonArray;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.OneSixParamStorage;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.gui.MainGUI;
import systems.kinau.fishingbot.gui.config.options.*;
import systems.kinau.fishingbot.io.config.ConvertException;
import systems.kinau.fishingbot.io.config.Property;
import systems.kinau.fishingbot.io.config.PropertyProcessor;
import systems.kinau.fishingbot.io.config.SettingsConfig;
import systems.kinau.fishingbot.modules.ejection.EjectionRule;
import systems.kinau.fishingbot.modules.timer.Timer;
import systems.kinau.fishingbot.utils.ConvertUtils;
import systems.kinau.fishingbot.utils.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConfigGUI {

    public Stage owner;

    public Label configOptionCategoryTitle;
    public VBox configOptionsBox;

    public Multimap<String, ConfigOption> configOptions = MultimapBuilder.hashKeys().arrayListValues().build();

    public ConfigGUI(Stage primaryStage) {
        this.owner = primaryStage;
        Stage window = new Stage();
        createConfigElements(window);
        VBox categoriesPane = new VBox(5);
        VBox configOptionsPane = new VBox(5);

        // init categories

        ListView<ConfigCategory> categoriesList = new ListView<>();
        categoriesList.prefHeightProperty().bind(window.heightProperty());
        categoriesList.getItems().addAll(configOptions.asMap().keySet().stream()
                .map(s -> new ConfigCategory(s, FishingBot.getI18n().t("config-" + s)))
                .sorted(Comparator.comparing(o -> o.translation))
                .collect(Collectors.toList()));

        categoriesList.setOnMouseClicked(new SelectCategoryListener(this));
        categoriesList.getSelectionModel().select(0);


        categoriesPane.getChildren().add(categoriesList);

        // init config options

        configOptionCategoryTitle = new Label();
        configOptionCategoryTitle.setFont(new Font(20));
        configOptionCategoryTitle.setPadding(new Insets(0, 10, 0, 10));
        configOptionsPane.getChildren().add(configOptionCategoryTitle);
        configOptionsBox = new VBox(5);
        configOptionsBox.setPadding(new Insets(5, 20, 5, 20));
        ScrollPane scrollPane = new ScrollPane(configOptionsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getContent().setOnScroll(scrollEvent -> {
            double deltaY = scrollEvent.getDeltaY() * 0.002;
            scrollPane.setVvalue(scrollPane.getVvalue() - deltaY);
        });
        configOptionsBox.getChildren().addAll(configOptions.get(categoriesList.getItems().get(0).getKey()));
        configOptionCategoryTitle.setText(categoriesList.getItems().get(0).getTranslation());
        configOptionsPane.getChildren().add(scrollPane);

        BorderPane rootPane = new BorderPane();
        rootPane.setLeft(categoriesPane);
        rootPane.setCenter(configOptionsPane);

        Scene scene = new Scene(rootPane, 750, 340);
        MainGUI.setStyle(scene.getRoot().getStylesheets());

        window.setTitle("FishingBot - Config");
        window.getIcons().add(new Image(ConfigGUI.class.getClassLoader().getResourceAsStream("img/items/fishing_rod.png")));
        window.setScene(scene);

        window.setMaxHeight(800);
        window.setMinHeight(200);
        window.setMinWidth(500);
        window.setHeight(400);
        window.setWidth(900);

        window.initModality(Modality.WINDOW_MODAL);
        window.initOwner(primaryStage);

        window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, this::saveConfig);

        window.show();
    }

    @SuppressWarnings("unchecked")
    public void createConfigElements(Stage window) {
        SettingsConfig config = FishingBot.getInstance().getConfig();
        boolean usingOneSix = OneSixParamStorage.getInstance() != null;

        if (usingOneSix)
            addConfigOption("account.onesix", new EmptyConfigOption(this, "account.onesix", FishingBot.getI18n().t("config-account-onesix")));
        else {
            addConfigOption("account.microsoft", new EmptyConfigOption(this, "account.microsoft", FishingBot.getI18n().t("config-account-microsoft")));
            boolean refreshTokenExists = FishingBot.getInstance().getRefreshTokenFile().exists();
            addConfigOption("account.microsoft", new ButtonConfigOption(this, "account.microsoft", FishingBot.getI18n().t("config-account-logout"), refreshTokenExists, event -> {
                try {
                    Files.delete(FishingBot.getInstance().getRefreshTokenFile().toPath());
                    ((Node) event.getSource()).setDisable(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        }

        List<Field> fields = ReflectionUtils.getAllFields(config);
        for (Field field : fields) {
            if (!field.isAnnotationPresent(Property.class))
                continue;
            Property propAnnotation = field.getAnnotation(Property.class);

            String key = propAnnotation.key().trim();
            String description = propAnnotation.description().trim();

            if (description.isEmpty())
                continue;
            if (key.isEmpty())
                throw new AnnotationFormatError("Property Annotation needs key");
            if (key.startsWith("account.") && usingOneSix)
                continue;

            if (field.getName().equals("defaultProtocol")) {
                addConfigOption(key, new VersionConfigOption(this, key, FishingBot.getI18n().t(description), ReflectionUtils.getField(field, config).toString()));
            } else if (field.getName().equals("realmId")) {
                addConfigOption(key, new RealmConfigOption(this, key, FishingBot.getI18n().t(description), (long) ReflectionUtils.getField(field, config), this));
            } else if (field.getType().isAssignableFrom(boolean.class)) {
                addConfigOption(key, new BooleanConfigOption(this, key, FishingBot.getI18n().t(description), (boolean) ReflectionUtils.getField(field, config)));
            } else if (field.getType().isAssignableFrom(String.class)) {
                addConfigOption(key, new StringConfigOption(this, key, FishingBot.getI18n().t(description), (String) ReflectionUtils.getField(field, config), field.getName().contains("password")));
            } else if (field.getType().isAssignableFrom(int.class)) {
                addConfigOption(key, new IntegerConfigOption(this, key, FishingBot.getI18n().t(description), (int) ReflectionUtils.getField(field, config)));
            } else if (field.getType().isAssignableFrom(float.class)) {
                addConfigOption(key, new IntegerConfigOption(this, key, FishingBot.getI18n().t(description), Float.valueOf((float) ReflectionUtils.getField(field, config)).intValue()));
            } else if (field.getType().isAssignableFrom(double.class)) {
                addConfigOption(key, new IntegerConfigOption(this, key, FishingBot.getI18n().t(description), Double.valueOf((double) ReflectionUtils.getField(field, config)).intValue()));
            } else if (field.getType().isAssignableFrom(long.class)) {
                addConfigOption(key, new IntegerConfigOption(this, key, FishingBot.getI18n().t(description), Long.valueOf((long) ReflectionUtils.getField(field, config)).intValue()));
            } else if (field.getType().isEnum()) {
                Enum currEnum = (Enum) ReflectionUtils.getField(field, config);
                addConfigOption(key, new EnumConfigOption(this, key, FishingBot.getI18n().t(description), ReflectionUtils.getField(field, config).toString(), (Enum[]) currEnum.getDeclaringClass().getEnumConstants()));
            } else if (field.getType().isAssignableFrom(List.class) && ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(String.class)) {
                List<String> content = (List<String>) ReflectionUtils.getField(field, config);
                addConfigOption(key, new StringArrayConfigOption(this, key, FishingBot.getI18n().t(description), content.toArray(new String[content.size()]), window));
            } else if (field.getType().isAssignableFrom(List.class) && ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(Timer.class)) {
                List<Timer> content = (List<Timer>) ReflectionUtils.getField(field, config);
                addConfigOption(key, new TimersConfigOption(this, key, FishingBot.getI18n().t(description), content, window));
            } else if (field.getType().isAssignableFrom(List.class) && ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(EjectionRule.class)) {
                List<EjectionRule> content = (List<EjectionRule>) ReflectionUtils.getField(field, config);
                addConfigOption(key, new EjectionRulesOption(this, key, FishingBot.getI18n().t(description), content, window));
            }
        }
    }

    private void addConfigOption(String key, ConfigOption configOption) {
        String[] parts = key.split("\\.");
        String category = parts[0];

        String[] subCats = Arrays.copyOfRange(parts, 1, parts.length - 1);

        StringBuilder currPath = new StringBuilder(category).append(".");
        TitledPaneOption lastSubCatPane = null;

        for (String subCat : subCats) {
            currPath.append(subCat);
            if (lastSubCatPane == null && configOptions.containsKey(category) && configOptions.get(category).stream().anyMatch(configOption1 -> configOption1.getKey().equals(currPath.toString()))) {
                lastSubCatPane = (TitledPaneOption) configOptions.get(category).stream().filter(configOption1 -> configOption1.getKey().equals(currPath.toString())).findAny().get();
            } else if (lastSubCatPane != null && lastSubCatPane.contains(currPath.toString())) {
                lastSubCatPane = (TitledPaneOption) lastSubCatPane.get(currPath.toString()).get();
            } else if (lastSubCatPane == null) {
                configOptions.put(category, new ActivateableTitledPaneOption(this, currPath.toString(), configOption.getDescription(), new VBox(5), (boolean) configOption.getValue()));
                return;
            } else {
                lastSubCatPane.getContent().getChildren().add(new ActivateableTitledPaneOption(this, currPath.toString(), configOption.getDescription(), new VBox(5), (boolean) configOption.getValue()));
                return;
            }
            currPath.append(".");
        }

        if (lastSubCatPane == null)
            configOptions.put(category, configOption);
        else
            lastSubCatPane.getContent().getChildren().add(configOption);
    }

    public Optional<ConfigOption> getConfigOption(String key) {
        String[] parts = key.split("\\.");
        String category = parts[0];

        String[] subCats = Arrays.copyOfRange(parts, 1, parts.length - 1);

        StringBuilder currPath = new StringBuilder(category).append(".");
        TitledPaneOption lastSubCatPane = null;

        for (String subCat : subCats) {
            currPath.append(subCat);
            if (lastSubCatPane == null && configOptions.containsKey(category) && configOptions.get(category).stream().anyMatch(configOption1 -> configOption1.getKey().equals(currPath.toString()))) {
                lastSubCatPane = (TitledPaneOption) configOptions.get(category).stream().filter(configOption1 -> configOption1.getKey().equals(currPath.toString())).findAny().get();
            } else if (lastSubCatPane != null && lastSubCatPane.contains(currPath.toString())) {
                lastSubCatPane = (TitledPaneOption) lastSubCatPane.get(currPath.toString()).get();
            }
            currPath.append(".");
        }

        if (lastSubCatPane == null)
            return configOptions.get(category).stream().filter(configOption1 -> configOption1.getKey().equals(key)).findAny();
        else if (lastSubCatPane.contains(key))
            return lastSubCatPane.get(key);
        return Optional.empty();
    }

    private void saveConfig(WindowEvent event) {
        configOptions.values().forEach(ConfigOption::updateValue);

        SettingsConfig config = FishingBot.getInstance().getConfig();

        List<Field> fields = ReflectionUtils.getAllFields(config);
        for (Field field : fields) {
            if (!field.isAnnotationPresent(Property.class))
                continue;
            Property propAnnotation = field.getAnnotation(Property.class);

            String key = propAnnotation.key().trim();
            Optional<ConfigOption> configOption = getConfigOption(key);
            configOption.ifPresent(option -> {
                String value = option.getValue().toString();
                if (option instanceof StringArrayConfigOption) {
                    JsonArray jsonArray = new JsonArray();
                    if (option.getValue() != null && option.getValue().getClass().isArray() && option.getValue() instanceof String[])
                        Arrays.asList((String[]) option.getValue()).forEach(jsonArray::add);
                    value = jsonArray.toString();
                }
                Object typedValue = ConvertUtils.fromConfigValue(value, field.getType(), field.getGenericType());
                if (typedValue == null)
                    throw new ConvertException("Cannot convert type from " + field.getName() + ":" + field.getType().getSimpleName());
                ReflectionUtils.setField(field, config, typedValue);
            });
        }

        new PropertyProcessor().saveConfig(config, new File(config.getPath()));
    }

    @AllArgsConstructor
    class ConfigCategory {
        @Getter
        private String key;
        @Getter
        private String translation;

        @Override
        public String toString() {
            return translation;
        }
    }

}
