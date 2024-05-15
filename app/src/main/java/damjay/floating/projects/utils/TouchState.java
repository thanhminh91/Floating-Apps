package damjay.floating.projects.utils;

public class TouchState {
    public static final int MOVE_TOLERANCE = 5;

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
        return originalX + (int) getMoveX();
    }

    public int updatedPositionY() {
        return originalY + (int) getMoveY();
    }

    public float getMoveX() {
        return finalX - initialX;
    }

    public float getMoveY() {
        return finalY - initialY;
    }

    public boolean hasMoved() {
        return Math.abs(getMoveX()) > MOVE_TOLERANCE || Math.abs(getMoveY()) > MOVE_TOLERANCE;
    }
    
}
