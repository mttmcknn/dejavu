package dejavu.internal

import dejavu.ExactLambdaBoundaryTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the same ownership boundaries with Android's Compose test helper. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExactLambdaBoundaryAndroidTest : ExactLambdaBoundaryTest()
