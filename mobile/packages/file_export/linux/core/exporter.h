#ifndef FILE_EXPORT_CORE_EXPORTER_H_
#define FILE_EXPORT_CORE_EXPORTER_H_

#include <gtk/gtk.h>

#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <thread>
#include <variant>
#include <vector>

namespace file_export {

using ExportSource = std::variant<std::vector<uint8_t>, std::string>;

struct ExportRequest {
  std::string file_name;
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

class Exporter : public std::enable_shared_from_this<Exporter> {
public:
  using Completion = std::function<void(ExportResult)>;

  Exporter() = default;
  ~Exporter();

  Exporter(const Exporter &) = delete;
  Exporter &operator=(const Exporter &) = delete;

  void Export(ExportRequest request, GtkWindow *parent, Completion completion);
  void Close();

private:
  void HandleDialogResponse(int response);
  void StartWrite(ExportRequest request, std::string destination_uri,
                  std::string location);
  void Finish(ExportResult result);
  void ReleaseDialog(bool disconnect);

  Completion completion_;
  GtkFileChooserNative *dialog_ = nullptr;
  gulong response_handler_ = 0;
  GCancellable *cancellable_ = nullptr;
  std::thread worker_;
  bool closed_ = false;
};

} // namespace file_export

#endif
