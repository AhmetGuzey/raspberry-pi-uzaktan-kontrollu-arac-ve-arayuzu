package com.muhammedrizaguler.bitirmeprojesi;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.widget.SwitchCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;

public class DynamicMenuManager implements
        MenuConfiguration.MenuItemClickListener,
        MenuConfiguration.SliderChangeListener,
        MenuConfiguration.SwitchChangeListener,
        MenuActionHandler.MenuActionCallback {

    private Context context;
    private FrameLayout menuContainer;
    private MenuBackgroundView backgroundView;
    private LinearLayout contentContainer;
    private boolean isMenuVisible = false;
    private View rootView;

    private Map<String, Button> buttonMap = new HashMap<>();
    private String selectedButtonId = null;
    private Map<String, String> menuSelectedButtons = new HashMap<>();
    private Map<String, androidx.appcompat.widget.SwitchCompat> switchViewMap = new HashMap<>();

    // ===== YENİ: Çoklu seçim için eklendi =====
    private Map<String, Set<String>> menuMultiSelectedButtons = new HashMap<>();
    // ===========================================

    private Map<String, Integer> sliderValues = new HashMap<>();
    private Map<String, Boolean> switchStates = new HashMap<>();
    private String currentMenuType = null;

    private static final String JSON_FILE_NAME = "menu_states.json";

    // MenuActionHandler - Tüm işlemler bu sınıfta yapılacak
    private MenuActionHandler actionHandler;

    // MediaProjection için (Activity'den çağrılacak)
    private MediaProjectionManager projectionManager;
    private ActivityCallback activityCallback;
    private static final int VIDEO_REQUEST_CODE = 1000;


    // Activity ile iletişim için callback interface
    public interface ActivityCallback {
        void startActivityForResult(Intent intent, int requestCode);
    }

    // Validation için geçerli ID'ler
    private static final Set<String> VALID_SOUND_BUTTONS = new HashSet<String>() {{
        add("sound_test1"); add("sound_test2"); add("sound_test3");
    }};

    private static final Set<String> VALID_RECORD_BUTTONS = new HashSet<String>() {{
        add("record_start"); add("record_stop");
    }};

    private static final Set<String> VALID_SCREENSHOT_BUTTONS = new HashSet<String>() {{
        add("screenshot_take"); add("screenshot_burst_start");
    }};

    private static final Set<String> VALID_EMERGENCY_BUTTONS = new HashSet<String>() {{
        add("emergency_stop");
    }};

    private static final Set<String> VALID_POWER_BUTTONS = new HashSet<String>() {{
        add("power_shutdown"); add("power_restart");
    }};

    private static final Set<String> VALID_SLIDER_IDS = new HashSet<String>() {{
        add("screenshot_interval");
    }};

    private static final Set<String> VALID_SWITCH_IDS = new HashSet<String>() {{
        add("lights_front");
        add("lights_back");
    }};

    public DynamicMenuManager(Context context, FrameLayout menuContainer, View rootView) {
        this.context = context;
        this.menuContainer = menuContainer;
        this.rootView = rootView;

        // MenuActionHandler'ı başlat
        this.actionHandler = new MenuActionHandler(context, rootView, menuContainer, this);

        // MediaProjectionManager'ı başlat
        this.projectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        initializeMenuComponents();
        initializeJsonFile();
    }

    /**
     * Activity callback'ini ayarla (video kayıt için gerekli)
     */
    public void setActivityCallback(ActivityCallback callback) {
        this.activityCallback = callback;
    }

    /**
     * RTSP Fragment referansını ayarla
     */
    public void setRTSPFragment(RTSPCameraActivity fragment) {
        if (actionHandler != null) {
            actionHandler.setRTSPFragment(fragment);
        }
    }

    /**
     * AudioManager referansını ayarla
     */
    public void setAudioManager(AudioManager audioManager) {
        if (actionHandler != null) {
            actionHandler.setAudioManager(audioManager);
        }
    }

    /**
     * Video kayıt sonucunu işle (Activity'den çağrılır)
     */
    public void handleMediaProjectionResult(int resultCode, Intent data) {
        if (actionHandler != null) {
            actionHandler.handleMediaProjectionResult(resultCode, data);
        }
    }

    // ==================== MenuActionCallback İMPLEMENTASYONU ====================

    @Override
    public void onMenuVisibilityChange(boolean isVisible) {
        this.isMenuVisible = isVisible;
    }

    @Override
    public boolean isMenuCurrentlyVisible() {
        return isMenuVisible;
    }

    // Public metod - MainActivity'den çağrılabilir
    public void hideMenuPublic() {
        hideMenu();
    }

    @Override
    public void requestMediaProjection() {
        // Video kayıt izni iste
        if (projectionManager != null && activityCallback != null) {
            Intent intent = projectionManager.createScreenCaptureIntent();
            activityCallback.startActivityForResult(intent, VIDEO_REQUEST_CODE);
        } else {
            Toast.makeText(context, "❌ Video kayıt için Activity callback ayarlanmamış", Toast.LENGTH_LONG).show();
        }
    }

    // ==================== JSON İŞLEMLERİ ====================

    public boolean writeJsonFile(String soundButton, String recordButton, String screenshotButton,
                                 String emergencyButton, String powerButton, Boolean soundEnable,
                                 Integer lightsFront , Integer screenshotInterval) {

        ValidationResult validation = validateParameters(soundButton, recordButton, screenshotButton,
                emergencyButton, powerButton, soundEnable, lightsFront, screenshotInterval);

        if (!validation.isValid()) {
            Toast.makeText(context, "Hata: " + validation.getErrorMessage(), Toast.LENGTH_LONG).show();
            return false;
        }

        try {
            JSONObject existingJson = loadMenuStatesFromInternalStorage();
            if (existingJson == null) existingJson = new JSONObject();

            updateJsonCategory(existingJson, "sound", soundButton, soundEnable, null);
            updateJsonCategory(existingJson, "record", recordButton, null, null);
            updateJsonCategory(existingJson, "lights", null, null,
                    new int[]{lightsFront != null ? lightsFront : -1});
            updateJsonCategory(existingJson, "screenshot", screenshotButton, null,
                    new int[]{screenshotInterval != null ? screenshotInterval : -1});
            updateJsonCategory(existingJson, "emergency", emergencyButton, null, null);
            updateJsonCategory(existingJson, "power", powerButton, null, null);

            writeJsonToFile(existingJson);
            reloadJsonData();
            updateMenu();

            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateJsonCategory(JSONObject json, String category, String button,
                                    Boolean switchState, int[] sliderValues) throws JSONException {
        JSONObject categoryObj = json.optJSONObject(category);
        if (categoryObj == null) categoryObj = new JSONObject();

        if (button != null) categoryObj.put("selectedButton", button);

        if (category.equals("sound") && switchState != null) {
            categoryObj.put("sound_enable", switchState);
        }

        if (category.equals("lights") && sliderValues != null) {
            if (sliderValues[0] >= 0) categoryObj.put("lights_front", sliderValues[0]);
            if (sliderValues[1] >= 0) categoryObj.put("lights_back", sliderValues[1]);
        }

        if (category.equals("screenshot") && sliderValues != null && sliderValues[0] >= 0) {
            categoryObj.put("screenshot_interval", sliderValues[0]);
        }

        json.put(category, categoryObj);
    }

    private ValidationResult validateParameters(String soundButton, String recordButton, String screenshotButton,
                                                String emergencyButton, String powerButton, Boolean soundEnable,
                                                Integer lightsFront, Integer screenshotInterval) {

        if (soundButton != null && !VALID_SOUND_BUTTONS.contains(soundButton)) {
            return new ValidationResult(false, "Geçersiz sound button: " + soundButton);
        }
        if (recordButton != null && !VALID_RECORD_BUTTONS.contains(recordButton)) {
            return new ValidationResult(false, "Geçersiz record button: " + recordButton);
        }
        if (screenshotButton != null && !VALID_SCREENSHOT_BUTTONS.contains(screenshotButton)) {
            return new ValidationResult(false, "Geçersiz screenshot button: " + screenshotButton);
        }
        if (emergencyButton != null && !VALID_EMERGENCY_BUTTONS.contains(emergencyButton)) {
            return new ValidationResult(false, "Geçersiz emergency button: " + emergencyButton);
        }
        if (powerButton != null && !VALID_POWER_BUTTONS.contains(powerButton)) {
            return new ValidationResult(false, "Geçersiz power button: " + powerButton);
        }
        if (lightsFront != null && (lightsFront < 0 || lightsFront > 10)) {
            return new ValidationResult(false, "lights_front değeri 0-10 aralığında olmalı");
        }
        if (screenshotInterval != null && (screenshotInterval < 1 || screenshotInterval > 10)) {
            return new ValidationResult(false, "screenshot_interval değeri 1-10 aralığında olmalı");
        }

        return new ValidationResult(true, "Validation başarılı");
    }

    private static class ValidationResult {
        private boolean valid;
        private String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }

    private void updateJsonRealTime(String category, String key, Object value) {
        try {
            JSONObject existingJson = loadMenuStatesFromInternalStorage();
            if (existingJson == null) existingJson = new JSONObject();

            JSONObject categoryObject = existingJson.optJSONObject(category);
            if (categoryObject == null) categoryObject = new JSONObject();

            categoryObject.put(key, value);
            existingJson.put(category, categoryObject);

            writeJsonToFile(existingJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ==================== MENÜ OLAY YÖNETİCİLERİ ====================

    @Override
    public void onItemClick(String itemId) {

        if (currentMenuType != null) {
            boolean isValid = false;
            switch (currentMenuType) {
                case "sound":
                    isValid = VALID_SOUND_BUTTONS.contains(itemId);
                    break;
                case "record":
                    isValid = VALID_RECORD_BUTTONS.contains(itemId);
                    break;
                case "screenshot":
                    isValid = VALID_SCREENSHOT_BUTTONS.contains(itemId);
                    break;
                case "emergency":
                    isValid = VALID_EMERGENCY_BUTTONS.contains(itemId);
                    break;
                case "power":
                    isValid = VALID_POWER_BUTTONS.contains(itemId);
                    break;
            }

            if (isValid) {
                updateJsonRealTime(currentMenuType, "selectedButton", itemId);

                // İşlemleri MenuActionHandler'a delege et
                Integer intervalValue = sliderValues.get("screenshot_interval");
                int interval = (intervalValue != null) ? intervalValue : 3;
                actionHandler.handleButtonClick(itemId, currentMenuType, interval);
            }
        }
    }

    @Override
    public void onSliderChange(String itemId, int value) {
        if (VALID_SLIDER_IDS.contains(itemId)) {
            String category = itemId.startsWith("lights") ? "lights" : "screenshot";
            updateJsonRealTime(category, itemId, value);

            // İşlemi MenuActionHandler'a delege et
            actionHandler.handleSliderChange(itemId, value);
        }
    }

    @Override
    public void onSwitchChange(String itemId, boolean isChecked) {
        if (!VALID_SWITCH_IDS.contains(itemId)) return;

        // ── MUTEX MANTIĞI ──────────────────────────────────────────────────────
        // "lights_front" ve "lights_back" aynı anda açık olamaz.
        if (isChecked) {
            String otherSwitchId = itemId.equals("lights_front") ? "lights_back" : "lights_front";

            // Eğer diğer switch açıksa, onu programatik olarak kapat
            if (Boolean.TRUE.equals(switchStates.get(otherSwitchId))) {
                switchStates.put(otherSwitchId, false);
                updateJsonRealTime("lights", otherSwitchId, false);

                // UI'ı güncelle (listener tetiklenmeden)
                androidx.appcompat.widget.SwitchCompat otherView = switchViewMap.get(otherSwitchId);
                if (otherView != null) {
                    // Listener'ı geçici olarak kaldır, durumu ayarla, geri tak
                    otherView.setOnCheckedChangeListener(null);
                    otherView.setChecked(false);
                    // Listener'ı geri bağla (aynı id ile yeniden bind)
                    otherView.setOnCheckedChangeListener((buttonView, checked) -> {
                        switchStates.put(otherSwitchId, checked);
                        onSwitchChange(otherSwitchId, checked);
                    });
                }

                // Diğer switch için "off" komutunu Raspberry'e gönder
                actionHandler.handleSwitchChange(otherSwitchId, false);
            }
        }
        // ──────────────────────────────────────────────────────────────────────

        // Durumu kaydet
        switchStates.put(itemId, isChecked);
        updateJsonRealTime("lights", itemId, isChecked);

        // Raspberry'e komutu gönder
        actionHandler.handleSwitchChange(itemId, isChecked);
    }

    // ==================== JSON YÜKLEME VE KAYDETME ====================

    private void initializeJsonFile() {
        try {
            JSONObject existingJson = loadMenuStatesFromInternalStorage();
            if (existingJson == null) {
                copyJsonFromAssetsToInternalStorage();
            }
            updateMenu();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyJsonFromAssetsToInternalStorage() {
        try {
            InputStream inputStream = context.getAssets().open(JSON_FILE_NAME);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            String jsonString = new String(buffer, StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(jsonString);
            writeJsonToFile(jsonObject);
        } catch (IOException | JSONException e) {
            createDefaultJsonFile();
        }
    }

    private void createDefaultJsonFile() {
        try {
            JSONObject defaultJson = new JSONObject();
            defaultJson.put("sound", new JSONObject().put("selectedButton", "sound_test1").put("sound_enable", true));
            defaultJson.put("record", new JSONObject().put("selectedButton", "record_start"));
            defaultJson.put("lights", new JSONObject().put("lights_front", 0).put("lights_back", 0));
            defaultJson.put("screenshot", new JSONObject().put("selectedButton", "screenshot_take").put("screenshot_interval", 3));
            defaultJson.put("emergency", new JSONObject().put("selectedButton", "emergency_stop"));
            defaultJson.put("power", new JSONObject().put("selectedButton", "power_shutdown"));
            writeJsonToFile(defaultJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void reloadJsonData() {
        try {
            JSONObject freshJson = loadMenuStatesFromInternalStorage();
            if (freshJson != null) {
                menuSelectedButtons.clear();
                sliderValues.clear();
                switchStates.clear();
                loadDataFromJson(freshJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadDataFromJson(JSONObject jsonObject) {
        try {
            loadCategoryData(jsonObject, "sound", true, true);
            loadCategoryData(jsonObject, "record", true, false);
            loadCategoryData(jsonObject, "screenshot", true, false);
            loadCategoryData(jsonObject, "emergency", true, false);
            loadCategoryData(jsonObject, "power", true, false);

            JSONObject lightsData = jsonObject.optJSONObject("lights");
            if (lightsData != null) {
                if (lightsData.has("lights_front")) sliderValues.put("lights_front", lightsData.getInt("lights_front"));
                if (lightsData.has("lights_back")) sliderValues.put("lights_back", lightsData.getInt("lights_back"));
            }

            JSONObject screenshotData = jsonObject.optJSONObject("screenshot");
            if (screenshotData != null && screenshotData.has("screenshot_interval")) {
                sliderValues.put("screenshot_interval", screenshotData.getInt("screenshot_interval"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadCategoryData(JSONObject json, String category, boolean hasButton, boolean hasSwitch) throws JSONException {
        JSONObject data = json.optJSONObject(category);
        if (data == null) return;

        if (hasButton) {
            String selectedButton = data.optString("selectedButton", null);
            if (selectedButton != null && !selectedButton.isEmpty()) {
                menuSelectedButtons.put(category, selectedButton);
            }
        }

        if (hasSwitch && category.equals("sound") && data.has("sound_enable")) {
            switchStates.put("sound_enable", data.getBoolean("sound_enable"));
        }
    }

    private void writeJsonToFile(JSONObject jsonObject) {
        try {
            FileOutputStream fos = context.openFileOutput(JSON_FILE_NAME, Context.MODE_PRIVATE);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(jsonObject.toString());
            writer.flush();
            writer.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JSONObject loadMenuStatesFromInternalStorage() {
        try {
            FileInputStream fis = context.openFileInput(JSON_FILE_NAME);
            int size = fis.available();
            byte[] buffer = new byte[size];
            fis.read(buffer);
            fis.close();

            String jsonString = new String(buffer, StandardCharsets.UTF_8);
            return new JSONObject(jsonString);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    public void updateMenu() {
        try {
            JSONObject menuData = loadMenuStatesFromInternalStorage();
            if (menuData != null) {
                loadDataFromJson(menuData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== MENÜ OLUŞTURMA FONKSİYONLARI ====================

    private void initializeMenuComponents() {
        if (menuContainer == null) return;

        backgroundView = new MenuBackgroundView(context);
        contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);

        float density = context.getResources().getDisplayMetrics().density;
        contentContainer.setPadding(
                (int) (12 * density),
                (int) (60 * density),
                (int) (12 * density),
                (int) (1 * density)
        );

        menuContainer.removeAllViews();
        menuContainer.addView(backgroundView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        menuContainer.addView(contentContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    public void showMenu(String buttonType, View anchorView) {
        if (menuContainer == null) return;

        currentMenuType = buttonType;
        clearMenuContent();

        MenuConfiguration.MenuConfig menuConfig = getMenuConfigForButton(buttonType);

        if (menuConfig != null) {
            if (backgroundView != null) {
                backgroundView.setMenuTitle(menuConfig.getTitle());
            }

            createMenuItems(menuConfig);
            loadSavedStates(buttonType);
            adjustMenuSizeDynamically(menuConfig);
            positionMenu(anchorView, menuConfig);

            menuContainer.setVisibility(View.VISIBLE);
            isMenuVisible = true;
        }
    }

    // ==================== ÇEKİM SEÇİM MANTIĞI ====================

    /**
     * Belirtilen menü tipinin çoklu seçimi destekleyip desteklemediğini döndürür.
     * Şu an sadece "sound" menüsü çoklu seçimi destekler.
     */
    private boolean isMultiSelectMenu(String menuType) {
        return "sound".equals(menuType);
    }

    private void loadSavedStates(String menuType) {
        if (isMultiSelectMenu(menuType)) {
            // Çoklu seçim: tüm seçili butonları geri yükle
            Set<String> selectedSet = menuMultiSelectedButtons.get(menuType);
            if (selectedSet != null) {
                for (String btnId : selectedSet) {
                    Button btn = buttonMap.get(btnId);
                    if (btn != null) {
                        setButtonSelected(btn);
                    }
                }
            }
        } else {
            // Tekli seçim: mevcut davranış
            String savedSelection = menuSelectedButtons.get(menuType);
            if (savedSelection != null) {
                Button savedButton = buttonMap.get(savedSelection);
                if (savedButton != null) {
                    setButtonSelected(savedButton);
                    selectedButtonId = savedSelection;
                }
            }
        }

        // Slider ve switch durumları her iki mod için de aynı
        for (Map.Entry<String, Integer> entry : sliderValues.entrySet()) {
            updateSliderValue(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Boolean> entry : switchStates.entrySet()) {
            updateSwitchState(entry.getKey(), entry.getValue());
        }
    }

    private void toggleButtonSelection(String buttonId) {
        Button clickedButton = buttonMap.get(buttonId);
        if (clickedButton == null) return;

        if (isMultiSelectMenu(currentMenuType)) {
            // ===== ÇOK SEÇİM MODU =====
            // Her butonu bağımsız olarak toggle et; diğer butonlara dokunma
            Set<String> selectedSet = menuMultiSelectedButtons.get(currentMenuType);
            if (selectedSet == null) {
                selectedSet = new HashSet<>();
                menuMultiSelectedButtons.put(currentMenuType, selectedSet);
            }

            if (selectedSet.contains(buttonId)) {
                // Seçili → seçimi kaldır
                setButtonUnselected(clickedButton);
                selectedSet.remove(buttonId);
            } else {
                // Seçili değil → seç
                setButtonSelected(clickedButton);
                selectedSet.add(buttonId);
            }
        } else {
            // ===== TEKLİ SEÇİM MODU (mevcut davranış) =====
            if (buttonId.equals(selectedButtonId)) {
                setButtonUnselected(clickedButton);
                selectedButtonId = null;
                menuSelectedButtons.remove(currentMenuType);
            } else {
                if (selectedButtonId != null) {
                    Button previousButton = buttonMap.get(selectedButtonId);
                    if (previousButton != null) {
                        setButtonUnselected(previousButton);
                    }
                }
                setButtonSelected(clickedButton);
                selectedButtonId = buttonId;
                menuSelectedButtons.put(currentMenuType, buttonId);
            }
        }
    }

    // ==================== YARDIMCI GÜNCELLEME METODlARI ====================

    private void updateSliderValue(String itemId, int value) {
        for (int i = 0; i < contentContainer.getChildCount(); i++) {
            View child = contentContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout container = (LinearLayout) child;
                for (int j = 0; j < container.getChildCount(); j++) {
                    View subChild = container.getChildAt(j);
                    if (subChild instanceof SeekBar && itemId.equals(subChild.getTag())) {
                        SeekBar seekBar = (SeekBar) subChild;
                        seekBar.setProgress(value);
                        if (j > 0 && container.getChildAt(0) instanceof TextView) {
                            TextView label = (TextView) container.getChildAt(0);
                            String originalText = label.getText().toString().split(":")[0];
                            label.setText(originalText + ": " + value);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void updateSwitchState(String itemId, boolean isChecked) {
        for (int i = 0; i < contentContainer.getChildCount(); i++) {
            View child = contentContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout container = (LinearLayout) child;
                for (int j = 0; j < container.getChildCount(); j++) {
                    View subChild = container.getChildAt(j);
                    if (subChild instanceof SwitchCompat && itemId.equals(subChild.getTag())) {
                        ((SwitchCompat) subChild).setChecked(isChecked);
                        break;
                    }
                }
            }
        }
    }

    private void clearMenuContent() {
        if (contentContainer != null) {
            contentContainer.removeAllViews();
        }
        buttonMap.clear();
        switchViewMap.clear();
        selectedButtonId = null;
    }

    private MenuConfiguration.MenuConfig getMenuConfigForButton(String buttonType) {
        switch (buttonType) {
            case "sound": return MenuConfiguration.getSoundMenu(this, this, this);
            case "lights": return MenuConfiguration.getLightsMenu(this);
            case "record": return MenuConfiguration.getRecordMenu(this);
            case "screenshot": return MenuConfiguration.getScreenshotMenu(this, this);
            case "power": return MenuConfiguration.getPowerMenu(this);
            default: return null;
        }
    }

    private void createMenuItems(MenuConfiguration.MenuConfig menuConfig) {
        if (contentContainer == null || menuConfig == null) return;

        for (MenuConfiguration.MenuItem item : menuConfig.getItems()) {
            View itemView = createItemView(item);
            if (itemView != null) {
                contentContainer.addView(itemView);
            }
        }
    }

    private View createItemView(MenuConfiguration.MenuItem item) {
        switch (item.getType()) {
            case BUTTON: return createEnhancedButtonView(item);
            case SLIDER: return createEnhancedSliderView(item);
            case SWITCH: return createEnhancedSwitchView(item);
            case TEXT: return createEnhancedTextView(item);
            default: return null;
        }
    }

    private View createEnhancedButtonView(MenuConfiguration.MenuItem item) {
        Button button = new Button(context);
        button.setText(item.getText());
        button.setId(View.generateViewId());
        button.setTag(item.getId());

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (42 * density)
        );
        params.setMargins((int) density, (int) (3 * density), (int) density, (int) (3 * density));
        button.setLayoutParams(params);

        setButtonUnselected(button);
        button.setTextSize(12);
        button.setAllCaps(false);

        buttonMap.put(item.getId(), button);

        button.setOnClickListener(v -> {
            toggleButtonSelection(item.getId());
            if (item.getClickListener() != null) {
                item.getClickListener().onItemClick(item.getId());
            }
        });

        return button;
    }

    private void setButtonSelected(Button button) {
        button.setBackgroundColor(Color.WHITE);
        button.setTextColor(Color.parseColor("#506B70"));
    }

    private void setButtonUnselected(Button button) {
        button.setBackground(new android.graphics.drawable.GradientDrawable());
        button.setTextColor(Color.WHITE);
    }

    private View createEnhancedSliderView(MenuConfiguration.MenuItem item) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins((int) (17 * density), (int) (5 * density),
                (int) (17 * density), (int) (5 * density));
        container.setLayoutParams(containerParams);
        container.setPadding((int) (7 * density), (int) (5 * density),
                (int) (7 * density), (int) (5 * density));

        Integer savedValue = sliderValues.get(item.getId());
        int currentValue = (savedValue != null) ? savedValue : item.getCurrentValue();
        item.setCurrentValue(currentValue);

        TextView label = new TextView(context);
        label.setText(item.getText() + ": " + currentValue);
        label.setTextColor(Color.parseColor("#E0E0E0"));
        label.setTextSize(12);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        container.addView(label);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setTag(item.getId());
        seekBar.setMax(item.getMaxValue() - item.getMinValue());
        seekBar.setProgress(currentValue - item.getMinValue());

        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (20 * density));
        seekBarParams.setMargins(0, (int) (8 * density), 0, (int) (8 * density));
        seekBar.setLayoutParams(seekBarParams);

        seekBar.setProgressDrawable(createThickProgressDrawable(density));
        seekBar.setThumb(createThickThumbDrawable(density));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int value = progress + item.getMinValue();
                    label.setText(item.getText() + ": " + value);
                    item.setCurrentValue(value);
                    sliderValues.put(item.getId(), value);
                    onSliderChange(item.getId(), value);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        container.addView(seekBar);
        return container;
    }

    private android.graphics.drawable.LayerDrawable createThickProgressDrawable(float density) {
        android.graphics.drawable.GradientDrawable backgroundDrawable =
                new android.graphics.drawable.GradientDrawable();
        backgroundDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        backgroundDrawable.setColor(Color.parseColor("#000000"));
        backgroundDrawable.setCornerRadius(10 * density);
        backgroundDrawable.setSize(-1, (int) (12 * density));

        android.graphics.drawable.GradientDrawable progressDrawable =
                new android.graphics.drawable.GradientDrawable();
        progressDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        progressDrawable.setColor(Color.parseColor("#506B70"));
        progressDrawable.setCornerRadius(10 * density);
        progressDrawable.setSize(-1, (int) (12 * density));

        android.graphics.drawable.Drawable[] layers = {backgroundDrawable, null, progressDrawable};
        android.graphics.drawable.LayerDrawable layerDrawable =
                new android.graphics.drawable.LayerDrawable(layers);
        layerDrawable.setId(0, android.R.id.background);
        layerDrawable.setId(2, android.R.id.progress);

        return layerDrawable;
    }

    private android.graphics.drawable.GradientDrawable createThickThumbDrawable(float density) {
        android.graphics.drawable.GradientDrawable thumbDrawable =
                new android.graphics.drawable.GradientDrawable();
        thumbDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        thumbDrawable.setColor(Color.parseColor("#FFFFFF"));
        int thumbSize = (int) (24 * density);
        thumbDrawable.setSize(thumbSize, thumbSize);
        return thumbDrawable;
    }

    private View createEnhancedSwitchView(MenuConfiguration.MenuItem item) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (40 * density));
        containerParams.setMargins((int) (18 * density), (int) (3 * density),
                (int) (18 * density), (int) (3 * density));
        container.setLayoutParams(containerParams);
        container.setPadding((int) (7 * density), (int) (5 * density),
                (int) (7 * density), (int) (5 * density));

        android.widget.TextView label = new android.widget.TextView(context);
        label.setText(item.getText());
        label.setTextColor(android.graphics.Color.parseColor("#E0E0E0"));
        label.setTextSize(12);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        androidx.appcompat.widget.SwitchCompat switchView =
                new androidx.appcompat.widget.SwitchCompat(context);
        switchView.setTag(item.getId());

        Boolean savedState = switchStates.get(item.getId());
        boolean currentState = (savedState != null) ? savedState : item.isEnabled();
        switchView.setChecked(currentState);
        item.setEnabled(currentState);

        switchView.setThumbTintList(
                android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#FFFFFF")));
        switchView.setTrackTintList(
                android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#506B70")));

        // Switch referansını kaydet — mutex için gerekli
        switchViewMap.put(item.getId(), switchView);

        final String switchId = item.getId();
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setEnabled(isChecked);
            switchStates.put(switchId, isChecked);
            onSwitchChange(switchId, isChecked);
        });

        container.addView(label);
        container.addView(switchView);
        return container;
    }

    private View createEnhancedTextView(MenuConfiguration.MenuItem item) {
        TextView textView = new TextView(context);
        textView.setText(item.getText());
        textView.setTextColor(Color.parseColor("#B0B0B0"));
        textView.setTextSize(11);
        textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins((int) (17 * density), (int) (3 * density),
                (int) (17 * density), (int) (3 * density));
        textView.setLayoutParams(params);
        textView.setPadding((int) (7 * density), (int) (5 * density),
                (int) (7 * density), (int) (5 * density));

        return textView;
    }

    private void adjustMenuSizeDynamically(MenuConfiguration.MenuConfig menuConfig) {
        if (menuContainer == null) return;

        contentContainer.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        float density = context.getResources().getDisplayMetrics().density;

        ViewGroup.LayoutParams params = menuContainer.getLayoutParams();
        if (params == null) {
            params = new ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
            );
        }

        int minWidth = (int) (170 * density);
        int maxWidth = (int) (310 * density);
        params.width = Math.max(minWidth, Math.min(maxWidth, (int) (menuConfig.getMenuWidth() * density)));

        int headerHeight = (int) (40 * density);
        int paddingHeight = (int) (26 * density);

        int totalItemsHeight = 0;
        for (MenuConfiguration.MenuItem item : menuConfig.getItems()) {
            switch (item.getType()) {
                case SLIDER:
                    totalItemsHeight += (int) (70 * density);
                    break;
                case SWITCH:
                    totalItemsHeight += (int) (46 * density);
                    break;
                case BUTTON:
                    totalItemsHeight += (int) (48 * density);
                    break;
                case TEXT:
                    totalItemsHeight += (int) (30 * density);
                    break;
            }
        }

        int calculatedHeight = 50 + headerHeight + paddingHeight + totalItemsHeight;
        int maxHeight = (int) (500 * density);

        params.height = Math.min(calculatedHeight, maxHeight);

        if (backgroundView != null) {
            backgroundView.setMenuHeight(params.height);
        }

        menuContainer.setLayoutParams(params);
    }

    private void positionMenu(View anchorView, MenuConfiguration.MenuConfig menuConfig) {
        if (menuContainer == null || anchorView == null) return;

        try {
            int[] buttonLocation = new int[2];
            anchorView.getLocationOnScreen(buttonLocation);

            ConstraintLayout.LayoutParams layoutParams =
                    (ConstraintLayout.LayoutParams) menuContainer.getLayoutParams();

            if (layoutParams == null) {
                layoutParams = new ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.WRAP_CONTENT,
                        ConstraintLayout.LayoutParams.WRAP_CONTENT);
            }

            float density = context.getResources().getDisplayMetrics().density;
            int menuWidth = layoutParams.width;
            int buttonWidth = anchorView.getWidth();
            int buttonCenterX = buttonLocation[0] + (buttonWidth / 2);
            int menuStartX = buttonCenterX - (menuWidth / 2);

            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            if (menuStartX < 0) {
                menuStartX = (int) (density - 2);
            } else if (menuStartX + menuWidth > screenWidth) {
                menuStartX = screenWidth - menuWidth - (int) (16 * density);
            }

            layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            layoutParams.leftMargin = menuStartX - 15;
            layoutParams.topMargin = (int) (42 * density);

            menuContainer.setLayoutParams(layoutParams);
            menuContainer.bringToFront();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isMenuVisible() {
        return isMenuVisible;
    }

    @Override
    public void hideMenu() {
        // Seri çekimi durdur
        if (actionHandler != null) {
            actionHandler.stopBurstMode();
        }

        if (menuContainer != null) {
            clearMenuContent();
            menuContainer.setVisibility(View.GONE);
            isMenuVisible = false;
        }
    }

    // Activity yaşam döngüsü için temizleme metodu
    public void onDestroy() {
        if (actionHandler != null) {
            actionHandler.onDestroy();
        }
    }
}