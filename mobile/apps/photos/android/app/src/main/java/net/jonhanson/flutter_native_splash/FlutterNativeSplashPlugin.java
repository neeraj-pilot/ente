package net.jonhanson.flutter_native_splash;

import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin;

/**
 * Minimal stub so release builds don't fail when the dev-only flutter_native_splash plugin
 * gets referenced in the generated plugin registrant.
 */
public class FlutterNativeSplashPlugin implements FlutterPlugin {
  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    // No-op
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    // No-op
  }
}
