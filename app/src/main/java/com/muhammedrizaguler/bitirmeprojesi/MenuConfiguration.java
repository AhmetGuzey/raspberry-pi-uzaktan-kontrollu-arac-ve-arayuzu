package com.muhammedrizaguler.bitirmeprojesi;

import java.util.ArrayList;
import java.util.List;

public class MenuConfiguration {

    public enum MenuItemType {
        BUTTON,
        SLIDER,
        SWITCH,
        TEXT
    }

    public static class MenuItem {
        private MenuItemType type;
        private String text;
        private String id;
        private int minValue;
        private int maxValue;
        private int currentValue;
        private boolean isEnabled;
        private MenuItemClickListener clickListener;

        public MenuItem(MenuItemType type, String text, String id) {
            this.type = type;
            this.text = text;
            this.id = id;
            this.isEnabled = true;
        }

        // Button constructor
        public MenuItem(String text, String id, MenuItemClickListener listener) {
            this.type = MenuItemType.BUTTON;
            this.text = text;
            this.id = id;
            this.clickListener = listener;
            this.isEnabled = true;
        }

        // Slider constructor
        public MenuItem(String text, String id, int minValue, int maxValue, int currentValue) {
            this.type = MenuItemType.SLIDER;
            this.text = text;
            this.id = id;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.currentValue = currentValue;
            this.isEnabled = true;
        }

        // Switch Constructor
        public MenuItem(String text, String id, boolean isEnabled) {
            this.type = MenuItemType.SWITCH;
            this.text = text;
            this.id = id;
            this.isEnabled = isEnabled;
        }

        // Getters and Setters
        public MenuItemType getType() { return type; }
        public String getText() { return text; }
        public String getId() { return id; }
        public int getMinValue() { return minValue; }
        public int getMaxValue() { return maxValue; }
        public int getCurrentValue() { return currentValue; }
        public boolean isEnabled() { return isEnabled; }
        public MenuItemClickListener getClickListener() { return clickListener; }

        public void setCurrentValue(int currentValue) { this.currentValue = currentValue; }
        public void setEnabled(boolean enabled) { this.isEnabled = enabled; }
        public void setClickListener(MenuItemClickListener listener) { this.clickListener = listener; }
    }

    public interface MenuItemClickListener {
        void onItemClick(String itemId);
    }

    public interface SliderChangeListener {
        void onSliderChange(String itemId, int value);
    }

    public interface SwitchChangeListener {
        void onSwitchChange(String itemId, boolean isChecked);
    }

    public static class MenuConfig {
        private String title;
        private List<MenuItem> items;
        private int menuWidth;
        private int menuHeight;

        public MenuConfig(String title) {
            this.title = title;
            this.items = new ArrayList<>();
            this.menuWidth = 180;
            this.menuHeight = -1;
        }

        public MenuConfig addButton(String text, String id, MenuItemClickListener listener) {
            items.add(new MenuItem(text, id, listener));
            return this;
        }

        public MenuConfig addSlider(String text, String id, int min, int max, int current) {
            items.add(new MenuItem(text, id, min, max, current));
            return this;
        }

        public MenuConfig addSwitch(String text, String id, boolean isEnabled) {
            items.add(new MenuItem(text, id, isEnabled));
            return this;
        }

        public MenuConfig addText(String text) {
            items.add(new MenuItem(MenuItemType.TEXT, text, "text_" + items.size()));
            return this;
        }

        public MenuConfig setWidth(int width) {
            this.menuWidth = width;
            return this;
        }

        public MenuConfig setHeight(int height) {
            this.menuHeight = height;
            return this;
        }

        // Getters
        public String getTitle() { return title; }
        public List<MenuItem> getItems() { return items; }
        public int getMenuWidth() { return menuWidth; }
        public int getMenuHeight() { return menuHeight; }
    }

    // Toolbar butonlarına göre güncellenmiş menü konfigürasyonları

    // sound_button için menü
    public static MenuConfig getSoundMenu(MenuItemClickListener buttonListener,
                                          SliderChangeListener sliderListener,
                                          SwitchChangeListener switchListener) {
        return new MenuConfig("İletişim")
                .addButton("Ses Al", "sound_test1", buttonListener)
                .addButton("Ses Gönder", "sound_test2", buttonListener)
                .setWidth(210);
    }

    // lights_button için menü
    public static MenuConfig getLightsMenu(SliderChangeListener sliderListener) {
        return new MenuConfig("Lambalar")
                .addSwitch("Ön Lamba", "lights_front", false)
                .addSwitch("Selektör", "lights_back", false)
                .setWidth(220);
    }

    // record_button için menü
    public static MenuConfig getRecordMenu(MenuItemClickListener buttonListener) {
        return new MenuConfig("Kayıt")
                .addButton("Kaydı Başlat", "record_start", buttonListener)
                .addButton("Kaydı Durdur", "record_stop", buttonListener)
                .setWidth(210);
    }

    // screenshot_button için menü
    public static MenuConfig getScreenshotMenu(MenuItemClickListener buttonListener,
                                               SliderChangeListener sliderListener) {
        return new MenuConfig("Ekran Görüntüsü")
                .addButton("Fotoğraf Çek", "screenshot_take", buttonListener)
                .addSlider("Seri Çekim Aralığı", "screenshot_interval", 1, 10, 3)
                .addButton("Seri Çekim Başlat", "screenshot_burst_start", buttonListener)
                .setWidth(240);
    }

    // power_button için menü
    public static MenuConfig getPowerMenu(MenuItemClickListener buttonListener) {
        return new MenuConfig("Güç Yönetimi")
                .addButton("Sistem Kapat", "power_shutdown", buttonListener)
                .addButton("Yeniden Başlat", "power_restart", buttonListener)
                .setWidth(210);
    }
}