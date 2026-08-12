def gross(order):
    return order.amount * (1 + order.tax_rate)


def report(orders):
    total = sum(gross(order) for order in orders)
    return f"{total:.2f}"
