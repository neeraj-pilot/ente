#ifndef FLUTTER_PLUGIN_FILE_EXPORT_PLUGIN_H_
#define FLUTTER_PLUGIN_FILE_EXPORT_PLUGIN_H_

#include <flutter_linux/flutter_linux.h>

G_BEGIN_DECLS

#ifdef FLUTTER_PLUGIN_IMPL
#define FLUTTER_PLUGIN_EXPORT __attribute__((visibility("default")))
#else
#define FLUTTER_PLUGIN_EXPORT
#endif

typedef struct _FileExportPlugin FileExportPlugin;
typedef struct {
  GObjectClass parent_class;
} FileExportPluginClass;

FLUTTER_PLUGIN_EXPORT GType file_export_plugin_get_type();
FLUTTER_PLUGIN_EXPORT void
file_export_plugin_register_with_registrar(FlPluginRegistrar *registrar);

G_END_DECLS

#endif
