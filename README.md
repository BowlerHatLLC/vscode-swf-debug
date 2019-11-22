# SWF Debugger for Visual Studio Code

This README file is intended for contributors to the extension. If you simply want to install the latest stable version of the extension, please visit the [Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-swf-debug). For help using the extension, visit the [vscode-as3mxml wiki](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki) for detailed instructions.

## Modules

This project is divided into several modules.

1. **swf-debug-adapter** provides SWF debugging for Visual Studio Code and other editors that support the [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/). This module is written in Java.

1. **vscode-extension** implements various features that are specific to Visual Studio Code, and initializes the various Java processes (like swf-debugger). This module is written in TypeScript.

1. **distribution** packages everything together to create the final extension that is compatible with Visual Studio Code.

## Build instructions

Requires [Apache Maven](https://maven.apache.org/) and [Node.js](https://nodejs.org/). Run the following command in the root directory to build the extension:

```
mvn clean package -s settings-template.xml
```

The extension will be generated in _distribution/target/vscode-swf-debug/vscode-swf-debug_. This directory may be run inside Visual Studio Code's extension host. Additionally, a _.vsix_ file will be generated that may be manually installed in Visual Studio Code.

## Support this project

The [SWF Debugger for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-swf-debug) is developed by [Josh Tynjala](http://patreon.com/josht) with the support of community members like you.

[Support Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

- [Moonshine IDE](http://moonshine-ide.com/)
- [Dedoose](https://www.dedoose.com/)
