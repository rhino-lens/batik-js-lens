package example.app;

public class LineItem {

    private final String sku;
    private int quantity;
    private final double unitPrice;

    public LineItem(String sku, int quantity, double unitPrice) {
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    /** Rejects negative quantities, to test that a setter's own exception reaches the script unchanged. */
    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be >= 0 but was " + quantity);
        }
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    @Override
    public String toString() {
        return sku + " x" + quantity;
    }
}
