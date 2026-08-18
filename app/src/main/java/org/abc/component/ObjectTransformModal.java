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

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/abc/fxml/object-transform.fxml")
        );

        Parent root = loader.load();

        ObjectTransformController controller = loader.getController();

        if(!(renderer instanceof OpenGLRenderer))
            return;

        controller.setRenderer((OpenGLRenderer) renderer);

        Stage stage = new Stage();

        stage.initOwner(owner);
        stage.setTitle("Object Transform");
        stage.setScene(new Scene(root));

        stage.setResizable(false);

        stage.show();
    }
}