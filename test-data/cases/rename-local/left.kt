package billing

fun totalFor(order: Order): Money {
    val sum = order.lines.sumOf { it.amount }
    val tax = sum * order.taxRate
    return Money(sum + tax, order.currency)
}
