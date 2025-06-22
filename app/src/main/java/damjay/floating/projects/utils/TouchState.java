package damjay.floating.projects.utils;

public class TouchState {
    public static int moveTolerance = 5;

    private float initialX;
    private float finalX;
    private float initialY;
    private float finalY;
    private int originalX;
    private int originalY;

    private static TouchState instance;

    private TouchState() {}

    public static TouchState newInstance() {
        return new TouchState();
    }

    public static TouchState getInstance() {
        if (instance == null)
            instance = new TouchState();
        return instance;
    }

    public void setInitialPosition(float initialX, float initialY) {
        this.initialX = initialX;
        this.initialY = initialY;
        setFinalPosition(initialX, initialY);
    }

    public void setFinalPosition(float finalX, float finalY) {
        this.finalX = finalX;
        this.finalY = finalY;
    }

    public void setOriginalPosition(int x, int y) {
        originalX = x;
        originalY = y;
    }

    public int updatedPositionX() {
        int updatedX = originalX + (int) getMoveX();
        return updatedX < 0 ? 0 : updatedX;
    }

    public int updatedPositionY() {
        int updatedY = originalY + (int) getMoveY();
        return updatedY < 0 ? 0 : updatedY;
    }

    public float getMoveX() {
        return finalX - initialX;
    }

    public float getMoveY() {
        return finalY - initialY;
    }

    public boolean hasMoved() {
        return Math.abs(getMoveX()) > moveTolerance || Math.abs(getMoveY()) > moveTolerance;
    }

    public void updateInitialTouch(float x, float y) {
        setInitialPosition(x, y);
    }

    public void updateViewPosition(int x, int y) {
        setOriginalPosition(x, y);
    }

    // Methods for ClipboardService compatibility
    public void savePosition(float x, float y) {
        setInitialPosition(x, y);
    }

    public void saveCoordinates(int x, int y) {
        setOriginalPosition(x, y);
    }

    public float getStartRawX() {
        return initialX;
    }

    public float getStartRawY() {
        return initialY;
    }

    public int getCoordinateX() {
        return originalX;
    }

    public int getCoordinateY() {
        return originalY;
    }
}
