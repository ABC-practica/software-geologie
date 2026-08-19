package org.abc.service;

import org.abc.model.Material;
import org.abc.model.ScanMesh;
import org.abc.model.Texture;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ThreeMfLoader implements Loader {

    @Override
    public ScanMesh load(Path path) throws IOException {

        try (ZipFile zip = new ZipFile(path.toFile())) {

            ZipEntry modelEntry = findModel(zip);

            if (modelEntry == null)
                throw new IOException("3MF model not found");

            Document document;

            try (InputStream input = zip.getInputStream(modelEntry)) {

                DocumentBuilderFactory factory =
                        DocumentBuilderFactory.newInstance();

                factory.setNamespaceAware(true);

                document =
                        factory.newDocumentBuilder()
                                .parse(input);
            }

            /*
             * Load all resources before loading the meshes.
             */
            Map<Integer, Material[]> materials =
                    loadMaterialGroups(document);

            Map<Integer, Texture> textures =
                    loadTextures(document, zip);

            Map<Integer, TextureGroup> textureGroups =
                    loadTextureGroups(
                            document,
                            textures
                    );

            /*
             * Build the final mesh.
             */
            ScanMesh mesh =
                    loadMeshes(
                            document,
                            materials,
                            textureGroups
                    );

            return mesh;

        } catch (IOException e) {

            throw e;

        } catch (Exception e) {

            throw new IOException(
                    "Failed to load 3MF file",
                    e
            );
        }
    }

    // ============================================================
    // MESH LOADING
    // ============================================================

    private ScanMesh loadMeshes(
            Document document,
            Map<Integer, Material[]> materials,
            Map<Integer, TextureGroup> textureGroups
    ) {

        List<Float> vertices =
                new ArrayList<>();

        List<Integer> indices =
                new ArrayList<>();

        /*
         * There is one Material for every generated vertex.
         *
         * Because we duplicate the vertices for every triangle:
         *
         * vertex 0 -> material 0
         * vertex 1 -> material 1
         * vertex 2 -> material 2
         *
         * etc.
         */
        List<Material> vertexMaterials =
                new ArrayList<>();

        /*
         * Two floats per vertex:
         *
         * u
         * v
         */
        List<Float> textureCoordinates =
                new ArrayList<>();

        NodeList objects =
                document.getElementsByTagNameNS(
                        "*",
                        "object"
                );

        for (int o = 0; o < objects.getLength(); o++) {

            Element object =
                    (Element) objects.item(o);

            /*
             * Resolve object-level material.
             *
             * Example:
             *
             * <object id="1" pid="5" pindex="2">
             *
             * means:
             *
             * resource 5
             * material index 2
             */
            Material objectMaterial =
                    resolveObjectMaterial(
                            object,
                            materials,
                            textureGroups
                    );

            NodeList meshNodes =
                    object.getElementsByTagNameNS(
                            "*",
                            "mesh"
                    );

            for (
                    int m = 0;
                    m < meshNodes.getLength();
                    m++
            ) {

                Element mesh =
                        (Element) meshNodes.item(m);

                List<float[]> meshVertices =
                        loadVertices(mesh);

                NodeList triangles =
                        mesh.getElementsByTagNameNS(
                                "*",
                                "triangle"
                        );

                for (
                        int i = 0;
                        i < triangles.getLength();
                        i++
                ) {

                    Element triangle =
                            (Element) triangles.item(i);

                    int v1 =
                            parseInt(
                                    triangle,
                                    "v1"
                            );

                    int v2 =
                            parseInt(
                                    triangle,
                                    "v2"
                            );

                    int v3 =
                            parseInt(
                                    triangle,
                                    "v3"
                            );

                    int base =
                            vertices.size() / 3;

                    /*
                     * Add the three triangle vertices.
                     */
                    addVertex(
                            meshVertices,
                            v1,
                            vertices
                    );

                    addVertex(
                            meshVertices,
                            v2,
                            vertices
                    );

                    addVertex(
                            meshVertices,
                            v3,
                            vertices
                    );

                    /*
                     * Add indices.
                     */
                    indices.add(base);
                    indices.add(base + 1);
                    indices.add(base + 2);

                    /*
                     * ------------------------------------------------
                     * MATERIAL
                     * ------------------------------------------------
                     *
                     * If the triangle has its own pid, use it.
                     *
                     * Otherwise use the object's material.
                     */
                    String pid =
                            triangle.getAttribute("pid");

                    if (!pid.isBlank()) {

                        addVertexMaterials(
                                triangle,
                                materials,
                                textureGroups,
                                vertexMaterials
                        );

                    } else {

                        /*
                         * No triangle-level material.
                         *
                         * All three vertices use the object material.
                         */
                        vertexMaterials.add(
                                objectMaterial
                        );

                        vertexMaterials.add(
                                objectMaterial
                        );

                        vertexMaterials.add(
                                objectMaterial
                        );
                    }

                    /*
                     * ------------------------------------------------
                     * TEXTURE COORDINATES
                     * ------------------------------------------------
                     */
                    TextureCoordinates[] coords =
                            resolveTextureCoordinates(
                                    triangle,
                                    textureGroups
                            );

                    if (coords != null) {

                        for (
                                TextureCoordinates c :
                                coords
                        ) {

                            textureCoordinates.add(
                                    c.u()
                            );

                            textureCoordinates.add(
                                    c.v()
                            );
                        }

                    } else {

                        /*
                         * No texture.
                         *
                         * Still add three UVs so the UV array remains
                         * aligned with the vertex array.
                         */
                        addEmptyUV(textureCoordinates);
                        addEmptyUV(textureCoordinates);
                        addEmptyUV(textureCoordinates);
                    }
                }
            }
        }

        /*
         * If there are no UVs at all, return null.
         *
         * Otherwise return the generated UV array.
         */
        float[] uvArray =
                textureCoordinates.isEmpty()
                        ? null
                        : toFloatArray(
                        textureCoordinates
                );

        return new ScanMesh(
                toFloatArray(vertices),

                indices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray(),

                uvArray,

                vertexMaterials.toArray(
                        new Material[0]
                )
        );
    }

    // ============================================================
    // MATERIAL RESOLUTION
    // ============================================================

    private Material resolveObjectMaterial(
            Element object,
            Map<Integer, Material[]> materials,
            Map<Integer, TextureGroup> textureGroups
    ) {

        int pid =
                parseInt(
                        object,
                        "pid",
                        -1
                );

        if (pid < 0)
            return null;

        int pindex =
                parseInt(
                        object,
                        "pindex",
                        0
                );

        /*
         * Normal material/color group.
         */
        Material[] materialGroup =
                materials.get(pid);

        if (materialGroup != null) {

            if (
                    pindex < 0 ||
                            pindex >= materialGroup.length
            ) {
                return null;
            }

            return materialGroup[pindex];
        }

        /*
         * Texture group.
         */
        TextureGroup textureGroup =
                textureGroups.get(pid);

        if (textureGroup != null) {

            return new Material(
                    new float[]{
                            1.0f,
                            1.0f,
                            1.0f
                    },
                    textureGroup.texture()
            );
        }

        return null;
    }

    private void addVertexMaterials(
            Element triangle,
            Map<Integer, Material[]> materials,
            Map<Integer, TextureGroup> textureGroups,
            List<Material> output
    ) {

        String pid =
                triangle.getAttribute("pid");

        if (pid.isBlank()) {

            output.add(null);
            output.add(null);
            output.add(null);

            return;
        }

        int id;

        try {

            id =
                    Integer.parseInt(pid);

        } catch (NumberFormatException e) {

            output.add(null);
            output.add(null);
            output.add(null);

            return;
        }

        /*
         * ------------------------------------------------------------
         * BASE MATERIALS / COLOR GROUP
         * ------------------------------------------------------------
         */
        Material[] materialGroup =
                materials.get(id);

        if (materialGroup != null) {

            output.add(
                    resolveVertexMaterial(
                            triangle,
                            "p1",
                            materialGroup
                    )
            );

            output.add(
                    resolveVertexMaterial(
                            triangle,
                            "p2",
                            materialGroup
                    )
            );

            output.add(
                    resolveVertexMaterial(
                            triangle,
                            "p3",
                            materialGroup
                    )
            );

            return;
        }

        /*
         * ------------------------------------------------------------
         * TEXTURE GROUP
         * ------------------------------------------------------------
         */
        TextureGroup textureGroup =
                textureGroups.get(id);

        if (textureGroup != null) {

            /*
             * Texture groups don't contain a normal RGB material.
             *
             * The actual texture is handled by the texture
             * coordinates.
             */
            Material textureMaterial =
                    new Material(
                            new float[]{
                                    1.0f,
                                    1.0f,
                                    1.0f
                            },
                            textureGroup.texture()
                    );

            output.add(textureMaterial);
            output.add(textureMaterial);
            output.add(textureMaterial);

            return;
        }

        /*
         * Unknown material resource.
         */
        output.add(null);
        output.add(null);
        output.add(null);
    }

    private Material resolveVertexMaterial(
            Element triangle,
            String attribute,
            Material[] materials
    ) {

        if (materials == null)
            return null;

        int index =
                parseInt(
                        triangle,
                        attribute,
                        -1
                );

        if (
                index < 0 ||
                        index >= materials.length
        ) {
            return null;
        }

        return materials[index];
    }

    // ============================================================
    // VERTICES
    // ============================================================

    private List<float[]> loadVertices(
            Element mesh
    ) {

        List<float[]> vertices =
                new ArrayList<>();

        NodeList nodes =
                mesh.getElementsByTagNameNS(
                        "*",
                        "vertex"
                );

        for (
                int i = 0;
                i < nodes.getLength();
                i++
        ) {

            Element v =
                    (Element) nodes.item(i);

            vertices.add(
                    new float[]{
                            Float.parseFloat(
                                    v.getAttribute("x")
                            ),

                            Float.parseFloat(
                                    v.getAttribute("y")
                            ),

                            Float.parseFloat(
                                    v.getAttribute("z")
                            )
                    }
            );
        }

        return vertices;
    }

    private void addVertex(
            List<float[]> vertices,
            int index,
            List<Float> output
    ) {

        if (
                index < 0 ||
                        index >= vertices.size()
        ) {
            throw new IllegalArgumentException(
                    "Invalid 3MF vertex index: " +
                            index
            );
        }

        float[] vertex =
                vertices.get(index);

        output.add(vertex[0]);
        output.add(vertex[1]);
        output.add(vertex[2]);
    }

    // ============================================================
    // 3MF MODEL FINDING
    // ============================================================

    private ZipEntry findModel(
            ZipFile zip
    ) {

        /*
         * Standard location.
         */
        ZipEntry model =
                zip.getEntry(
                        "3D/3dmodel.model"
                );

        if (model != null)
            return model;

        /*
         * Fallback.
         */
        for (
                Enumeration<? extends ZipEntry> entries =
                zip.entries();
                entries.hasMoreElements();
        ) {

            ZipEntry entry =
                    entries.nextElement();

            if (
                    !entry.isDirectory() &&
                            entry.getName()
                                    .toLowerCase()
                                    .endsWith(".model")
            ) {

                return entry;
            }
        }

        return null;
    }

    // ============================================================
    // MATERIAL GROUPS
    // ============================================================

    private Map<Integer, Material[]> loadMaterialGroups(
            Document document
    ) {

        Map<Integer, Material[]> groups =
                new HashMap<>();

        loadBaseMaterials(
                document,
                groups
        );

        loadColorGroups(
                document,
                groups
        );

        return groups;
    }

    private void loadBaseMaterials(
            Document document,
            Map<Integer, Material[]> groups
    ) {

        NodeList nodes =
                document.getElementsByTagNameNS(
                        "*",
                        "basematerials"
                );

        for (
                int i = 0;
                i < nodes.getLength();
                i++
        ) {

            Element group =
                    (Element) nodes.item(i);

            int id =
                    parseId(group);

            if (id < 0)
                continue;

            NodeList bases =
                    group.getElementsByTagNameNS(
                            "*",
                            "base"
                    );

            Material[] materials =
                    new Material[bases.getLength()];

            for (
                    int j = 0;
                    j < bases.getLength();
                    j++
            ) {

                Element base =
                        (Element) bases.item(j);

                String color =
                        base.getAttribute(
                                "displaycolor"
                        );

                /*
                 * IMPORTANT:
                 *
                 * Keep the array index exactly the same
                 * as the <base> index.
                 *
                 * pindex/p1/p2/p3 refer to this index.
                 */
                if (color.isBlank()) {

                    materials[j] = null;

                } else {

                    materials[j] =
                            new Material(
                                    parseColor(color)
                            );
                }
            }

            groups.put(
                    id,
                    materials
            );
        }
    }

    private void loadColorGroups(
            Document document,
            Map<Integer, Material[]> groups
    ) {

        NodeList nodes =
                document.getElementsByTagNameNS(
                        "*",
                        "colorgroup"
                );

        for (
                int i = 0;
                i < nodes.getLength();
                i++
        ) {

            Element group =
                    (Element) nodes.item(i);

            int id =
                    parseId(group);

            if (id < 0)
                continue;

            NodeList colors =
                    group.getElementsByTagNameNS(
                            "*",
                            "color"
                    );

            Material[] materials =
                    new Material[
                            colors.getLength()
                            ];

            for (
                    int j = 0;
                    j < colors.getLength();
                    j++
            ) {

                Element color =
                        (Element) colors.item(j);

                String value =
                        color.getAttribute(
                                "color"
                        );

                if (value.isBlank()) {

                    materials[j] = null;

                } else {

                    materials[j] =
                            new Material(
                                    parseColor(value)
                            );
                }
            }

            groups.put(
                    id,
                    materials
            );
        }
    }

    // ============================================================
    // TEXTURES
    // ============================================================

    private Map<Integer, Texture> loadTextures(
            Document document,
            ZipFile zip
    ) throws IOException {

        Map<Integer, Texture> textures =
                new HashMap<>();

        NodeList nodes =
                document.getElementsByTagNameNS(
                        "*",
                        "texture2d"
                );

        for (
                int i = 0;
                i < nodes.getLength();
                i++
        ) {

            Element element =
                    (Element) nodes.item(i);

            int id =
                    parseId(element);

            if (id < 0)
                continue;

            String path =
                    element.getAttribute(
                            "path"
                    );

            String type =
                    element.getAttribute(
                            "contenttype"
                    );

            if (path.isBlank())
                continue;

            ZipEntry entry =
                    zip.getEntry(
                            normalizePath(path)
                    );

            if (entry == null) {

                /*
                 * Some exporters store the path with
                 * a leading slash.
                 */
                entry =
                        zip.getEntry(
                                normalizePath(
                                        path
                                )
                        );
            }

            if (entry == null)
                continue;

            try (
                    InputStream input =
                            zip.getInputStream(entry)
            ) {

                textures.put(
                        id,
                        new Texture(
                                readAllBytes(input),
                                type
                        )
                );
            }
        }

        return textures;
    }

    private Map<Integer, TextureGroup> loadTextureGroups(
            Document document,
            Map<Integer, Texture> textures
    ) {

        Map<Integer, TextureGroup> groups =
                new HashMap<>();

        NodeList nodes =
                document.getElementsByTagNameNS(
                        "*",
                        "texture2dgroup"
                );

        for (
                int i = 0;
                i < nodes.getLength();
                i++
        ) {

            Element group =
                    (Element) nodes.item(i);

            int id =
                    parseId(group);

            if (id < 0)
                continue;

            String texidString =
                    group.getAttribute(
                            "texid"
                    );

            int textureId;

            try {

                textureId =
                        Integer.parseInt(
                                texidString
                        );

            } catch (NumberFormatException e) {

                continue;
            }

            Texture texture =
                    textures.get(textureId);

            if (texture == null)
                continue;

            NodeList coordNodes =
                    group.getElementsByTagNameNS(
                            "*",
                            "tex2coord"
                    );

            List<TextureCoordinates> coordinates =
                    new ArrayList<>();

            for (
                    int j = 0;
                    j < coordNodes.getLength();
                    j++
            ) {

                Element c =
                        (Element) coordNodes.item(j);

                try {

                    float u =
                            Float.parseFloat(
                                    c.getAttribute(
                                            "u"
                                    )
                            );

                    float v =
                            Float.parseFloat(
                                    c.getAttribute(
                                            "v"
                                    )
                            );

                    coordinates.add(
                            new TextureCoordinates(
                                    u,
                                    v
                            )
                    );

                } catch (NumberFormatException ignored) {
                }
            }

            groups.put(
                    id,
                    new TextureGroup(
                            texture,
                            coordinates
                    )
            );
        }

        return groups;
    }

    // ============================================================
    // TEXTURE COORDINATES
    // ============================================================

    private TextureCoordinates[] resolveTextureCoordinates(
            Element triangle,
            Map<Integer, TextureGroup> groups
    ) {

        String pid =
                triangle.getAttribute(
                        "pid"
                );

        if (pid.isBlank())
            return null;

        int id;

        try {

            id =
                    Integer.parseInt(pid);

        } catch (NumberFormatException e) {

            return null;
        }

        TextureGroup group =
                groups.get(id);

        if (group == null)
            return null;

        return new TextureCoordinates[]{
                getCoordinate(
                        triangle,
                        "p1",
                        group
                ),

                getCoordinate(
                        triangle,
                        "p2",
                        group
                ),

                getCoordinate(
                        triangle,
                        "p3",
                        group
                )
        };
    }

    private TextureCoordinates getCoordinate(
            Element triangle,
            String attribute,
            TextureGroup group
    ) {

        int index =
                parseInt(
                        triangle,
                        attribute,
                        -1
                );

        if (
                index < 0 ||
                        index >= group.coordinates().size()
        ) {

            throw new IllegalArgumentException(
                    "Invalid 3MF texture coordinate: " +
                            index
            );
        }

        return group.coordinates().get(index);
    }

    private void addEmptyUV(
            List<Float> output
    ) {

        output.add(0.0f);
        output.add(0.0f);
    }

    // ============================================================
    // PARSING
    // ============================================================

    private int parseId(
            Element element
    ) {

        return parseInt(
                element,
                "id",
                -1
        );
    }

    private int parseInt(
            Element element,
            String attribute
    ) {

        return parseInt(
                element,
                attribute,
                Integer.MIN_VALUE
        );
    }

    private int parseInt(
            Element element,
            String attribute,
            int defaultValue
    ) {

        String value =
                element.getAttribute(
                        attribute
                );

        if (value.isBlank()) {

            if (
                    defaultValue !=
                            Integer.MIN_VALUE
            ) {

                return defaultValue;
            }

            throw new IllegalArgumentException(
                    "Missing 3MF attribute: " +
                            attribute
            );
        }

        try {

            return Integer.parseInt(value);

        } catch (NumberFormatException e) {

            if (
                    defaultValue !=
                            Integer.MIN_VALUE
            ) {

                return defaultValue;
            }

            throw e;
        }
    }

    // ============================================================
    // COLORS
    // ============================================================

    private float[] parseColor(String value) {

        String color = value.trim();

        if (color.startsWith("#")) {
            color = color.substring(1);
        }

        /*
         * 3MF colors:
         *
         * #RRGGBB
         * #RRGGBBAA
         *
         * Alpha is LAST, not first.
         */
        if (color.length() != 6 && color.length() != 8) {
            throw new IllegalArgumentException(
                    "Invalid 3MF color: " + value
            );
        }

        int r = Integer.parseInt(
                color.substring(0, 2),
                16
        );

        int g = Integer.parseInt(
                color.substring(2, 4),
                16
        );

        int b = Integer.parseInt(
                color.substring(4, 6),
                16
        );

        /*
         * #RRGGBB
         */
        if (color.length() == 6) {

            return new float[]{
                    r / 255.0f,
                    g / 255.0f,
                    b / 255.0f
            };
        }

        /*
         * #RRGGBBAA
         */
        int a = Integer.parseInt(
                color.substring(6, 8),
                16
        );

        return new float[]{
                r / 255.0f,
                g / 255.0f,
                b / 255.0f,
                a / 255.0f
        };
    }

    // ============================================================
    // ZIP / FILE HELPERS
    // ============================================================

    private String normalizePath(
            String path
    ) {

        /*
         * 3MF paths normally use '/'.
         */
        path =
                path.replace(
                        '\\',
                        '/'
                );

        while (path.startsWith("/"))
            path =
                    path.substring(1);

        return path;
    }

    private byte[] readAllBytes(
            InputStream input
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer =
                new byte[8192];

        int read;

        while (
                (read =
                        input.read(buffer))
                        != -1
        ) {

            output.write(
                    buffer,
                    0,
                    read
            );
        }

        return output.toByteArray();
    }

    // ============================================================
    // ARRAYS
    // ============================================================

    private float[] toFloatArray(
            List<Float> values
    ) {

        float[] result =
                new float[values.size()];

        for (
                int i = 0;
                i < values.size();
                i++
        ) {

            result[i] =
                    values.get(i);
        }

        return result;
    }

    // ============================================================
    // RECORDS
    // ============================================================

    private record TextureGroup(
            Texture texture,
            List<TextureCoordinates> coordinates
    ) {
    }

    private record TextureCoordinates(
            float u,
            float v
    ) {
    }
}