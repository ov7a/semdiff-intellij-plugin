package demo;

import java.util.List;
import java.util.Map;

/**
 * One file that exercises everything the plugin can show.
 *
 * Each numbered section changes in exactly one way, so you can point at a section and say which
 * feature it demonstrates. See the list in README.md under "Seeing the difference".
 */
public final class Pricing {

    // ---------------------------------------------------------------------------------
    // 1. REFORMATTED. Not one token differs -- the parameter list, the loop header, two
    //    short bodies and a long argument are simply wrapped onto different lines.
    //    Built-in diff: marks all of it, with "Ignore whitespaces" on as well, because the
    //    line structure changed. difftastic and diffsitter: mark nothing here at all.
    // ---------------------------------------------------------------------------------
    public Money subtotal(List<Line> lines, Rules rules) {
        Money running = Money.ZERO;
        for (Line line : lines) {
            if (line.isVoided()) {
                continue;
            }
            Money amount = line.amount();
            if (rules.appliesTo(line)) {
                amount = amount.times(rules.factorFor(line));
            }
            if (amount.isNegative()) {
                throw new IllegalStateException("negative line amount");
            }
            running = running.plus(amount);
        }
        if (running.isNegative()) {
            return Money.ZERO;
        }
        return running;
    }

    // ---------------------------------------------------------------------------------
    // 2. A LOCAL RENAMED at four sites. Built-in marks four whole lines; a semantic diff
    //    marks just the identifier, four times.
    // ---------------------------------------------------------------------------------
    public Money total(List<Line> lines, Rules rules, double rate) {
        Money sum = subtotal(lines, rules);
        Money tax = sum.times(rate);
        Money shipping = shippingFor(sum);
        return sum.plus(tax).plus(shipping);
    }

    // ---------------------------------------------------------------------------------
    // 3. A STRING literal. Experimental viewer: magenta bold underline.
    // ---------------------------------------------------------------------------------
    public String describe(Money money) {
        return "Total due: " + money;
    }

    // ---------------------------------------------------------------------------------
    // 4. A COMMENT reworded, code untouched. Experimental viewer: blue bold underline.
    // ---------------------------------------------------------------------------------
    // Rounds to whole cents.
    public Money round(Money money) {
        return money.roundTo(2);
    }

    // ---------------------------------------------------------------------------------
    // 5. A TYPE name changed. Experimental viewer: olive dotted underline.
    // ---------------------------------------------------------------------------------
    public Money discountAll(List<Discount> discounts, Money money) {
        for (Discount discount : discounts) {
            money = discount.applyTo(money);
        }
        return money;
    }

    // ---------------------------------------------------------------------------------
    // 6. A KEYWORD added. Experimental viewer: teal wave.
    // ---------------------------------------------------------------------------------
    public Money shippingFor(Money subtotal) {
        return subtotal.isAbove(Money.of(50)) ? Money.ZERO : Money.of(5);
    }

    // ---------------------------------------------------------------------------------
    // 7. DELIMITERS changed: an array parameter becomes varargs.
    // ---------------------------------------------------------------------------------
    public Money sumOf(Money[] amounts) {
        return Money.sum(amounts);
    }

    // ---------------------------------------------------------------------------------
    // 8. MOVED. These two are swapped over and nothing else about them changes, which is
    //    what lets sem call it a reorder rather than a modification.
    // ---------------------------------------------------------------------------------
    public Money floorAt(Money money, Money floor) {
        return money.isBelow(floor) ? floor : money;
    }

    public Money capAt(Money money, Money cap) {
        return money.isAbove(cap) ? cap : money;
    }

    // ---------------------------------------------------------------------------------
    // 9. DELETED here, and a different method ADDED on the other side.
    // ---------------------------------------------------------------------------------
    public Money legacyRate(Money money) {
        return money.times(0.15);
    }

    // ---------------------------------------------------------------------------------
    // 10. UNTOUCHED, so there is somewhere for every viewer to agree.
    // ---------------------------------------------------------------------------------
    public Map<String, Money> byCurrency(List<Line> lines) {
        return Money.group(lines);
    }
}
