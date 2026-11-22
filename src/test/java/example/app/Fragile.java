package example.app;

/** Misbehaves on purpose: its getter and toString throw. Scripts must still run normally under Lens. */
public class Fragile {

    private int value;

    public int getValue() {
        throw new IllegalStateException("getter exploded");
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int peek() {
        return value;
    }

    @Override
    public String toString() {
        throw new IllegalStateException("toString exploded");
    }
}
