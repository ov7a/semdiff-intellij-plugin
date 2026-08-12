package demo;

import java.util.List;

/**
 * Not one token differs between this file and its counterpart.
 *
 * Only the layout does: wrapped parameter lists, a wrapped call chain, short bodies put on one
 * line, different indentation. This is what a formatter run or a change of code style produces.
 *
 * The built-in diff marks several blocks here, and turning on "Ignore whitespaces" does not help,
 * because the line structure itself changed -- text that was on one line is now on four.
 * A semantic diff reports no differences at all.
 */
public final class Shipping {

    public Quote quote(
            Address origin,
            Address destination,
            Parcel parcel,
            Carrier carrier) {
        return rates
                .of(origin)
                .to(destination)
                .forParcel(parcel)
                .via(carrier)
                .cheapest();
    }

    public Money surcharge(Parcel parcel) {
        if (parcel.isOversized()) { return Money.of(25); }
        if (parcel.isFragile()) { return Money.of(10); }
        return Money.ZERO;
    }

    public List<Carrier> carriersFor(
            Address destination, Parcel parcel, boolean expedited) {
        return registry.lookup(
                destination.country(), parcel.weightClass(), expedited);
    }
}
