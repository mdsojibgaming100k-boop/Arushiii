package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AndroidActionBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sojib AI", appName)
  }

  @Test
  fun `test AndroidActionBridge openWhatsApp returns result`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = AndroidActionBridge(context)
    val result = bridge.openWhatsApp()
    assertNotNull(result)
  }
}
