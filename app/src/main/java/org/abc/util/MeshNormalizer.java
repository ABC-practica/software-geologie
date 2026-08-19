package org.abc.util;

import org.abc.model.ScanMesh;

public class MeshNormalizer {

    // How large the largest dimension of the model
    // should be after normalization.
    private static final float TARGET_SIZE = 3.0f;

    public static ScanMesh normalize(ScanMesh mesh) {

        float[] vertices = mesh.getVertices();

        if (vertices == null || vertices.length == 0) {
            return mesh;
        }

        float minX = vertices[0];
        float minY = vertices[1];
        float minZ = vertices[2];

        float maxX = vertices[0];
        float maxY = vertices[1];
        float maxZ = vertices[2];

        // Find bounding box
        for (int i = 0; i < vertices.length; i += 3) {

            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);

            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        // Center of the model
        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;

        // Original dimensions
        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;

        // Largest dimension
        float size = Math.max(sizeX, Math.max(sizeY, sizeZ));

        if (size == 0.0f) {
            return mesh;
        }

        /*
         * Scale the largest dimension to TARGET_SIZE.
         *
         * IMPORTANT:
         * The exact same scale is used for X, Y and Z.
         */
        float scale = TARGET_SIZE / size;

        float[] normalizedVertices = new float[vertices.length];

        for (int i = 0; i < vertices.length; i += 3) {

            normalizedVertices[i] = (vertices[i] - centerX) * scale;
            normalizedVertices[i + 1] = (vertices[i + 1] - centerY) * scale;
            normalizedVertices[i + 2] = (vertices[i + 2] - centerZ) * scale;
        }

        return new ScanMesh(normalizedVertices, mesh.getIndices(), mesh.getTextureCoordinates(), mesh.getVertexMaterials());
    }
}