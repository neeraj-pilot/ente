#ifndef FLUTTER_PLUGIN_FILE_EXPORT_PLUGIN_H_
#define FLUTTER_PLUGIN_FILE_EXPORT_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>
#include <optional>

#include "core/exporter.h"

namespace file_export {

class ReplyDispatcher;

class FileExportPlugin : public flutter::Plugin {
public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  FileExportPlugin(flutter::PluginRegistrarWindows *registrar, HWND window);
  ~FileExportPlugin() override;

  FileExportPlugin(const FileExportPlugin &) = delete;
  FileExportPlugin &operator=(const FileExportPlugin &) = delete;

private:
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
  std::optional<LRESULT> HandleWindowMessage(UINT message, LPARAM parameter);

  flutter::PluginRegistrarWindows *registrar_;
  int window_proc_delegate_;
  HWND window_;
  std::shared_ptr<ReplyDispatcher> replies_;
  Exporter exporter_;
};

} // namespace file_export

#endif
