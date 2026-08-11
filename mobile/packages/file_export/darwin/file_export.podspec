Pod::Spec.new do |s|
  s.name             = 'file_export'
  s.version          = '0.0.1'
  s.summary          = 'Ente-owned system file export'
  s.homepage         = 'https://github.com/ente-io/ente'
  s.license          = { :type => 'AGPL-3.0-only' }
  s.author           = { 'Ente' => 'support@ente.io' }
  s.source           = { :path => '.' }
  s.source_files     = 'file_export/Sources/file_export/**/*.swift'
  s.ios.dependency 'Flutter'
  s.osx.dependency 'FlutterMacOS'
  s.ios.deployment_target = '13.0'
  s.osx.deployment_target = '10.15'
  s.swift_version = '5.0'
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386'
  }
end
