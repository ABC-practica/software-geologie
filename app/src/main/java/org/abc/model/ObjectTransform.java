package org.abc.model;

public class ObjectTransform {

    private float positionX;
    private float positionY;
    private float positionZ;

    private float rotationX;
    private float rotationY;
    private float rotationZ;

    public float getPositionX() {
        return positionX;
    }

    public float getPositionY() {
        return positionY;
    }

    public float getPositionZ() {
        return positionZ;
    }

    public float getRotationX() {
        return rotationX;
    }

    public float getRotationY() {
        return rotationY;
    }

    public float getRotationZ() {
        return rotationZ;
    }

    public void move(float x, float y, float z) {
        positionX += x;
        positionY += y;
        positionZ += z;
    }

    public void rotate(float x, float y, float z) {
        rotationX += x;
        rotationY += y;
        rotationZ += z;
    }

    public void setPositionX(float positionX) {
        this.positionX = positionX;
    }

    public void setPositionY(float positionY) {
        this.positionY = positionY;
    }

    public void setPositionZ(float positionZ) {
        this.positionZ = positionZ;
    }

    public void setRotationX(float rotationX) {
        this.rotationX = rotationX;
    }

    public void setRotationY(float rotationY) {
        this.rotationY = rotationY;
    }

    public void setRotationZ(float rotationZ) {
        this.rotationZ = rotationZ;
    }

    public void reset() {
        positionX = 0.0f;
        positionY = 0.0f;
        positionZ = 0.0f;

        rotationX = 0.0f;
        rotationY = 0.0f;
        rotationZ = 0.0f;
    }
}