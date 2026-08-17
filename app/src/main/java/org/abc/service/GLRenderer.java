package org.abc.service;

import org.abc.model.ScanMesh;

public interface GLRenderer {

    void renderLoop();

    void initializeOpenGL();

    void cleanupOpenGL();

    void drawMesh(ScanMesh mesh);

    void updateProjection(int width, int height);
}