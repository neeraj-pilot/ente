#include "exporter.h"

#include <gio/gio.h>

#include <algorithm>
#include <cstring>
#include <utility>

namespace file_export {
namespace {

bool IsFileName(const std::string &value) {
  if (value.empty() || value == "." || value == ".." ||
      !g_utf8_validate(value.c_str(), value.size(), nullptr)) {
    return false;
  }
  bool has_non_whitespace = false;
  for (const char *cursor = value.c_str(); *cursor != '\0';
       cursor = g_utf8_next_char(cursor)) {
    const gunichar character = g_utf8_get_char(cursor);
    if (character < 32 ||
        (character < 128 && strchr("\\/:*?\"<>|", character) != nullptr)) {
      return false;
    }
    has_non_whitespace |= !g_unichar_isspace(character);
  }
  return has_non_whitespace;
}

bool IsMimeType(const std::string &value) {
  const auto slash = value.find('/');
  if (slash == std::string::npos || slash == 0 || slash + 1 == value.size() ||
      value.find('/', slash + 1) != std::string::npos) {
    return false;
  }
  return std::all_of(value.begin(), value.end(), [](unsigned char character) {
    return character == '/' || g_ascii_isalnum(character) ||
           strchr("!#$&^_.+-", character) != nullptr;
  });
}

std::optional<ExportFailure> SourceFailure(const ExportSource &source) {
  const auto *path = std::get_if<std::string>(&source);
  if (path == nullptr)
    return std::nullopt;

  g_autoptr(GFile) file = g_file_new_for_path(path->c_str());
  g_autoptr(GError) error = nullptr;
  g_autoptr(GFileInfo) info = g_file_query_info(
      file, G_FILE_ATTRIBUTE_STANDARD_TYPE "," G_FILE_ATTRIBUTE_ACCESS_CAN_READ,
      G_FILE_QUERY_INFO_NONE, nullptr, &error);
  if (info == nullptr) {
    return error != nullptr && error->domain == G_IO_ERROR &&
                   error->code == G_IO_ERROR_NOT_FOUND
               ? ExportFailure::kSourceMissing
               : ExportFailure::kSourceUnreadable;
  }
  return g_file_info_get_file_type(info) == G_FILE_TYPE_REGULAR &&
                 g_file_info_get_attribute_boolean(
                     info, G_FILE_ATTRIBUTE_ACCESS_CAN_READ)
             ? std::nullopt
             : std::optional(ExportFailure::kSourceUnreadable);
}

ExportResult Write(const ExportRequest &request,
                   const std::string &destination_uri,
                   const std::string &location, GCancellable *cancellable) {
  if (auto failure = SourceFailure(request.source)) {
    return ExportResult::Failed(*failure);
  }
  g_autoptr(GFile) destination_file =
      g_file_new_for_uri(destination_uri.c_str());
  if (const auto *source_path = std::get_if<std::string>(&request.source)) {
    g_autoptr(GFile) source_file = g_file_new_for_path(source_path->c_str());
    if (g_file_equal(source_file, destination_file)) {
      return ExportResult::Exported(location);
    }
  }

  g_autoptr(GError) error = nullptr;
  g_autoptr(GFileInputStream) input = nullptr;
  if (const auto *source_path = std::get_if<std::string>(&request.source)) {
    g_autoptr(GFile) source_file = g_file_new_for_path(source_path->c_str());
    input = g_file_read(source_file, cancellable, &error);
    if (input == nullptr) {
      auto failure = SourceFailure(request.source);
      return ExportResult::Failed(
          failure.value_or(ExportFailure::kSourceUnreadable),
          error != nullptr ? error->message : "");
    }
  }

  g_autoptr(GFileOutputStream) output =
      g_file_replace(destination_file, nullptr, FALSE, G_FILE_CREATE_PRIVATE,
                     cancellable, &error);
  if (output == nullptr) {
    return ExportResult::Failed(ExportFailure::kWriteFailed,
                                error != nullptr ? error->message : "");
  }

  bool wrote = false;
  if (const auto *bytes = std::get_if<std::vector<uint8_t>>(&request.source)) {
    gsize written = 0;
    wrote = bytes->empty() ||
            (g_output_stream_write_all(G_OUTPUT_STREAM(output), bytes->data(),
                                       bytes->size(), &written, cancellable,
                                       &error) &&
             written == bytes->size());
  } else {
    wrote = g_output_stream_splice(
                G_OUTPUT_STREAM(output), G_INPUT_STREAM(input),
                G_OUTPUT_STREAM_SPLICE_CLOSE_SOURCE, cancellable, &error) >= 0;
  }
  const bool success = wrote && g_output_stream_close(G_OUTPUT_STREAM(output),
                                                      cancellable, &error);
  if (!wrote) {
    g_cancellable_cancel(cancellable);
    g_output_stream_close(G_OUTPUT_STREAM(output), cancellable, nullptr);
  }
  if (!success) {
    auto failure = SourceFailure(request.source);
    return ExportResult::Failed(failure.value_or(ExportFailure::kWriteFailed),
                                error != nullptr ? error->message : "");
  }
  return ExportResult::Exported(location);
}

struct CompletedWrite {
  std::shared_ptr<Exporter> exporter;
  ExportResult result;
};

} // namespace

bool ExportRequest::IsValid() const {
  const auto *path = std::get_if<std::string>(&source);
  return IsFileName(file_name) && IsMimeType(mime_type) &&
         (path == nullptr || g_path_is_absolute(path->c_str()));
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
  ReleaseDialog(true);
  if (cancellable_ != nullptr)
    g_object_unref(cancellable_);
  if (worker_.joinable())
    worker_.join();
}

void Exporter::Export(ExportRequest request, GtkWindow *parent,
                      Completion completion) {
  if (closed_) {
    completion(ExportResult::Failed(ExportFailure::kPresentationFailed));
    return;
  }
  if (completion_) {
    completion(ExportResult::Failed(ExportFailure::kBusy));
    return;
  }
  if (!request.IsValid()) {
    completion(ExportResult::Failed(ExportFailure::kPresentationFailed,
                                    "Invalid export request"));
    return;
  }
  if (auto failure = SourceFailure(request.source)) {
    completion(ExportResult::Failed(*failure));
    return;
  }

  completion_ = std::move(completion);
  dialog_ = gtk_file_chooser_native_new(
      nullptr, parent, GTK_FILE_CHOOSER_ACTION_SAVE, nullptr, nullptr);
  GtkFileChooser *chooser = GTK_FILE_CHOOSER(dialog_);
  gtk_file_chooser_set_current_name(chooser, request.file_name.c_str());
  gtk_file_chooser_set_do_overwrite_confirmation(chooser, TRUE);
  auto *shared = new std::shared_ptr<Exporter>(shared_from_this());
  response_handler_ = g_signal_connect_data(
      dialog_, "response",
      G_CALLBACK(+[](GtkNativeDialog *, int response, gpointer data) {
        auto exporter = *static_cast<std::shared_ptr<Exporter> *>(data);
        exporter->HandleDialogResponse(response);
      }),
      shared,
      +[](gpointer data, GClosure *) {
        delete static_cast<std::shared_ptr<Exporter> *>(data);
      },
      static_cast<GConnectFlags>(0));
  g_object_set_data_full(
      G_OBJECT(dialog_), "file-export-request",
      new ExportRequest(std::move(request)),
      [](gpointer data) { delete static_cast<ExportRequest *>(data); });
  gtk_native_dialog_show(GTK_NATIVE_DIALOG(dialog_));
}

void Exporter::Close() {
  if (closed_)
    return;
  closed_ = true;
  if (cancellable_ != nullptr) {
    g_cancellable_cancel(cancellable_);
    return;
  }
  if (completion_) {
    ReleaseDialog(true);
    Finish(ExportResult::Failed(ExportFailure::kPresentationFailed));
  }
}

void Exporter::HandleDialogResponse(int response) {
  if (dialog_ == nullptr)
    return;
  auto *request = static_cast<ExportRequest *>(
      g_object_get_data(G_OBJECT(dialog_), "file-export-request"));
  if (response != GTK_RESPONSE_ACCEPT) {
    ReleaseDialog(false);
    Finish(ExportResult::Cancelled());
    return;
  }
  g_autofree gchar *destination_uri =
      gtk_file_chooser_get_uri(GTK_FILE_CHOOSER(dialog_));
  if (request == nullptr || destination_uri == nullptr) {
    ReleaseDialog(false);
    Finish(ExportResult::Failed(ExportFailure::kPresentationFailed));
    return;
  }
  g_autoptr(GFile) destination_file = g_file_new_for_uri(destination_uri);
  g_autofree gchar *destination_path = g_file_get_path(destination_file);
  ExportRequest owned_request = std::move(*request);
  std::string owned_destination_uri(destination_uri);
  std::string location(destination_path != nullptr ? destination_path
                                                   : destination_uri);
  ReleaseDialog(false);
  StartWrite(std::move(owned_request), std::move(owned_destination_uri),
             std::move(location));
}

void Exporter::StartWrite(ExportRequest request, std::string destination_uri,
                          std::string location) {
  if (worker_.joinable())
    worker_.join();
  cancellable_ = g_cancellable_new();
  GCancellable *cancellable = G_CANCELLABLE(g_object_ref(cancellable_));
  auto self = shared_from_this();
  worker_ =
      std::thread([self = std::move(self), request = std::move(request),
                   destination_uri = std::move(destination_uri),
                   location = std::move(location), cancellable]() mutable {
        ExportResult result =
            Write(request, destination_uri, location, cancellable);
        g_object_unref(cancellable);
        g_main_context_invoke(
            nullptr,
            +[](gpointer data) -> gboolean {
              std::unique_ptr<CompletedWrite> completed(
                  static_cast<CompletedWrite *>(data));
              completed->exporter->Finish(std::move(completed->result));
              return G_SOURCE_REMOVE;
            },
            new CompletedWrite{std::move(self), std::move(result)});
      });
}

void Exporter::Finish(ExportResult result) {
  if (worker_.joinable())
    worker_.join();
  if (cancellable_ != nullptr) {
    g_object_unref(cancellable_);
    cancellable_ = nullptr;
  }
  auto completion = std::move(completion_);
  completion_ = nullptr;
  if (completion)
    completion(std::move(result));
}

void Exporter::ReleaseDialog(bool disconnect) {
  if (dialog_ == nullptr)
    return;
  if (disconnect && response_handler_ != 0) {
    g_signal_handler_disconnect(dialog_, response_handler_);
  }
  gtk_native_dialog_hide(GTK_NATIVE_DIALOG(dialog_));
  g_object_unref(dialog_);
  dialog_ = nullptr;
  response_handler_ = 0;
}

} // namespace file_export
