#include "include/file_export/file_export_plugin.h"

#include <flutter_linux/flutter_linux.h>
#include <gtk/gtk.h>

#include <cstring>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include "core/exporter.h"

#define FILE_EXPORT_PLUGIN(obj)                                                \
  (G_TYPE_CHECK_INSTANCE_CAST((obj), file_export_plugin_get_type(),            \
                              FileExportPlugin))

struct _FileExportPlugin {
  GObject parent_instance;
  std::shared_ptr<file_export::Exporter> *exporter;
  GtkWindow *window;
};

G_DEFINE_TYPE(FileExportPlugin, file_export_plugin, g_object_get_type())

namespace {

constexpr char kChannel[] = "io.ente.file_export";

FlValue *Lookup(FlValue *map, const char *key) {
  return map != nullptr && fl_value_get_type(map) == FL_VALUE_TYPE_MAP
             ? fl_value_lookup_string(map, key)
             : nullptr;
}

const char *String(FlValue *value) {
  return value != nullptr && fl_value_get_type(value) == FL_VALUE_TYPE_STRING
             ? fl_value_get_string(value)
             : nullptr;
}

std::optional<file_export::ExportRequest> Decode(FlValue *arguments) {
  const char *file_name = String(Lookup(arguments, "fileName"));
  const char *mime_type = String(Lookup(arguments, "mimeType"));
  FlValue *source = Lookup(arguments, "source");
  const char *type = String(Lookup(source, "type"));
  if (file_name == nullptr || mime_type == nullptr || type == nullptr) {
    return std::nullopt;
  }

  file_export::ExportSource export_source;
  if (strcmp(type, "bytes") == 0) {
    FlValue *bytes = Lookup(source, "bytes");
    if (bytes == nullptr ||
        fl_value_get_type(bytes) != FL_VALUE_TYPE_UINT8_LIST) {
      return std::nullopt;
    }
    const size_t length = fl_value_get_length(bytes);
    const uint8_t *data = fl_value_get_uint8_list(bytes);
    if (length == 0) {
      export_source = std::vector<uint8_t>();
    } else if (data == nullptr) {
      return std::nullopt;
    } else {
      export_source = std::vector<uint8_t>(data, data + length);
    }
  } else if (strcmp(type, "file") == 0) {
    const char *path = String(Lookup(source, "path"));
    if (path == nullptr || !g_path_is_absolute(path))
      return std::nullopt;
    export_source = std::string(path);
  } else {
    return std::nullopt;
  }
  file_export::ExportRequest request{file_name, mime_type,
                                     std::move(export_source)};
  return request.IsValid()
             ? std::optional<file_export::ExportRequest>(std::move(request))
             : std::nullopt;
}

const char *FailureName(file_export::ExportFailure failure) {
  switch (failure) {
  case file_export::ExportFailure::kBusy:
    return "busy";
  case file_export::ExportFailure::kSourceMissing:
    return "sourceMissing";
  case file_export::ExportFailure::kSourceUnreadable:
    return "sourceUnreadable";
  case file_export::ExportFailure::kPresentationFailed:
    return "presentationFailed";
  case file_export::ExportFailure::kWriteFailed:
    return "writeFailed";
  }
  return "writeFailed";
}

FlValue *Encode(const file_export::ExportResult &result) {
  FlValue *value = fl_value_new_map();
  switch (result.status) {
  case file_export::ExportResult::Status::kExported:
    fl_value_set_string_take(value, "status", fl_value_new_string("exported"));
    fl_value_set_string_take(value, "location",
                             fl_value_new_string(result.location.c_str()));
    break;
  case file_export::ExportResult::Status::kCancelled:
    fl_value_set_string_take(value, "status", fl_value_new_string("cancelled"));
    break;
  case file_export::ExportResult::Status::kFailed:
    fl_value_set_string_take(value, "status", fl_value_new_string("failed"));
    fl_value_set_string_take(value, "reason",
                             fl_value_new_string(FailureName(result.failure)));
    if (!result.message.empty()) {
      fl_value_set_string_take(value, "message",
                               fl_value_new_string(result.message.c_str()));
    }
    break;
  }
  return value;
}

void HandleMethodCall(FileExportPlugin *self, FlMethodCall *call) {
  if (strcmp(fl_method_call_get_name(call), "export") != 0) {
    fl_method_call_respond_not_implemented(call, nullptr);
    return;
  }
  auto request = Decode(fl_method_call_get_args(call));
  if (!request) {
    fl_method_call_respond_error(call, "invalidRequest",
                                 "Export request is invalid", nullptr, nullptr);
    return;
  }

  FlMethodCall *pending = FL_METHOD_CALL(g_object_ref(call));
  (*self->exporter)
      ->Export(std::move(*request), self->window,
               [pending](file_export::ExportResult result) {
                 g_autoptr(FlValue) value = Encode(result);
                 fl_method_call_respond_success(pending, value, nullptr);
                 g_object_unref(pending);
               });
}

void MethodCallCallback(FlMethodChannel *, FlMethodCall *call,
                        gpointer user_data) {
  HandleMethodCall(FILE_EXPORT_PLUGIN(user_data), call);
}

} // namespace

static void file_export_plugin_dispose(GObject *object) {
  FileExportPlugin *self = FILE_EXPORT_PLUGIN(object);
  if (self->exporter != nullptr) {
    (*self->exporter)->Close();
    delete self->exporter;
    self->exporter = nullptr;
  }
  G_OBJECT_CLASS(file_export_plugin_parent_class)->dispose(object);
}

static void file_export_plugin_class_init(FileExportPluginClass *klass) {
  G_OBJECT_CLASS(klass)->dispose = file_export_plugin_dispose;
}

static void file_export_plugin_init(FileExportPlugin *self) {
  self->exporter = new std::shared_ptr<file_export::Exporter>(
      std::make_shared<file_export::Exporter>());
  self->window = nullptr;
}

void file_export_plugin_register_with_registrar(FlPluginRegistrar *registrar) {
  FileExportPlugin *plugin =
      FILE_EXPORT_PLUGIN(g_object_new(file_export_plugin_get_type(), nullptr));
  FlView *view = fl_plugin_registrar_get_view(registrar);
  if (view != nullptr) {
    GtkWidget *toplevel = gtk_widget_get_toplevel(GTK_WIDGET(view));
    if (GTK_IS_WINDOW(toplevel))
      plugin->window = GTK_WINDOW(toplevel);
  }

  g_autoptr(FlStandardMethodCodec) codec = fl_standard_method_codec_new();
  g_autoptr(FlMethodChannel) channel =
      fl_method_channel_new(fl_plugin_registrar_get_messenger(registrar),
                            kChannel, FL_METHOD_CODEC(codec));
  fl_method_channel_set_method_call_handler(
      channel, MethodCallCallback, g_object_ref(plugin), g_object_unref);
  g_object_unref(plugin);
}
