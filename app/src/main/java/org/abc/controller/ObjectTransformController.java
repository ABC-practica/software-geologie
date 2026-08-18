package org.abc.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.abc.service.OpenGLRenderer;
import org.abc.service.RendererControl;

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

    public void setRenderer(OpenGLRenderer renderer) {
        this.renderer = renderer;
        updateSelectedObject();
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

    @FXML
    private void handleRefreshSelection() {
        updateSelectedObject();
    }

    private void updateSelectedObject() {

        if (renderer == null) {
            return;
        }

        int selected = renderer.getSelectedObject();

        if (selected < 0) {
            selectedObjectLabel.setText("No object selected");
            return;
        }

        selectedObjectLabel.setText(
                "Selected object: " + selected
        );

        // We will add getters for these next.
    }
}