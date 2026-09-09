@Test fun diagnosticMessage() {
    var caught: UnexpectedRecompositionsError? = null
    runRecompositionTrackingUiTest {
        setTrackedContent { DeliberatelyInefficient() }
        driveFourSettledUpdates()
        caught = assertFailsWith<UnexpectedRecompositionsError> { onNodeWithTag("target").assertStable() }
    }
    assertTrue(caught!!.message!!.contains("Expected"))
}
