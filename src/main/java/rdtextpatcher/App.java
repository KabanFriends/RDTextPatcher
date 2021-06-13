package rdtextpatcher;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.apache.commons.text.translate.*;
import rdtextpatcher.unitypatcher.*;

import org.apache.commons.text.StringEscapeUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App extends Application {

    public boolean working = false;

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("/ui.fxml"));
        primaryStage.setTitle("RDTextPatcher");
        primaryStage.setScene(new Scene(root, 350, 300));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    @FXML
    private TextField resultField;

    @FXML
    public void clickInstall(ActionEvent event) {
        if (!working) {
            File f = new File("Rhythm Doctor_Data/resources.assets");
            if(f.exists() && !f.isDirectory()) {
                installPatch("data/RDLocalization.json", false);
            }else {
                resultField.setText("Rhythm Doctorのデータが見つかりませんでした。");
            }
        }
    }

    @FXML
    public void clickUninstall(ActionEvent event) {
        if (!working) {
            File f = new File("Rhythm Doctor_Data/resources.assets");
            if(f.exists() && !f.isDirectory()) {
                installPatch("data/original/RDLocalization.json", true);
            }else {
                resultField.setText("Rhythm Doctorのデータが見つかりませんでした。");
            }
        }
    }

    private void installPatch(String fileName, boolean uninstall) {
        working = true;
        new Thread(() -> {
            try {
                File dataFile = new File("data");
                File originalFile = new File("data/original");
                File cacheFile = new File("data/cache");
                if (!dataFile.exists()) Files.createDirectory(Paths.get("data"));
                if (!originalFile.exists()) Files.createDirectory(Paths.get("data/original"));
                if (!cacheFile.exists()) Files.createDirectory(Paths.get("data/cache"));

                resultField.setText("言語ファイルを展開中…");

                AssetFile assetFile = new AssetFile(Paths.get("Rhythm Doctor_Data/resources.assets"));
                assetFile.parse();
                HashMap<UnityIndex, UnityAsset> allAssets = assetFile.getAssets();

                ArrayList<UnityAsset> assets = new ArrayList<>();
                UnityIndex[] uIndexes = indexFromPartialName("_English", allAssets);
                for (UnityIndex i : uIndexes) {
                    UnityAsset as = allAssets.get(i);
                    assets.add(as);
                }

                UnityIndex uIndex = indexFromName("RDLocalization", allAssets);
                UnityAsset asset = allAssets.get(uIndex);

                resultField.setText("インデックスを作成中…");

                String s = new String(asset.asTextContent(), StandardCharsets.UTF_8);
                String content = s.substring(s.indexOf('\n')+1);

                Gson gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .disableHtmlEscaping()
                        .create();

                Gson diaGson = new GsonBuilder()
                        .disableHtmlEscaping()
                        .create();

                JsonElement rdlElement = JsonParser.parseString(content);
                JsonObject rdlJson = rdlElement.getAsJsonObject();

                JsonObject outJson = new JsonObject();

                FileCharDetector fd = new FileCharDetector(fileName);

                JsonReader dataReader = new JsonReader(new BufferedReader(new InputStreamReader(new FileInputStream(fileName) ,fd.detector())));
                JsonElement dataElement = JsonParser.parseReader(dataReader);
                JsonObject dataJson = dataElement.getAsJsonObject();

                HashMap<String, String[]> indexes = new HashMap<>();
                for (String key : rdlJson.keySet()) {
                    JsonObject sub = rdlJson.get(key).getAsJsonObject();

                    String keyName = "RDNull";
                    String keyIndex = "";
                    String text = "RDNull";
                    for (String subKey : sub.keySet()) {
                        text = sub.get(subKey).getAsString();
                        if (subKey.endsWith(":1")) {
                            keyName = sub.get(subKey).getAsString();
                        }
                        if (subKey.endsWith(":2")) {
                            if (!keyName.equals("RDNull") || !keyName.equals(" ")) {
                                keyIndex = subKey;
                                outJson.addProperty(keyName, text);
                                String[] pair = new String[2];
                                pair[0] = key;
                                pair[1] = keyIndex;
                                indexes.put(keyName, pair);
                                keyName = "RDNull";
                            }
                        }
                        sub.addProperty(subKey, StringEscapeUtils.escapeJava(text));
                    }
                }

                File f = new File("data/original/RDLocalization.json");
                if (!f.exists() && !f.isDirectory()) {
                    Writer writer = Files.newBufferedWriter(Paths.get("data/original/RDLocalization.json"));
                    gson.toJson(outJson, writer);
                    writer.close();
                }

                for (UnityAsset as : assets) {
                    JsonElement diaElement = JsonParser.parseString(new String(as.asTextContent(), StandardCharsets.UTF_8));
                    JsonObject diaJson = diaElement.getAsJsonObject();

                    JsonObject diaOutJson = new JsonObject();

                    JsonObject root = diaJson.get("root").getAsJsonArray().get(2).getAsJsonObject();
                    for (String key : root.keySet()) {
                        if (root.get(key).isJsonArray()) {
                            JsonArray entryArray = root.get(key).getAsJsonArray();
                            if (entryArray.size() > 0) {
                                JsonObject entryJson = new JsonObject();
                                for (JsonElement element : entryArray) {
                                    if (element.isJsonPrimitive()) {
                                        if (element.getAsJsonPrimitive().isString()) {
                                            String str = element.getAsString();
                                            if (str.startsWith("^") && str.matches(".+ .+")) {
                                                entryJson.addProperty(str.substring(1), str.substring(1));
                                            }
                                        }
                                    }
                                }
                                if (entryJson.size() > 0) {
                                    diaOutJson.add(key, entryJson);
                                }
                            }
                        }
                    }
                    File f2 = new File("data/original/" + getDiaJsonName(as.getTextName()));
                    if (!f2.exists() && !f2.isDirectory()) {
                        Writer writer = Files.newBufferedWriter(Paths.get("data/original/" + getDiaJsonName(as.getTextName())));
                        gson.toJson(diaOutJson, writer);
                        writer.close();
                    }
                }

                resultField.setText("テキストを置き換え中…");

                Set<String> dataEntries = dataJson.keySet();
                for (String key: dataEntries) {
                    String text = dataJson.get(key).getAsString();
                    String[] pair = indexes.get(key);

                    rdlJson.get(pair[0]).getAsJsonObject().addProperty(pair[1], text);
                }
                String finalJsonString = "Japanese Translation - Patched with RDTextPatcher\n" + gson.toJson(rdlJson);
                System.out.println(finalJsonString);
                finalJsonString = finalJsonString.replaceAll("\\\\\\\\([0-9a-fnru])", "\\\\$1");
                System.out.println(finalJsonString);
                byte[] newContent = finalJsonString.getBytes(StandardCharsets.UTF_8);
                asset.replaceTextContent(newContent);

                for (UnityAsset as : assets) {
                    String name = as.getTextName();
                    String fName;
                    if (uninstall) fName = "data/original/" + getDiaJsonName(name);
                    else fName = "data/" + getDiaJsonName(name);
                    String cacheFName = "data/cache/" + getDiaJsonName(name);

                    JsonElement diaElement = JsonParser.parseString(new String(as.asTextContent(), StandardCharsets.UTF_8));
                    JsonObject diaJson = diaElement.getAsJsonObject();

                    File cacheJsonFile = new File(cacheFName);
                    if (cacheJsonFile.exists() || cacheJsonFile.isFile()) {
                        FileCharDetector fd2 = new FileCharDetector(cacheFName);

                        JsonReader newDataReader = new JsonReader(new BufferedReader(new InputStreamReader(new FileInputStream(cacheFName) ,fd2.detector())));
                        JsonElement newDataElement = JsonParser.parseReader(newDataReader);
                        JsonObject root = newDataElement.getAsJsonObject();
                        JsonObject diaRoot = diaJson.get("root").getAsJsonArray().get(2).getAsJsonObject();

                        for (String key : root.keySet()) {
                            JsonObject values = root.get(key).getAsJsonObject();
                            for (String textKey : values.keySet()) {
                                String text = values.get(textKey).getAsString();
                                if (diaRoot.get(key).isJsonArray()) {
                                    JsonArray jArray = diaRoot.get(key).getAsJsonArray();

                                    int index = 0;
                                    boolean hasText = false;
                                    for (int i=0; i < jArray.size(); i++) {
                                        JsonElement element = jArray.get(i);
                                        if (element.isJsonPrimitive()) {
                                            if (element.getAsJsonPrimitive().isString()) {
                                                if (element.getAsJsonPrimitive().getAsString().equals("^" + text)) {
                                                    index = i;
                                                    hasText = true;
                                                }
                                            }
                                        }
                                    }
                                    if (hasText) {
                                        ArrayList<Object> array = new ArrayList<>();
                                        for (int i=0;i<jArray.size();i++){
                                            JsonElement element = jArray.get(i);
                                            if (element.isJsonPrimitive()) {
                                                if (element.getAsJsonPrimitive().isString()) {
                                                    array.add(element.getAsString());
                                                }else {
                                                    array.add(element);
                                                }
                                            }else {
                                                array.add(element);
                                            }
                                        }
                                        array.set(index, "^" + textKey);

                                        JsonArray newArray = new JsonArray();
                                        for (Object obj : array) {
                                            if (obj instanceof String) {
                                                newArray.add((String)obj);
                                            }else {
                                                newArray.add((JsonElement)obj);
                                            }
                                        }

                                        diaRoot.add(key, newArray);
                                    }
                                }
                            }
                        }
                        byte[] newContent2 = diaGson.toJson(diaJson).getBytes(StandardCharsets.UTF_8);
                        as.replaceTextContent(newContent2);
                    }

                    File jsonFile = new File(fName);
                    if (jsonFile.exists() || jsonFile.isFile()) {
                        FileCharDetector fd2 = new FileCharDetector(fName);

                        JsonReader newDataReader = new JsonReader(new BufferedReader(new InputStreamReader(new FileInputStream(fName) ,fd2.detector())));
                        JsonElement newDataElement = JsonParser.parseReader(newDataReader);
                        JsonObject root = newDataElement.getAsJsonObject();
                        JsonObject diaRoot = diaJson.get("root").getAsJsonArray().get(2).getAsJsonObject();
                        JsonObject cacheJson = new JsonObject();

                        for (String key : root.keySet()) {
                            JsonObject values = root.get(key).getAsJsonObject();
                            JsonObject cacheKeyJson = new JsonObject();
                            for (String textKey : values.keySet()) {
                                String text = values.get(textKey).getAsString();
                                if (diaRoot.get(key).isJsonArray()) {
                                    JsonArray jArray = diaRoot.get(key).getAsJsonArray();

                                    int index = 0;
                                    boolean hasText = false;
                                    for (int i=0; i < jArray.size(); i++) {
                                        JsonElement element = jArray.get(i);
                                        if (element.isJsonPrimitive()) {
                                            if (element.getAsJsonPrimitive().isString()) {
                                                if (element.getAsJsonPrimitive().getAsString().equals("^" + textKey)) {
                                                    index = i;
                                                    hasText = true;
                                                }
                                            }
                                        }
                                    }
                                    if (hasText) {
                                        ArrayList<Object> array = new ArrayList<>();
                                        for (int i=0;i<jArray.size();i++){
                                            JsonElement element = jArray.get(i);
                                            if (element.isJsonPrimitive()) {
                                                if (element.getAsJsonPrimitive().isString()) {
                                                    array.add(element.getAsString());
                                                }else {
                                                    array.add(element);
                                                }
                                            }else {
                                                array.add(element);
                                            }
                                        }
                                        array.set(index, "^" + text);
                                        cacheKeyJson.addProperty(textKey, text);

                                        JsonArray newArray = new JsonArray();
                                        for (Object obj : array) {
                                            if (obj instanceof String) {
                                                newArray.add((String)obj);
                                            }else {
                                                newArray.add((JsonElement)obj);
                                            }
                                        }

                                        diaRoot.add(key, newArray);
                                    }
                                }
                            }
                            if (cacheKeyJson.size() > 0) {
                                cacheJson.add(key, cacheKeyJson);
                            }
                        }
                        byte[] newContent2 = diaGson.toJson(diaJson).getBytes(StandardCharsets.UTF_8);
                        as.replaceTextContent(newContent2);

                        Writer writer = Files.newBufferedWriter(Paths.get(cacheFName));
                        gson.toJson(cacheJson, writer);
                        writer.close();
                    }
                }

                assetFile.updateOffsetsAndSize();
                assetFile.save(Paths.get("Rhythm Doctor_Data/resources.assets"));

                if (uninstall) resultField.setText("日本語化パッチを取り消しました。");
                else resultField.setText("パッチの適応が正常に完了しました。");
            }catch (Exception e) {
                resultField.setText("パッチの適応中にエラーが発生しました。");
                e.printStackTrace();
            }
            working = false;
        }).start();
    }

    private static UnityIndex indexFromName(String arg, HashMap<UnityIndex, UnityAsset> assets) {
        for (UnityIndex index : assets.keySet()) {
            UnityAsset asset = assets.get(index);
            if (asset.isTextContent() && arg.equalsIgnoreCase(asset.getTextName())) {
                return index;
            }
        }
        return null;
    }

    private static UnityIndex[] indexFromPartialName(String arg, HashMap<UnityIndex, UnityAsset> assets) {
        ArrayList<UnityIndex> list = new ArrayList<>();
        for (UnityIndex index : assets.keySet()) {
            UnityAsset asset = assets.get(index);
            if (asset.isTextContent() && asset.getTextName().contains(arg)) {
                list.add(index);
            }
        }
        UnityIndex[] array = list.toArray(new UnityIndex[list.size()]);
        return array;
    }

    private static String getDiaJsonName(String assetName) {
        String string = assetName.substring(0, assetName.indexOf("_"));
        string = string.substring(3);

        return string + ".json";
    }
}
