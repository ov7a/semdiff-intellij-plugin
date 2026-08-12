package billing

fun apply(discount: Discount, money: Money): Money {
    if (discount.isExpired()) return money
    return Money(money.amount - discount.value, money.currency)
}
