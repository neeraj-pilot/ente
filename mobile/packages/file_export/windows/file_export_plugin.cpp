#include "file_export_plugin.h"

#include <flutter/standard_method_codec.h>

#include <optional>
#include <utility>

namespace file_export {
namespace {

constexpr char kChannel[] = "io.ente.file_export";

const flutter::EncodableValue *Find(const flutter::EncodableMap &map,
                                    const char *key) {
  const auto value = map.find(flutter::EncodableValue(key));
  return value == map.end() ? nullptr : &value->second;
}

std::optional<std::wstring> Wide(const std::string &value) {
  if (value.empty())
    return std::wstring();
  const int size =
      MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                          static_cast<int>(value.size()), nullptr, 0);
  if (size <= 0)
    return std::nullopt;
  std::wstring result(size, L'\0');
  if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                          static_cast<int>(value.size()), result.data(),
                          size) <= 0) {
    return std::nullopt;
  }
  return result;
}

std::optional<ExportRequest> Decode(const flutter::EncodableValue *value) {
  const auto *arguments =
      value == nullptr ? nullptr : std::get_if<flutter::EncodableMap>(value);
  if (arguments == nullptr)
    return std::nullopt;
  const auto *file_name_value = Find(*arguments, "fileName");
  const auto *mime_type_value = Find(*arguments, "mimeType");
  const auto *source_value = Find(*arguments, "source");
  if (file_name_value == nullptr || mime_type_value == nullptr ||
      source_value == nullptr) {
    return std::nullopt;
  }
  const auto *file_name = std::get_if<std::string>(file_name_value);
  const auto *mime_type = std::get_if<std::string>(mime_type_value);
  const auto *source = std::get_if<flutter::EncodableMap>(source_value);
  if (file_name == nullptr || mime_type == nullptr || source == nullptr) {
    return std::nullopt;
  }
  const auto wide_name = Wide(*file_name);
  if (!wide_name) {
    return std::nullopt;
  }

  const auto *type_value = Find(*source, "type");
  const auto *type =
      type_value == nullptr ? nullptr : std::get_if<std::string>(type_value);
  if (type == nullptr)
    return std::nullopt;
  ExportSource export_source;
  if (*type == "bytes") {
    const auto *bytes_value = Find(*source, "bytes");
    const auto *bytes = bytes_value == nullptr
                            ? nullptr
                            : std::get_if<std::vector<uint8_t>>(bytes_value);
    if (bytes == nullptr)
      return std::nullopt;
    export_source = *bytes;
  } else if (*type == "file") {
    const auto *path_value = Find(*source, "path");
    const auto *path =
        path_value == nullptr ? nullptr : std::get_if<std::string>(path_value);
    if (path == nullptr)
      return std::nullopt;
    auto wide_path = Wide(*path);
    if (!wide_path) {
      return std::nullopt;
    }
    export_source = std::move(*wide_path);
  } else {
    return std::nullopt;
  }
  ExportRequest request{*wide_name, *mime_type, std::move(export_source)};
  return request.IsValid() ? std::optional<ExportRequest>(std::move(request))
                           : std::nullopt;
}

const char *FailureName(ExportFailure failure) {
  switch (failure) {
  case ExportFailure::kBusy:
    return "busy";
  case ExportFailure::kSourceMissing:
    return "sourceMissing";
  case ExportFailure::kSourceUnreadable:
    return "sourceUnreadable";
  case ExportFailure::kPresentationFailed:
    return "presentationFailed";
  case ExportFailure::kWriteFailed:
    return "writeFailed";
  }
  return "writeFailed";
}

flutter::EncodableValue Encode(const ExportResult &result) {
  flutter::EncodableMap value;
  switch (result.status) {
  case ExportResult::Status::kExported:
    value[flutter::EncodableValue("status")] =
        flutter::EncodableValue("exported");
    value[flutter::EncodableValue("location")] =
        flutter::EncodableValue(result.location);
    break;
  case ExportResult::Status::kCancelled:
    value[flutter::EncodableValue("status")] =
        flutter::EncodableValue("cancelled");
    break;
  case ExportResult::Status::kFailed:
    value[flutter::EncodableValue("status")] =
        flutter::EncodableValue("failed");
    value[flutter::EncodableValue("reason")] =
        flutter::EncodableValue(FailureName(result.failure));
    if (!result.message.empty()) {
      value[flutter::EncodableValue("message")] =
          flutter::EncodableValue(result.message);
    }
    break;
  }
  return flutter::EncodableValue(value);
}

} // namespace

void FileExportPlugin::RegisterWithRegistrar(
    flutter::PluginRegistrarWindows *registrar) {
  auto channel =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          registrar->messenger(), kChannel,
          &flutter::StandardMethodCodec::GetInstance());
  auto *view = registrar->GetView();
  auto plugin = std::make_unique<FileExportPlugin>(
      view == nullptr ? nullptr : view->GetNativeWindow());
  channel->SetMethodCallHandler(
      [plugin_pointer = plugin.get()](const auto &call, auto result) {
        plugin_pointer->HandleMethodCall(call, std::move(result));
      });
  registrar->AddPlugin(std::move(plugin));
}

FileExportPlugin::FileExportPlugin(HWND window) : window_(window) {}

void FileExportPlugin::HandleMethodCall(
    const flutter::MethodCall<flutter::EncodableValue> &call,
    std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result) {
  if (call.method_name() != "export") {
    result->NotImplemented();
    return;
  }
  auto request = Decode(call.arguments());
  if (!request) {
    result->Error("invalidRequest", "Export request is invalid");
    return;
  }
  auto reply = std::shared_ptr<flutter::MethodResult<flutter::EncodableValue>>(
      std::move(result));
  exporter_.Export(std::move(*request), window_, [reply](ExportResult outcome) {
    reply->Success(Encode(outcome));
  });
}

} // namespace file_export
