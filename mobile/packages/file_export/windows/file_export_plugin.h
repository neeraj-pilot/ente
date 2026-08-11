#ifndef FLUTTER_PLUGIN_FILE_EXPORT_PLUGIN_H_
#define FLUTTER_PLUGIN_FILE_EXPORT_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>

#include "core/exporter.h"

namespace file_export {

class FileExportPlugin : public flutter::Plugin {
public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  explicit FileExportPlugin(HWND window);
  ~FileExportPlugin() override = default;

  FileExportPlugin(const FileExportPlugin &) = delete;
  FileExportPlugin &operator=(const FileExportPlugin &) = delete;

private:
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);

  HWND window_;
  Exporter exporter_;
};

} // namespace file_export

#endif
