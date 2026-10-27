package com.sakec.smartspace.ui;

import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Helpers for building consistent data tables without repeating cell-factory boilerplate. */
public final class Tables {
    private static final double ROW = 50;

    private Tables() { }

    public static <T> TableColumn<T, String> text(String title, Function<T, String> f, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(f.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    public static <T> TableColumn<T, T> node(String title, Function<T, Node> f, double width) {
        TableColumn<T, T> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : f.apply(item));
            }
        });
        c.setSortable(false);
        c.setPrefWidth(width);
        return c;
    }

    @SafeVarargs
    public static <T> TableView<T> table(List<T> rows, String emptyTitle, String emptyText, TableColumn<T, ?>... cols) {
        TableView<T> t = new TableView<>(FXCollections.observableArrayList(rows));
        t.getColumns().addAll(cols);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setFixedCellSize(ROW);
        t.setPlaceholder(new EmptyState("inbox", emptyTitle, emptyText));
        double h = rows.isEmpty() ? 240 : Math.min(560, rows.size() * ROW + 44);
        t.setPrefHeight(h);
        t.setMinHeight(h);
        t.getStyleClass().add("data-table");
        return t;
    }
}
