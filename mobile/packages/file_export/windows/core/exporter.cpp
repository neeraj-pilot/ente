#include "exporter.h"

#include <shobjidl.h>
#include <wrl/client.h>

#include <algorithm>
#include <cwctype>
#include <filesystem>
#include <optional>
#include <string_view>
#include <utility>

namespace file_export {
namespace {

using Microsoft::WRL::ComPtr;

bool IsFileName(const std::wstring &value) {
  return !value.empty() && value != L"." && value != L".." &&
         std::any_of(
             value.begin(), value.end(),
             [](wchar_t character) { return !std::iswspace(character); }) &&
         std::none_of(value.begin(), value.end(),
                      [](wchar_t character) { return character < 32; }) &&
         value.find_first_of(L"\\/:*?\"<>|") == std::wstring::npos &&
         value.find(L'\0') == std::wstring::npos;
}

bool IsMimeType(const std::string &value) {
  const auto slash = value.find('/');
  if (slash == std::string::npos || slash == 0 || slash + 1 == value.size() ||
      value.find('/', slash + 1) != std::string::npos) {
    return false;
  }
  return std::all_of(value.begin(), value.end(), [](unsigned char character) {
    return character == '/' || (character >= 'A' && character <= 'Z') ||
           (character >= 'a' && character <= 'z') ||
           (character >= '0' && character <= '9') ||
           std::string_view("!#$&^_.+-").find(character) !=
               std::string_view::npos;
  });
}

std::string Utf8(const std::wstring &value) {
  if (value.empty())
    return {};
  const int size = WideCharToMultiByte(CP_UTF8, 0, value.data(),
                                       static_cast<int>(value.size()), nullptr,
                                       0, nullptr, nullptr);
  if (size <= 0)
    return {};
  std::string result(size, '\0');
  WideCharToMultiByte(CP_UTF8, 0, value.data(), static_cast<int>(value.size()),
                      result.data(), size, nullptr, nullptr);
  return result;
}

std::string ErrorMessage(DWORD error) {
  wchar_t *buffer = nullptr;
  const DWORD size = FormatMessageW(
      FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM |
          FORMAT_MESSAGE_IGNORE_INSERTS,
      nullptr, error, 0, reinterpret_cast<wchar_t *>(&buffer), 0, nullptr);
  if (size == 0 || buffer == nullptr)
    return "Windows error " + std::to_string(error);
  std::wstring message(buffer, size);
  LocalFree(buffer);
  while (!message.empty() &&
         (message.back() == L'\r' || message.back() == L'\n')) {
    message.pop_back();
  }
  return Utf8(message);
}

std::wstring ExtendedPath(const std::wstring &path) {
  if (path.rfind(L"\\\\?\\", 0) == 0)
    return path;
  if (path.rfind(L"\\\\", 0) == 0)
    return L"\\\\?\\UNC\\" + path.substr(2);
  return L"\\\\?\\" + path;
}

std::optional<ExportFailure> SourceFailure(const std::wstring &path) {
  const DWORD attributes = GetFileAttributesW(ExtendedPath(path).c_str());
  if (attributes == INVALID_FILE_ATTRIBUTES) {
    const DWORD error = GetLastError();
    return error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND
               ? std::optional(ExportFailure::kSourceMissing)
               : std::optional(ExportFailure::kSourceUnreadable);
  }
  if ((attributes & FILE_ATTRIBUTE_DIRECTORY) != 0) {
    return ExportFailure::kSourceUnreadable;
  }
  HANDLE file =
      CreateFileW(ExtendedPath(path).c_str(), GENERIC_READ, FILE_SHARE_READ,
                  nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
  if (file == INVALID_HANDLE_VALUE)
    return ExportFailure::kSourceUnreadable;
  CloseHandle(file);
  return std::nullopt;
}

std::wstring NewTemporaryPath(const std::wstring &destination) {
  const std::filesystem::path path(destination);
  GUID guid;
  if (FAILED(CoCreateGuid(&guid)))
    return {};
  wchar_t identifier[40] = {};
  StringFromGUID2(guid, identifier, 40);
  return (path.parent_path() /
          (L"." + path.filename().wstring() + identifier + L".tmp"))
      .wstring();
}

bool WriteBytes(const std::vector<uint8_t> &bytes,
                const std::wstring &destination, std::string *message) {
  HANDLE file =
      CreateFileW(ExtendedPath(destination).c_str(), GENERIC_WRITE, 0, nullptr,
                  CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
  if (file == INVALID_HANDLE_VALUE) {
    *message = ErrorMessage(GetLastError());
    return false;
  }
  size_t offset = 0;
  while (offset < bytes.size()) {
    const DWORD count = static_cast<DWORD>(
        std::min<size_t>(bytes.size() - offset, 1024 * 1024));
    DWORD written = 0;
    if (!WriteFile(file, bytes.data() + offset, count, &written, nullptr) ||
        written != count) {
      *message = ErrorMessage(GetLastError());
      CloseHandle(file);
      return false;
    }
    offset += written;
  }
  if (!FlushFileBuffers(file)) {
    *message = ErrorMessage(GetLastError());
    CloseHandle(file);
    return false;
  }
  CloseHandle(file);
  return true;
}

ExportResult Write(const ExportRequest &request,
                   const std::wstring &destination) {
  if (const auto *source = std::get_if<std::wstring>(&request.source)) {
    if (auto failure = SourceFailure(*source)) {
      return ExportResult::Failed(*failure);
    }
    if (_wcsicmp(ExtendedPath(*source).c_str(),
                 ExtendedPath(destination).c_str()) == 0) {
      return ExportResult::Exported(Utf8(destination));
    }
  }

  const std::wstring temporary = NewTemporaryPath(destination);
  if (temporary.empty()) {
    return ExportResult::Failed(ExportFailure::kWriteFailed,
                                "Unable to create a temporary file name");
  }
  const std::wstring extended_temporary = ExtendedPath(temporary);
  std::string message;
  bool wrote = false;
  if (const auto *bytes = std::get_if<std::vector<uint8_t>>(&request.source)) {
    wrote = WriteBytes(*bytes, temporary, &message);
  } else {
    const auto &source = std::get<std::wstring>(request.source);
    wrote = CopyFileW(ExtendedPath(source).c_str(), extended_temporary.c_str(),
                      TRUE) != FALSE;
    if (!wrote)
      message = ErrorMessage(GetLastError());
  }
  if (!wrote) {
    DeleteFileW(extended_temporary.c_str());
    auto failure = ExportFailure::kWriteFailed;
    if (const auto *source = std::get_if<std::wstring>(&request.source)) {
      if (auto source_failure = SourceFailure(*source))
        failure = *source_failure;
    }
    return ExportResult::Failed(failure, message);
  }
  if (!MoveFileExW(extended_temporary.c_str(),
                   ExtendedPath(destination).c_str(),
                   MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
    message = ErrorMessage(GetLastError());
    DeleteFileW(extended_temporary.c_str());
    return ExportResult::Failed(ExportFailure::kWriteFailed, message);
  }
  return ExportResult::Exported(Utf8(destination));
}

std::variant<std::wstring, ExportResult>
ChooseDestination(const ExportRequest &request, HWND owner) {
  ComPtr<IFileSaveDialog> dialog;
  HRESULT result =
      CoCreateInstance(CLSID_FileSaveDialog, nullptr, CLSCTX_INPROC_SERVER,
                       IID_PPV_ARGS(&dialog));
  if (FAILED(result)) {
    return ExportResult::Failed(ExportFailure::kPresentationFailed,
                                ErrorMessage(result));
  }
  DWORD options = 0;
  if (FAILED(dialog->GetOptions(&options)) ||
      FAILED(dialog->SetOptions(options | FOS_FORCEFILESYSTEM |
                                FOS_PATHMUSTEXIST | FOS_OVERWRITEPROMPT)) ||
      FAILED(dialog->SetFileName(request.file_name.c_str()))) {
    return ExportResult::Failed(ExportFailure::kPresentationFailed);
  }

  const auto dot = request.file_name.find_last_of(L'.');
  if (dot != std::wstring::npos && dot + 1 < request.file_name.size()) {
    dialog->SetDefaultExtension(request.file_name.c_str() + dot + 1);
  }
  result = dialog->Show(owner);
  if (result == HRESULT_FROM_WIN32(ERROR_CANCELLED)) {
    return ExportResult::Cancelled();
  }
  if (FAILED(result)) {
    return ExportResult::Failed(ExportFailure::kPresentationFailed,
                                ErrorMessage(result));
  }

  ComPtr<IShellItem> item;
  if (FAILED(dialog->GetResult(&item))) {
    return ExportResult::Failed(ExportFailure::kPresentationFailed);
  }
  wchar_t *path = nullptr;
  if (FAILED(item->GetDisplayName(SIGDN_FILESYSPATH, &path)) ||
      path == nullptr) {
    return ExportResult::Failed(ExportFailure::kPresentationFailed);
  }
  std::wstring destination(path);
  CoTaskMemFree(path);
  return destination;
}

} // namespace

bool ExportRequest::IsValid() const {
  const auto *path = std::get_if<std::wstring>(&source);
  return IsFileName(file_name) && IsMimeType(mime_type) &&
         (path == nullptr || std::filesystem::path(*path).is_absolute());
}

ExportResult ExportResult::Exported(std::string location) {
  return {Status::kExported, std::move(location)};
}

ExportResult ExportResult::Cancelled() { return {Status::kCancelled}; }

ExportResult ExportResult::Failed(ExportFailure failure, std::string message) {
  ExportResult result{Status::kFailed};
  result.failure = failure;
  result.message = std::move(message);
  return result;
}

Exporter::~Exporter() {
  if (worker_.joinable())
    worker_.join();
}

void Exporter::Export(ExportRequest request, HWND owner,
                      Completion completion) {
  bool busy = false;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    busy = busy_;
    if (!busy)
      busy_ = true;
  }
  if (busy) {
    completion(ExportResult::Failed(ExportFailure::kBusy));
    return;
  }
  if (!request.IsValid()) {
    {
      std::lock_guard<std::mutex> lock(mutex_);
      busy_ = false;
    }
    completion(ExportResult::Failed(ExportFailure::kPresentationFailed,
                                    "Invalid export request"));
    return;
  }
  if (worker_.joinable())
    worker_.join();

  if (const auto *source = std::get_if<std::wstring>(&request.source)) {
    if (auto failure = SourceFailure(*source)) {
      {
        std::lock_guard<std::mutex> lock(mutex_);
        busy_ = false;
      }
      completion(ExportResult::Failed(*failure));
      return;
    }
  }
  auto selection = ChooseDestination(request, owner);
  if (const auto *result = std::get_if<ExportResult>(&selection)) {
    {
      std::lock_guard<std::mutex> lock(mutex_);
      busy_ = false;
    }
    completion(*result);
    return;
  }
  std::wstring destination = std::get<std::wstring>(std::move(selection));
  worker_ = std::thread([this, request = std::move(request),
                         destination = std::move(destination),
                         completion = std::move(completion)]() mutable {
    ExportResult result = Write(request, destination);
    {
      std::lock_guard<std::mutex> lock(mutex_);
      busy_ = false;
    }
    completion(std::move(result));
  });
}

} // namespace file_export
