#ifndef FILE_EXPORT_CORE_EXPORTER_H_
#define FILE_EXPORT_CORE_EXPORTER_H_

#include <windows.h>

#include <cstdint>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <variant>
#include <vector>

namespace file_export {

using ExportSource = std::variant<std::vector<uint8_t>, std::wstring>;

struct ExportRequest {
  std::wstring file_name;
  std::string mime_type;
  ExportSource source;

  bool IsValid() const;
};

enum class ExportFailure {
  kBusy,
  kSourceMissing,
  kSourceUnreadable,
  kPresentationFailed,
  kWriteFailed,
};

struct ExportResult {
  enum class Status { kExported, kCancelled, kFailed };

  Status status;
  std::string location;
  ExportFailure failure = ExportFailure::kWriteFailed;
  std::string message;

  static ExportResult Exported(std::string location);
  static ExportResult Cancelled();
  static ExportResult Failed(ExportFailure failure, std::string message = {});
};

class Exporter {
public:
  using Completion = std::function<void(ExportResult)>;

  Exporter() = default;
  ~Exporter();

  Exporter(const Exporter &) = delete;
  Exporter &operator=(const Exporter &) = delete;

  void Export(ExportRequest request, HWND owner, Completion completion);

private:
  std::mutex mutex_;
  bool busy_ = false;
  std::thread worker_;
};

} // namespace file_export

#endif
