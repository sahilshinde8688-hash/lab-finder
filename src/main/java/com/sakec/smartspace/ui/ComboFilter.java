package com.sakec.smartspace.ui;

import java.util.List;
import java.util.function.Function;
import javafx.scene.control.ComboBox;

/** A drop-down whose first entry means "all". selected() returns null for that entry. */
public final class ComboFilter<T> {
    public final ComboBox<String> box = new ComboBox<>();
    private final List<T> items;

    public ComboFilter(String allLabel, List<T> items, Function<T, String> label) {
        this.items = items;
        box.getItems().add(allLabel);
        for (T t : items) box.getItems().add(label.apply(t));
        box.getSelectionModel().select(0);
        box.setMaxWidth(Double.MAX_VALUE);
    }

    public T selected() {
        int i = box.getSelectionModel().getSelectedIndex();
        return i <= 0 ? null : items.get(i - 1);
    }

    public void onChange(Runnable r) { box.valueProperty().addListener((o, a, b) -> r.run()); }
}
