package org.abc.service;

import org.abc.model.ScanMesh;

public class HeatmapRenderer implements GLRenderer, Renderer, RendererControl {

    private final ScanMesh object;

    public HeatmapRenderer(ScanMesh object) {
        this.object = object;
    }

    @Override
    public void renderLoop() {

    }

    @Override
    public void initializeOpenGL() {

    }

    @Override
    public void cleanupOpenGL() {

    }

    @Override
    public void drawMesh(ScanMesh mesh) {
        // calculate/use curvature or roughness
        // convert value to RGB
        // draw vertex
    }

    @Override
    public void updateProjection(int width, int height) {

    }

    @Override
    public void render() {

    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }

    @Override
    public void reload() {

    }

    @Override
    public void resetCamera() {

    }

    @Override
    public void refresh() {

    }
}
