# SWF Debugger for Visual Studio Code Changelog

## v1.10.0

### New Features

- General: Automatically uses **adl64** executable, if it exists, and `<architecture>64</architecture>` is detected in the Adobe AIR application descriptor.

### Fixed Issues

- Workers: Fixed issue where a worker terminating would sometimes be incorrectly detected as the main SWF terminating instead.

### Other Changes

- Extension: Refactored all TypeScript code to use `strict` mode.

## v1.9.0

### Fixed Issues

- Breakpoints: Fixed adding breakpoints to `<fx:Script>` inside `<fx:Component>`.
- Launch: Fixed resolution of `asconfigPath` field in multi-root workspaces where launch configuration is defined in the _.code-workspace_ file.

### Other Changes

- Dependencies: Apache Royale debugger updated to v0.9.12.
- General: Requires JDK 11 or newer due to new Royale minimum requirements. Previously JDK 8 or newer was required.

## v1.8.3

### Fixed Issues

- General: Fixed resolution of other paths from `asconfigPath` when _asconfig.json_ is not at the root of the workspace.

## v1.8.2

### Fixed Issues

- Evaluate: Catch more parsing exceptions when evaluating AS3 code in debugger.
- General: Redirected stdout in dependencies to stderr when using stdout/stdin for debug adapter protocol so that it doesn't send invalid protocol messages.

## v1.8.1

### Fixed Issues

- Launch: Fixed null pointer exception when `runtimeArgs` is null.

## v1.8.0

### New Features

- Launch: Can now specify `env` field in _launch.json_ to set a map of environment variables.
- Breakpoints: Hovering over a member variable or property in the editor while stopped at a breakpoint will show the value in a tool-tip.

### Fixed Issues

- Launch: Fixed `runtimeArgs` in _launch.json_ being ignored if `runtimeExecutable` is not specified.
- Launch: Fixed detection of Adobe AIR application descriptor output path when the file name is generated from the main class.

### Other Changes

- Dependencies: Apache Royale debugger updated to v0.9.10.

## v1.7.1

### Fixed Issues

- Breakpoints: Fixed detection of SDK _.as_ and _.mxml_ source files in debugger on macOS and Linux when the SDK was built on Windows.

## v1.7.0

### New Features

- Breakpoints: Added support for conditional breakpoints where an AS3 expression is evaluated to determine if the debugger should stop.
- Evaluate: Enhanced to support accessing variables from outer scopes inside closures.
- Evaluate: Enhanced to support members of `this` without requiring `this.` at the beginning.
- Variables: When editing numeric values, now supports hexadecimal integer formatting, which starts with `0x` and contains the characters `0-9` and `A-F`.

### Fixed Issues

- Launch: Fixed failed detection of Adobe AIR application IDs containing the `-` character.
- Variables: Fixed setting numeric variable with more than one digit after decimal point.
- Variables: Fixed setting numeric variable starting with a `-` character as a negative value.

## v1.6.1

### Other Changes

- Documentation improvements

## v1.6.0

### Fixed Issues

- Workers: Fixed intermittent issue where worker would remain in a paused state indefinitely after being started.

## v1.5.0

### New Features

- Variables: Array members are now displayed as indexed, in the correct order.
- Launch: Added new `asconfigPath` field to the _launch.json_ configuration options to allow the use of a custom file path instead of the default _asconfig.json_ .

### Fixed Issues

- Workers: Fix exception when attempting to resume a newly created worker because sometimes they are not started in a suspended state.

## v1.4.0

### Fixed Issues

- Launch: Fixed exceptions when trying to run launch configurations defined in _.code-workspace_ file instead of _launch.json_.

### Other Changes

- General: Switched references from vscode-nextgenas to vscode-as3mxml following extension ID change.

## v1.3.0

### New Features

- Launch: Install and launch an Adobe AIR app on the iOS Simulator in Xcode. Set the `platform` field in _launch.json_ to `"ios_simulator"`.
- Launch: Automatically provides debug configurations based on the contents your project's _asconfig.json_ file. Debugging is now possible without a _launch.json_ file.

### Fixed Issues

- Breakpoints: Fixed intermittent null reference exception when setting breakpoints.

## v1.2.2

### Fixed Issues

- General: Fix `NotConnectedException` on macOS due to bug in wrong SWF ID returned by runtime.
- General: Fix issue where the SWF unload was not handled correctly if it happened due to an exception before SWF load.

## v1.2.1

### Fixed Issues

- Breakpoints: Fixed issue where adding breakpoints to a SWF with frame scripts added in Adobe Animate could result in an InvalidPathException.

## v1.2.0

### New Features

- Workers: Pause, resume, step into, step over, and step out for workers.
- Stack: When worker stops on a breakpoint or exception, display the worker's stack trace.
- Variables: When a worker stops on a breakpoint or exception, list the variables in the current scope.

### Fixed Issues

- Breakpoints: Fixed issue where breakpoints added in document class constructor were ignored.
- General: Fixed issue where an error response to a request that fails incorrectly displayed a token instead of the real text.

## v1.1.2

### Fixed Issues

- Breakpoints: Fixed issue where a "no response" exception was thrown sometimes when setting breakpoints before the SWF finished loading.

## v1.1.1

### Fixed Issues

- Launch: Fixed issue where `mainClass` in _asconfig.json_ file was not correctly resolved.
- Launch: Fixed issue where no failure message was reported on exit when running without debug.

### Other Changes

- Launch: When _asconfig.json_ includes different Adobe AIR application descriptors for "ios" and "android", but the `versionPlatform` is not specified in _launch.json_, tries to use "ios" first and then "android" instead of failing.
- Launch: When _asconfig.json_ includes multiple Adobe AIR application descriptors for desktop platforms ("windows" and "mac"), uses the one that matches the current platform.
- Miscellaneous: Improved some exception handling related to runtime exitting.

## v1.1.0

### New Features

- Evaluate: Added support for assigning values from debug console.
- Threads: Updates list of workers when one is started or stopped.
- Variables: Added support for modifying string, number, and boolean values in the variables view when paused at a breakpoint.

### Fixed Issues

- Evaluate: Fixed exception when expression submitted through console when paused. Now displays a more appropriate error message.
- Step In/Out/Over: Improved exception handling when activated too quickly and runtime is not paused.
- Variables: Fixed "Invalid variable attributes" message in variables list.
- Workers: Fixed infinite loop when a worker is paused on exception or suspended for any other reason.

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
