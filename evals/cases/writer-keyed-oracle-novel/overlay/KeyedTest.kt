@Test fun keyedAccuracy() {
    runRecompositionTrackingUiTest {
        var callbacks = 0
        setTrackedContent { SideEffect("constant") { callbacks++ }; Value(state.value) }
        // Four settled state changes recompose Value, but callback key stays constant.
        assertEquals(callbacks - 1, getTrackedCount())
    }
    assertTrue(capturedError!!.message!!.contains("Expected"))
}
