#include "file_export_plugin.h"

#include <flutter/standard_method_codec.h>

#include <mutex>
#include <optional>
#include <unordered_map>
#include <utility>

namespace file_export {

struct PendingReply {
  std::shared_ptr<flutter::MethodResult<flutter::EncodableValue>> reply;
  ExportResult outcome;
};

class ReplyDispatcher {
public:
  explicit ReplyDispatcher(HWND window) : window_(window) {}

  void Post(
      std::shared_ptr<flutter::MethodResult<flutter::EncodableValue>> reply,
      ExportResult outcome) {
    auto pending = std::make_unique<PendingReply>(
        PendingReply{std::move(reply), std::move(outcome)});
    auto *pointer = pending.get();
    {
      std::lock_guard<std::mutex> lock(mutex_);
      if (closed_)
        return;
      pending_.emplace(pointer, std::move(pending));
    }
    if (window_ == nullptr || !PostMessage(window_, ExportCompletedMessage(), 0,
                                           reinterpret_cast<LPARAM>(pointer))) {
      std::lock_guard<std::mutex> lock(mutex_);
      pending_.erase(pointer);
    }
  }

  std::unique_ptr<PendingReply> Take(PendingReply *pointer) {
    std::lock_guard<std::mutex> lock(mutex_);
    const auto item = pending_.find(pointer);
    if (item == pending_.end())
      return nullptr;
    auto pending = std::move(item->second);
    pending_.erase(item);
    return pending;
  }

  void Close() {
    std::lock_guard<std::mutex> lock(mutex_);
    closed_ = true;
    pending_.clear();
  }

  static UINT ExportCompletedMessage() {
    static const UINT message =
        RegisterWindowMessageW(L"io.ente.file_export.completed");
    return message;
  }

private:
  HWND window_;
  std::mutex mutex_;
  bool closed_ = false;
  std::unordered_map<PendingReply *, std::unique_ptr<PendingReply>> pending_;
};

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
      registrar, view == nullptr ? nullptr : view->GetNativeWindow());
  channel->SetMethodCallHandler(
      [plugin_pointer = plugin.get()](const auto &call, auto result) {
        plugin_pointer->HandleMethodCall(call, std::move(result));
      });
  registrar->AddPlugin(std::move(plugin));
}

FileExportPlugin::FileExportPlugin(flutter::PluginRegistrarWindows *registrar,
                                   HWND window)
    : registrar_(registrar), window_(window),
      replies_(std::make_shared<ReplyDispatcher>(window)) {
  window_proc_delegate_ = registrar_->RegisterTopLevelWindowProcDelegate(
      [this](HWND, UINT message, WPARAM, LPARAM parameter) {
        return HandleWindowMessage(message, parameter);
      });
}

FileExportPlugin::~FileExportPlugin() {
  replies_->Close();
  registrar_->UnregisterTopLevelWindowProcDelegate(window_proc_delegate_);
}

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
  exporter_.Export(
      std::move(*request), window_,
      [dispatcher = replies_, reply](ExportResult outcome) mutable {
        dispatcher->Post(std::move(reply), std::move(outcome));
      });
}

std::optional<LRESULT> FileExportPlugin::HandleWindowMessage(UINT message,
                                                             LPARAM parameter) {
  if (message != ReplyDispatcher::ExportCompletedMessage())
    return std::nullopt;
  auto pending = replies_->Take(reinterpret_cast<PendingReply *>(parameter));
  if (!pending)
    return std::nullopt;
  pending->reply->Success(Encode(pending->outcome));
  return 0;
}

} // namespace file_export
