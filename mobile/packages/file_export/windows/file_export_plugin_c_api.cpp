#include "include/file_export/file_export_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "file_export_plugin.h"

void FileExportPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  file_export::FileExportPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
