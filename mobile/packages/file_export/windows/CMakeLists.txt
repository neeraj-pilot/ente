cmake_minimum_required(VERSION 3.14)

set(PROJECT_NAME "file_export")
project(${PROJECT_NAME} LANGUAGES CXX)
cmake_policy(VERSION 3.14...3.25)

set(PLUGIN_NAME "file_export_plugin")

add_library(${PLUGIN_NAME} SHARED
  "include/file_export/file_export_plugin_c_api.h"
  "core/exporter.cpp"
  "core/exporter.h"
  "file_export_plugin_c_api.cpp"
  "file_export_plugin.cpp"
  "file_export_plugin.h"
)

apply_standard_settings(${PLUGIN_NAME})
set_target_properties(${PLUGIN_NAME} PROPERTIES CXX_VISIBILITY_PRESET hidden)
target_compile_definitions(${PLUGIN_NAME} PRIVATE FLUTTER_PLUGIN_IMPL)
target_include_directories(${PLUGIN_NAME} INTERFACE "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_link_libraries(${PLUGIN_NAME} PRIVATE flutter flutter_wrapper_plugin ole32 shell32)

set(file_export_bundled_libraries "" PARENT_SCOPE)
