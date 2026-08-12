package billing

fun totalFor(order: Order): Money {
    val subtotal = order.lines.sumOf { it.amount }
    val tax = subtotal * order.taxRate
    return Money(subtotal + tax, order.currency)
}
