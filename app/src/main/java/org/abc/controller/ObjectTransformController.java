package org.abc.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.abc.model.ObjectTransform;
import org.abc.service.OpenGLRenderer;

import java.util.function.Consumer;

public class ObjectTransformController {

    @FXML
    private Label selectedObjectLabel;

    @FXML
    private TextField positionX;

    @FXML
    private TextField positionY;

    @FXML
    private TextField positionZ;

    @FXML
    private TextField rotationX;

    @FXML
    private TextField rotationY;

    @FXML
    private TextField rotationZ;

    private OpenGLRenderer renderer;
    private final Consumer<Integer> selectionListener =
            index -> Platform.runLater(this::updateSelectedObject);

    public void setRenderer(OpenGLRenderer renderer) {

        this.renderer = renderer;

        renderer.addSelectionListener(selectionListener);

        updateSelectedObject();
    }

    @FXML
    public void handleClose() {

        if (renderer != null) {
            renderer.removeSelectionListener(selectionListener);
            renderer = null;
        }
    }

    @FXML
    private void handleApply() {

        if (renderer == null) {
            return;
        }

        int selected = renderer.getSelectedObject();

        if (selected < 0) {
            return;
        }

        try {
            float x = Float.parseFloat(positionX.getText());
            float y = Float.parseFloat(positionY.getText());
            float z = Float.parseFloat(positionZ.getText());

            float rx = Float.parseFloat(rotationX.getText());
            float ry = Float.parseFloat(rotationY.getText());
            float rz = Float.parseFloat(rotationZ.getText());

            renderer.setObjectTransform(
                    selected,
                    x, y, z,
                    rx, ry, rz
            );

            // Make sure the fields represent
            // the actual transform after applying.
            updateSelectedObject();

        } catch (NumberFormatException e) {
            System.out.println("Invalid transform values.");
        }
    }

    @FXML
    private void handleReset() {

        if (renderer == null) {
            return;
        }

        renderer.resetSelectedObject();

        updateSelectedObject();
    }

    private void updateSelectedObject() {

        if (renderer == null) {
            return;
        }

        int selected = renderer.getSelectedObject();

        if (selected < 0) {
            selectedObjectLabel.setText("No object selected");

            positionX.clear();
            positionY.clear();
            positionZ.clear();

            rotationX.clear();
            rotationY.clear();
            rotationZ.clear();

            return;
        }

        selectedObjectLabel.setText(
                "Selected object: " + selected
        );

        ObjectTransform transform =
                renderer.getObjectTransform(selected);

        if (transform == null) {
            return;
        }

        positionX.setText(
                Float.toString(transform.getPositionX())
        );

        positionY.setText(
                Float.toString(transform.getPositionY())
        );

        positionZ.setText(
                Float.toString(transform.getPositionZ())
        );

        rotationX.setText(
                Float.toString(transform.getRotationX())
        );

        rotationY.setText(
                Float.toString(transform.getRotationY())
        );

        rotationZ.setText(
                Float.toString(transform.getRotationZ())
        );
    }

    private void clearFields() {

        positionX.clear();
        positionY.clear();
        positionZ.clear();

        rotationX.clear();
        rotationY.clear();
        rotationZ.clear();
    }
}