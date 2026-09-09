class Summary(val items: MutableList<String>)
@Composable fun Header(summary: Summary) { Text(summary.items.size.toString()) }
