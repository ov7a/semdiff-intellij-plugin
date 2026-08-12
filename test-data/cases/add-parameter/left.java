package billing;

public final class Invoice {
    public static Invoice create(Customer customer, List<Line> lines) {
        return new Invoice(customer, lines, Currency.EUR);
    }
}
