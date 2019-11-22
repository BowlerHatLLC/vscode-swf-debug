# SWF Debugger for Visual Studio Code Changelog

## v1.0.0

### New Features

- Haxe: In addition to ActionScript and MXML projects, the extension now supports Haxe projects that target Adobe AIR or Flash Player. You can now add breakpoints in _.hx_ files.
- General: No longer requires a workspace containing an _asconfig.json_ file to debug SWFs. Missing fields in _launch.json_ will still be automatically populated, if _asconfig.json_ exists. However, if the file doesn't exist, all fields can be set manually, and helpful error messages will indicate if a field is missing or invalid.
- Attach: When specifying `platform` to install an Adobe AIR app on a mobile device, you may also specify its `applicationID` to uninstall any old versions, and the path to an _.apk_ or _.ipa_ `bundle` to install. These two new fields are automatically populated from _asconfig.json_, but may be specified manually for Haxe projects.

### Fixed Issues

- Attach: Fixed issue where port forwarding was not correctly cleaned up when finished debugging an Adobe AIR app on an Android device.

### Other Changes

- Initial release as a separate extension from [vscode-as3mxml](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas).
