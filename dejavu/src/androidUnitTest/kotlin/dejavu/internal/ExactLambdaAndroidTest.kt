package dejavu.internal

import dejavu.ExactLambdaInstanceTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Run the identical SideEffect-grounded cases through Android's real Compose test helper. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExactLambdaAndroidTest : ExactLambdaInstanceTest()
