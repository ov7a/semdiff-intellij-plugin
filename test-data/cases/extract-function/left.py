def report(orders):
    total = 0
    for order in orders:
        total += order.amount * (1 + order.tax_rate)
    return f"{total:.2f}"
