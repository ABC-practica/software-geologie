package org.abc.component;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.abc.controller.ObjectTransformController;
import org.abc.service.OpenGLRenderer;
import org.abc.service.RendererControl;

import java.io.IOException;

public class ObjectTransformModal {

    public void show(Window owner, RendererControl renderer) throws IOException {
        System.out.println("[INFO] Opening Object Transform panel");

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/abc/fxml/object-transform.fxml")
        );

        Parent root = loader.load();

        ObjectTransformController controller = loader.getController();

        if (!(renderer instanceof OpenGLRenderer)) {
            System.out.println(
                    "[ERROR] Object Transform requires OpenGLRenderer. Actual renderer: "
                            + (renderer == null ? "null" : renderer.getClass().getName())
            );
            return;
        }

        controller.setRenderer((OpenGLRenderer) renderer);

        Stage stage = new Stage();

        stage.initOwner(owner);
        stage.setTitle("Object Transform");
        stage.setScene(new Scene(root));

        stage.setResizable(false);

        //stage.setOnHidden(event -> controller.handleClose());

        stage.show();
    }
}