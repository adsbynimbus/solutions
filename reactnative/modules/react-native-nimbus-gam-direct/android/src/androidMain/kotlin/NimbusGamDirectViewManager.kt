package com.adsbynimbus.solutions.react.direct

import android.graphics.Color
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.NimbusGamDirectViewManagerInterface
import com.facebook.react.viewmanagers.NimbusGamDirectViewManagerDelegate

@ReactModule(name = NimbusGamDirectViewManager.NAME)
class NimbusGamDirectViewManager : SimpleViewManager<NimbusGamDirectView>(),
  NimbusGamDirectViewManagerInterface<NimbusGamDirectView> {
  private val mDelegate: ViewManagerDelegate<NimbusGamDirectView>

  init {
    mDelegate = NimbusGamDirectViewManagerDelegate(this)
  }

  override fun getDelegate(): ViewManagerDelegate<NimbusGamDirectView>? {
    return mDelegate
  }

  override fun getName(): String {
    return NAME
  }

  public override fun createViewInstance(context: ThemedReactContext): NimbusGamDirectView {
    return NimbusGamDirectView(context)
  }

  @ReactProp(name = "color")
  override fun setColor(view: NimbusGamDirectView?, color: String?) {
    view?.setBackgroundColor(Color.parseColor(color))
  }

  companion object {
    const val NAME = "NimbusGamDirectView"
  }
}
