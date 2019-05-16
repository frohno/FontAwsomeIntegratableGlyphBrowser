/**
 * Copyright (c) 2016 Jens Deters http://www.jensd.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 *
 */
package de.jensd.fx.glyphs.browser;

import de.jensd.fx.glyphs.GlyphIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconNameComparator;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.GridView;

/**
 *
 * @author Jens Deters
 */
public class MinimalGlyphsBrowser extends VBox {

    @FXML
    private GridView<GlyphIcon> glyphsGridView;
    @FXML
    private TextField searchBar;

    private GlyphsPack glyphsPack;

    private final SimpleObjectProperty glyphSizeProperty = new SimpleObjectProperty<>(24);

    private String glyphName = "";
    Lock lock = new ReentrantLock();
    Condition condition = lock.newCondition();

    public static String getGlyph(Node node) {
        MinimalGlyphsBrowser iconsBrowser = new MinimalGlyphsBrowser();
        iconsBrowser.lock.lock();
        try {
            Platform.runLater(() -> {
                Stage stage = new Stage();
                Scene scene = new Scene(iconsBrowser, 220, 300);
                stage.setScene(scene);
                stage.initStyle(StageStyle.UNDECORATED);
                stage.initModality(Modality.APPLICATION_MODAL);
                Bounds bounds = node.localToScreen(node.getBoundsInLocal());
                stage.setX(bounds.getMinX());
                stage.setY(bounds.getMinY());
                stage.show();
            });
            iconsBrowser.condition.await();
            return iconsBrowser.glyphName;
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        } finally {
            iconsBrowser.lock.unlock();
        }
        return "";
    }

    private MinimalGlyphsBrowser() {
        glyphsPack = new GlyphsPack(FXCollections.observableArrayList((List<GlyphIcon>) Stream.of(FontAwesomeIcon.values())
                .sorted(new FontAwesomeIconNameComparator())
                .map(i -> createIconView(new FontAwesomeIconView(i)))
                .collect(Collectors.toList())));

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("glyphs_browser.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    void initialize() {
        glyphsGridView.setCellFactory((GridView<GlyphIcon> gridView) -> new GlyphsGridCell());
        glyphsGridView.cellHeightProperty().bind(glyphSizeProperty);
        glyphsGridView.cellWidthProperty().bind(glyphSizeProperty);
        glyphsPack.getGlyphNodes().forEach(glyph -> glyph.setVisible(true));
        updateBrowser(glyphsPack);
        glyphsGridView.getItems().forEach(glyph -> glyph.setFill(Color.web("#048BA8")));
        glyphsGridView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getTarget() instanceof GlyphIcon) {
                glyphName = ((GlyphIcon) event.getTarget()).getGlyphName();
                glyphsGridView.getItems().forEach(glyph -> glyph.setFill(Color.web("#048BA8")));
                ((GlyphIcon) event.getTarget()).setFill(Color.WHITE);
            }
        });
        searchBar.textProperty().addListener((observable, oldValue, newValue) -> {
            //In case search bar has no text inside
            if (searchBar.getText().isEmpty()) {
                //Reset all to visible
                glyphsPack.getGlyphNodes().forEach(glyph -> glyph.setVisible(true));
                updateBrowser(glyphsPack);
            } else { //Let's do some search magic
                glyphsPack.getGlyphNodes().forEach(glyph -> {

                    //Glyph name contains search bar text ? [ No case sensitive ]
                    String searchValue = newValue.toLowerCase(); //Speed improvements
                    glyph.setVisible(glyph.getGlyphName().toLowerCase().contains(searchValue)); //visible only if name matches searchValue
                });

                //Add the new items
                glyphsGridView.setItems(glyphsPack.getGlyphNodes().stream().filter(Node::isVisible)
                        .collect(Collectors.toCollection(FXCollections::observableArrayList)));
            }
        });

    }

    private void updateBrowser(GlyphsPack glyphPack) {
        glyphsGridView.setItems(glyphPack.getGlyphNodes());
    }

    private GlyphIcon createIconView(GlyphIcon icon) {
        icon.glyphSizeProperty().bind(glyphSizeProperty);
        return icon;
    }

    @FXML
    private void save() {
        lock.lock();
        try {
            condition.signal();
            ((Stage) searchBar.getScene().getWindow()).close();
        } finally {
            lock.unlock();
        }
    }

    @FXML
    private void cancel() {
        lock.lock();
        try {
            glyphName = null;
            condition.signal();
            ((Stage) searchBar.getScene().getWindow()).close();
        } finally {
            lock.unlock();
        }
    }
}
