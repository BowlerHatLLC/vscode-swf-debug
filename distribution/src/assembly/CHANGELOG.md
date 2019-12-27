# SWF Debugger for Visual Studio Code Changelog

## v1.0.2

### Fixed Issues

- Linux: Fixed broken detection of _flashplayerdebugger_ executable location.
- Adobe AIR: Fixed issue where a custom `runtimeExecutable` in _launch.json_ would be ignored in some cases.
- Adobe AIR: Fixed issue where debugger might try to reference default paths to Adobe AIR executables, even if the SDK did not contain them.
- Adobe AIR: Fixed issue where detecting iOS devices failed if device handle were three digits instead of two.
- General: If a command like step in/out/over times out, prints a simpler message that no longer contains a stack trace to avoid looking like a crash.

## v1.0.1

### Fixed Issues

- Watch: Fixed issue where adding any watch expression failed with an error that made the debugger unresponsive.

## v1.0.0

### New Features

- Haxe: In addition to ActionScript and MXML projects, the extension now supports Haxe projects that target Adobe AIR or Flash Player. You can now add breakpoints in _.hx_ files.
- General: No longer requires a workspace containing an _asconfig.json_ file to debug SWFs. Missing fields in _launch.json_ will still be automatically populated, if _asconfig.json_ exists. However, if the file doesn't exist, all fields can be set manually, and helpful error messages will indicate if a field is missing or invalid.
- Attach: When specifying `platform` to install an Adobe AIR app on a mobile device, you may also specify its `applicationID` to uninstall any old versions, and the path to an _.apk_ or _.ipa_ `bundle` to install. These two new fields are automatically populated from _asconfig.json_, but may be specified manually for Haxe projects.

### Fixed Issues

- Attach: Fixed issue where port forwarding was not correctly cleaned up when finished debugging an Adobe AIR app on an Android device.

### Other Changes

- Initial release as a separate extension from [vscode-as3mxml](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas).
