package org.abc.service;

import org.abc.model.Material;
import org.abc.model.ScanMesh;
import org.abc.model.Texture;
import org.abc.util.LightNormalizer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.abc.model.ObjectTransform;

import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class OpenGLRenderer implements RenderStrategy, Runnable {

    private volatile long window;
    private volatile int windowWidth = 800;
    private volatile int windowHeight = 600;

    private int lastViewportWidth = -1;
    private int lastViewportHeight = -1;

    private volatile float cameraDistance = 2.5f;

    private volatile float cameraPositionX = 0.0f;
    private volatile float cameraPositionY = 0.0f;
    private volatile float cameraPositionZ = 0.0f;

    private volatile float cameraRotationX = 0.0f;
    private volatile float cameraRotationY = 0.0f;
    private volatile float cameraRotationZ = 0.0f;

    private final List<ObjectTransform> transforms = new ArrayList<>();
    private final List<Consumer<Integer>> selectionListeners =
            new ArrayList<>();

    private int selectedObject;

    private double lastMouseX;
    private double lastMouseY;

    private volatile double pendingClickX = -1;
    private volatile double pendingClickY = -1;

    private volatile boolean rotating;
    private volatile boolean moving;
    private volatile boolean cameraRotating;
    private volatile boolean cameraMoving;

    private volatile boolean running;
    private volatile boolean renderFinished;

    private final List<ScanMesh> objects;

    private final Map<Texture, Integer> textureIds = new HashMap<>();
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();

    private Thread renderThread;

    public OpenGLRenderer(List<ScanMesh> objects) {
        this.objects = objects;

        for (int i = 0; i < objects.size(); i++) {
            ObjectTransform transform = new ObjectTransform();

            // Preserve your original initial camera/object rotation
            transform.rotate(25.0f, 35.0f, 0.0f);

            transforms.add(transform);
        }
        setSelectedObject(-1);
    }

    public void addSelectionListener(Consumer<Integer> listener) {
        selectionListeners.add(listener);
    }

    public void removeSelectionListener(Consumer<Integer> listener) {
        selectionListeners.remove(listener);
    }

    private void setSelectedObject(int index) {

        if (index < -1 || index >= objects.size()) {
            return;
        }

        selectedObject = index;

        for (Consumer<Integer> listener : selectionListeners) {
            listener.accept(index);
        }

        System.out.println(
                "[INFO] Selected object: " + selectedObject
        );
    }

    @Override
    public void run() {
        open();
    }

    @Override
    public void open() {
        GLFWManager.initialize();
        GLFWManager.execute(this::createWindowOnMainThread);
    }

    private void createWindowOnMainThread() {
        if (!GLFWManager.isOwnerThread()) {
            throw new IllegalStateException(
                    "GLFW window creation must happen on the JVM main thread"
            );
        }

        if (window != 0) {
            return;
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow(
                windowWidth,
                windowHeight,
                "3D Renderer",
                0,
                0
        );

        if (window == 0) {
            throw new IllegalStateException("Unable to create GLFW window");
        }

        setupCallbacks();

        GLFWManager.register(this);

        running = true;
        renderFinished = false;

        GLFW.glfwShowWindow(window);

        renderThread = new Thread(this::renderLoop, "OpenGL-Renderer");
        renderThread.start();
    }

    private void setupCallbacks() {
        GLFW.glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            windowWidth = Math.max(1, width);
            windowHeight = Math.max(1, height);
        });

        GLFW.glfwSetScrollCallback(
                window,
                (w, xOffset, yOffset) -> zoom((float) -yOffset * 0.2f)
        );

        GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                running = false;
            }
        });

        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {

            double[] x = new double[1];
            double[] y = new double[1];

            GLFW.glfwGetCursorPos(window, x, y);

            if (action == GLFW.GLFW_PRESS) {

                lastMouseX = x[0];
                lastMouseY = y[0];

                // LEFT MOUSE
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {

                    pendingClickX = x[0];
                    pendingClickY = y[0];

                    // We don't yet know whether this is
                    // an object or the camera.
                    rotating = false;
                    cameraRotating = false;
                }

                // RIGHT MOUSE
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {

                    if (selectedObject >= 0) {
                        moving = true;
                        cameraMoving = false;
                    } else {
                        moving = false;
                        cameraMoving = true;
                    }
                }
            }

            if (action == GLFW.GLFW_RELEASE) {

                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    rotating = false;
                    cameraRotating = false;
                }

                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    moving = false;
                    cameraMoving = false;
                }
            }
        });

        GLFW.glfwSetCursorPosCallback(window, (w, x, y) -> {

            float dx = (float) (x - lastMouseX);
            float dy = (float) (y - lastMouseY);

            // Rotate selected object
            if (rotating) {

                rotate(
                        dy * 0.5f,
                        dx * 0.5f,
                        0.0f
                );
            }

            // Rotate camera
            if (cameraRotating) {

                cameraRotationX += dy * 0.5f;
                cameraRotationY += dx * 0.5f;
            }

            // Move selected object
            if (moving) {
                moveObjectWithCamera(dx, dy);
            }

            // Move camera
            if (cameraMoving) {

                cameraPositionX += dx * 0.005f;
                cameraPositionY -= dy * 0.005f;
            }

            lastMouseX = x;
            lastMouseY = y;
        });

        GLFW.glfwSetWindowCloseCallback(window, w -> running = false);
    }

    private void moveObjectWithCamera(float dx, float dy) {

        float sensitivity = 0.005f;

        // Mouse movement in screen space
        float screenRight = dx * sensitivity;
        float screenUp = -dy * sensitivity;

        double pitch = Math.toRadians(cameraRotationX);
        double yaw   = Math.toRadians(cameraRotationY);
        double roll  = Math.toRadians(cameraRotationZ);

        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);

        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);

        float cr = (float) Math.cos(roll);
        float sr = (float) Math.sin(roll);

        /*
         * These are the camera's local axes expressed
         * in WORLD space.
         *
         * They are obtained from the inverse of:
         *
         *     Rx(pitch) * Ry(yaw) * Rz(roll)
         */

        // Camera RIGHT
        float rightX = cr * cy;
        float rightY = -sr * cy;
        float rightZ = sy;

        // Camera UP
        float upX = sp * sy * cr + sr * cp;
        float upY = -sp * sr * sy + cp * cr;
        float upZ = -sp * cy;

        /*
         * Convert screen-space movement into world-space movement.
         */
        float worldX =
                rightX * screenRight +
                        upX * screenUp;

        float worldY =
                rightY * screenRight +
                        upY * screenUp;

        float worldZ =
                rightZ * screenRight +
                        upZ * screenUp;

        move(worldX, worldY, worldZ);
    }

    private void renderLoop() {
        try {
            GLFW.glfwMakeContextCurrent(window);

            GL.createCapabilities();
            GLFW.glfwSwapInterval(0);

            initializeOpenGL();
            loadTextures();
            updateViewport();

            while (running) {
                processCommands();
                render();
            }
        } finally {
            cleanupOpenGL();
            GLFW.glfwMakeContextCurrent(0);

            renderFinished = true;
            GLFW.glfwPostEmptyEvent();
        }
    }

    private void initializeOpenGL() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);

        GL11.glColorMaterial(
                GL11.GL_FRONT_AND_BACK,
                GL11.GL_AMBIENT_AND_DIFFUSE
        );

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_POSITION,
                new float[]{2.0f, 3.0f, 4.0f, 1.0f}
        );

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_DIFFUSE,
                new float[]{1.0f, 1.0f, 1.0f, 1.0f}
        );

        GL11.glLightfv(
                GL11.GL_LIGHT0,
                GL11.GL_AMBIENT,
                new float[]{0.2f, 0.2f, 0.2f, 1.0f}
        );
    }

    private void drawMeshForPicking(ScanMesh mesh) {

        float[] vertices = mesh.getVertices();
        int[] indices = mesh.getIndices();

        if (vertices == null || indices == null) {
            return;
        }

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < indices.length; i += 3) {

            int i1 = indices[i];
            int i2 = indices[i + 1];
            int i3 = indices[i + 2];

            float[] p1 = getVertex(vertices, i1);
            float[] p2 = getVertex(vertices, i2);
            float[] p3 = getVertex(vertices, i3);

            GL11.glVertex3f(
                    p1[0],
                    p1[1],
                    p1[2]
            );

            GL11.glVertex3f(
                    p2[0],
                    p2[1],
                    p2[2]
            );

            GL11.glVertex3f(
                    p3[0],
                    p3[1],
                    p3[2]
            );
        }

        GL11.glEnd();
    }

    private void processCommands() {
        Runnable command;

        while ((command = commands.poll()) != null) {
            command.run();
        }

        if (pendingClickX >= 0 && pendingClickY >= 0) {

            double x = pendingClickX;
            double y = pendingClickY;

            pendingClickX = -1;
            pendingClickY = -1;

            pickObject(x, y);
        }
    }

    private void pickObject(double mouseX, double mouseY) {

        updateViewport();

        GL11.glClear(
                GL11.GL_COLOR_BUFFER_BIT |
                        GL11.GL_DEPTH_BUFFER_BIT
        );

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glTranslatef(
                cameraPositionX,
                cameraPositionY,
                cameraPositionZ - cameraDistance
        );

        GL11.glRotatef(
                cameraRotationX,
                1.0f,
                0.0f,
                0.0f
        );

        GL11.glRotatef(
                cameraRotationY,
                0.0f,
                1.0f,
                0.0f
        );

        GL11.glRotatef(
                cameraRotationZ,
                0.0f,
                0.0f,
                1.0f
        );

        for (int i = 0; i < objects.size(); i++) {

            ObjectTransform transform = transforms.get(i);

            GL11.glPushMatrix();

            GL11.glTranslatef(
                    transform.getPositionX(),
                    transform.getPositionY(),
                    transform.getPositionZ()
            );

            GL11.glRotatef(
                    transform.getRotationX(),
                    1.0f,
                    0.0f,
                    0.0f
            );

            GL11.glRotatef(
                    transform.getRotationY(),
                    0.0f,
                    1.0f,
                    0.0f
            );

            GL11.glRotatef(
                    transform.getRotationZ(),
                    0.0f,
                    0.0f,
                    1.0f
            );

            // Object 0 = (1, 0, 0)
            // Object 1 = (2, 0, 0)
            // Object 2 = (3, 0, 0)
            int id = i + 1;

            int r = id & 0xFF;
            int g = (id >> 8) & 0xFF;
            int b = (id >> 16) & 0xFF;

            GL11.glColor3ub(
                    (byte) r,
                    (byte) g,
                    (byte) b
            );

            drawMeshForPicking(objects.get(i));

            GL11.glPopMatrix();
        }

        // Convert Java/GLFW mouse coordinates to OpenGL framebuffer coordinates
        int pixelX = (int) mouseX;

        int pixelY = windowHeight - (int) mouseY;

        if (pixelX >= 0 &&
                pixelX < windowWidth &&
                pixelY >= 0 &&
                pixelY < windowHeight) {

            ByteBuffer pixel = ByteBuffer.allocateDirect(3);

            GL11.glReadPixels(
                    pixelX,
                    pixelY,
                    1,
                    1,
                    GL11.GL_RGB,
                    GL11.GL_UNSIGNED_BYTE,
                    pixel
            );

            int r = Byte.toUnsignedInt(pixel.get(0));
            int g = Byte.toUnsignedInt(pixel.get(1));
            int b = Byte.toUnsignedInt(pixel.get(2));

            int id = r | (g << 8) | (b << 16);
            System.out.println(id);

            if (id == 0 || id > objects.size()) {

                setSelectedObject(-1);

                rotating = false;
                cameraRotating = true;

                System.out.println("[INFO] Nothing selected.");

            } else {

                int objectIndex = id - 1;

                if (objectIndex >= 0 &&
                        objectIndex < objects.size()) {

                    setSelectedObject(objectIndex);

                    rotating = true;
                    cameraRotating = false;
                }
            }
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    }

    @Override
    public void close() {
        running = false;

        if (GLFWManager.isInitialized()) {
            GLFW.glfwPostEmptyEvent();
        }
    }

    void requestCloseFromManager() {
        running = false;
    }

    boolean isRenderFinished() {
        return renderFinished;
    }

    void destroyWindowOnMainThread() {
        if (!GLFWManager.isOwnerThread()) {
            throw new IllegalStateException(
                    "Window destruction must happen on the GLFW main thread"
            );
        }

        if (window == 0 || !renderFinished) {
            return;
        }

        GLFW.glfwDestroyWindow(window);

        window = 0;
        renderThread = null;

        GLFWManager.unregister(this);
    }

    @Override
    public void refresh() {
        // Continuous renderer.
    }

    @Override
    public void resetCamera() {
        cameraDistance = 2.5f;
    }

    @Override
    public void reload() {
        commands.add(() -> {
            cleanupOpenGL();
            loadTextures();
            resetCamera();
        });
    }

    @Override
    public void render() {
        updateViewport();

        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        // Camera transform
        GL11.glTranslatef(
                cameraPositionX,
                cameraPositionY,
                cameraPositionZ - cameraDistance
        );

        GL11.glRotatef(
                cameraRotationX,
                1.0f,
                0.0f,
                0.0f
        );

        GL11.glRotatef(
                cameraRotationY,
                0.0f,
                1.0f,
                0.0f
        );

        GL11.glRotatef(
                cameraRotationZ,
                0.0f,
                0.0f,
                1.0f
        );

        for (int i = 0; i < objects.size(); i++) {

            ScanMesh object = objects.get(i);
            ObjectTransform transform = transforms.get(i);

            GL11.glPushMatrix();

            GL11.glTranslatef(
                    transform.getPositionX(),
                    transform.getPositionY(),
                    transform.getPositionZ()
            );

            GL11.glRotatef(
                    transform.getRotationX(),
                    1.0f,
                    0.0f,
                    0.0f
            );

            GL11.glRotatef(
                    transform.getRotationY(),
                    0.0f,
                    1.0f,
                    0.0f
            );

            GL11.glRotatef(
                    transform.getRotationZ(),
                    0.0f,
                    0.0f,
                    1.0f
            );

            drawMesh(object);

            GL11.glPopMatrix();
        }

        GLFW.glfwSwapBuffers(window);
    }

    private void updateViewport() {
        int width = windowWidth;
        int height = windowHeight;

        if (width == lastViewportWidth && height == lastViewportHeight) {
            return;
        }

        lastViewportWidth = width;
        lastViewportHeight = height;

        GL11.glViewport(0, 0, width, height);
        updateProjection(width, height);
    }

    private void loadTextures() {
        for (ScanMesh object : objects) {
            Material[] materials = object.getVertexMaterials();

            if (materials == null) {
                continue;
            }

            for (Material material : materials) {
                if (material == null || !material.hasTexture()) {
                    continue;
                }

                Texture texture = material.getTexture();

                if (!textureIds.containsKey(texture)) {
                    textureIds.put(texture, createTexture(texture));
                }
            }
        }
    }

    private int createTexture(Texture texture) {
        int id = GL11.glGenTextures();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_LINEAR
        );

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER,
                GL11.GL_LINEAR
        );

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S,
                GL11.GL_REPEAT
        );

        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T,
                GL11.GL_REPEAT
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer image = STBImage.stbi_load_from_memory(
                    ByteBuffer.wrap(texture.getData()),
                    width,
                    height,
                    channels,
                    4
            );

            if (image == null) {
                GL11.glDeleteTextures(id);

                throw new IllegalStateException(
                        "Failed to load texture: " + STBImage.stbi_failure_reason()
                );
            }

            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA,
                    width.get(0),
                    height.get(0),
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    image
            );

            STBImage.stbi_image_free(image);
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        return id;
    }

    private void updateProjection(int width, int height) {
        if (height <= 0) {
            return;
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = (float) width / height;
        float near = 0.1f;
        float far = 100.0f;
        float fov = 60.0f;

        float yScale = (float) (1.0 / Math.tan(Math.toRadians(fov / 2.0)));
        float xScale = yScale / aspect;

        GL11.glFrustum(
                -near * xScale,
                near * xScale,
                -near * yScale,
                near * yScale,
                near,
                far
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private void drawMesh(ScanMesh mesh) {
        float[] vertices = mesh.getVertices();
        int[] indices = mesh.getIndices();
        float[] uvs = mesh.getTextureCoordinates();
        Material[] materials = mesh.getVertexMaterials();

        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < indices.length; i += 3) {
            int i1 = indices[i];
            int i2 = indices[i + 1];
            int i3 = indices[i + 2];

            float[] p1 = getVertex(vertices, i1);
            float[] p2 = getVertex(vertices, i2);
            float[] p3 = getVertex(vertices, i3);

            float[] normal = LightNormalizer.calculateNormal(p1, p2, p3);

            GL11.glNormal3f(normal[0], normal[1], normal[2]);

            drawVertex(p1, getMaterial(materials, i1), uvs, i, 0);
            drawVertex(p2, getMaterial(materials, i2), uvs, i, 1);
            drawVertex(p3, getMaterial(materials, i3), uvs, i, 2);
        }

        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void drawVertex(float[] vertex, Material material, float[] uvs, int triangleIndex, int vertexIndex) {
        if (material != null) {
            float[] color = material.getDiffuseColor();

            GL11.glColor4f(
                    color[0],
                    color[1],
                    color[2],
                    color.length > 3 ? color[3] : 1.0f
            );

            if (material.hasTexture()) {
                bindTexture(material.getTexture());

                if (uvs != null) {
                    int uvIndex = triangleIndex * 2 + vertexIndex * 2;

                    if (uvIndex + 1 < uvs.length) {
                        GL11.glTexCoord2f(
                                uvs[uvIndex],
                                1.0f - uvs[uvIndex + 1]
                        );
                    }
                }
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
        } else {
            GL11.glColor4f(0.7f, 0.7f, 0.7f, 1.0f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
    }

    private float[] getVertex(float[] vertices, int index) {
        int offset = index * 3;

        return new float[]{
                vertices[offset],
                vertices[offset + 1],
                vertices[offset + 2]
        };
    }

    private Material getMaterial(Material[] materials, int index) {
        if (materials == null || index < 0 || index >= materials.length) {
            return null;
        }

        return materials[index];
    }

    private void bindTexture(Texture texture) {
        Integer id = textureIds.get(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id == null ? 0 : id);
    }

    private void cleanupOpenGL() {
        for (int id : textureIds.values()) {
            GL11.glDeleteTextures(id);
        }

        textureIds.clear();
    }

    @Override
    public void zoom(float amount) {
        cameraDistance = Math.clamp(cameraDistance + amount, 0.5f, 20.0f);
    }

    @Override
    public void move(float x, float y, float z) {
        if (selectedObject < 0 || selectedObject >= transforms.size()) {
            return;
        }
        transforms.get(selectedObject).move(x, y, z);
        for (Consumer<Integer> listener : selectionListeners) {
            listener.accept(selectedObject);
        }
    }

    @Override
    public void rotate(float x, float y, float z) {
        if (selectedObject < 0 || selectedObject >= transforms.size()) {
            return;
        }
        transforms.get(selectedObject).rotate(x, y, z);
        for (Consumer<Integer> listener : selectionListeners) {
            listener.accept(selectedObject);
        }
    }


    public ObjectTransform getObjectTransform(int index) {
        if (index < 0 || index >= transforms.size()) {
            return null;
        }

        return transforms.get(index);
    }

    public void setObjectTransform(
            int index,
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ
    ) {
        if (index < 0 || index >= transforms.size()) {
            return;
        }

        ObjectTransform transform = transforms.get(index);

        transform.reset();

        transform.move(
                positionX,
                positionY,
                positionZ
        );

        transform.rotate(
                rotationX,
                rotationY,
                rotationZ
        );
    }

    public int getObjectCount() {
        return objects.size();
    }

    public int getSelectedObject() {
        return selectedObject;
    }

    public void resetSelectedObject() {
        if (selectedObject < 0 || selectedObject >= transforms.size()) {
            return;
        }

        transforms.get(selectedObject).reset();

        // Restore your initial rotation
        transforms.get(selectedObject).rotate(
                25.0f,
                35.0f,
                0.0f
        );
    }

    public void resetAllObjects() {
        for (ObjectTransform transform : transforms) {
            transform.reset();
            transform.rotate(25.0f, 35.0f, 0.0f);
        }
    }
}