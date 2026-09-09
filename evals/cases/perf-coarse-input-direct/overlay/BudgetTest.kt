// Existing settled regression: scroll index moves 1 -> 2 -> 3 -> 4.
// The header text remains Back to top; the sibling stays Help.
onNodeWithTag("header").assertTextEquals("Back to top")
onNodeWithTag("header").assertRecompositions(atMost = 1)
onNodeWithTag("static").assertStable()
