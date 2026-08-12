package billing;

public final class Invoice {
    public static Invoice create(Customer customer, List<Line> lines, Currency currency) {
        return new Invoice(customer, lines, currency);
    }
}
