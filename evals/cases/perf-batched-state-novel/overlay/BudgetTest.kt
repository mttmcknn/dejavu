// a and b updated together in a single mutable snapshot, followed by one idle.
// UI displays both new values. Independent unkeyed SideEffect delta=1; DejaVu=1.
onNodeWithTag("total").assertRecompositions(atMost = 1)
