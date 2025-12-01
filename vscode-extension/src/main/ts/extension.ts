/*
Copyright 2016-2025 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import findJava from "./utils/findJava";
import validateJava from "./utils/validateJava";
import SWFDebugConfigurationProvider from "./utils/SWFDebugConfigurationProvider";
import SWFDebugAdapterDescriptorFactory from "./utils/SWFDebugAdapterDescriptorFactory";
import * as vscode from "vscode";

let savedContext: vscode.ExtensionContext | null | undefined = null;

export function activate(context: vscode.ExtensionContext) {
  savedContext = context;

  context.subscriptions.push(
    vscode.debug.registerDebugConfigurationProvider(
      "swf",
      new SWFDebugConfigurationProvider(debugPathsCallback),
      vscode.DebugConfigurationProviderTriggerKind.Initial
    )
  );
  context.subscriptions.push(
    vscode.debug.registerDebugConfigurationProvider(
      "swf",
      new SWFDebugConfigurationProvider(debugPathsCallback),
      vscode.DebugConfigurationProviderTriggerKind.Dynamic
    )
  );

  context.subscriptions.push(
    vscode.debug.registerDebugAdapterDescriptorFactory(
      "swf",
      new SWFDebugAdapterDescriptorFactory(context, debugPathsCallback)
    )
  );
}

export function deactivate() {
  savedContext = null;
}

function debugPathsCallback(): {
  javaPath: string | null | undefined;
  frameworkSdkPath?: string | null | undefined;
  editorSdkPath?: string | null | undefined;
} {
  let frameworkSdkPath = undefined;
  let editorSdkPath = undefined;
  let as3mxmlExtension = vscode.extensions.getExtension(
    "bowlerhatllc.vscode-as3mxml"
  );
  if (as3mxmlExtension && as3mxmlExtension.isActive) {
    frameworkSdkPath = as3mxmlExtension.exports.frameworkSDKPath;
    editorSdkPath = as3mxmlExtension.exports.editorSDKPath;
  }

  const javaPathSetting: string | undefined | null = vscode.workspace
    .getConfiguration("as3mxml")
    .get("java.path");
  let javaPath = findJava(javaPathSetting, (foundJavaPath) => {
    if (!savedContext) {
      return false;
    }
    return validateJava(savedContext.extensionPath, foundJavaPath);
  });

  return {
    javaPath,
    frameworkSdkPath,
    editorSdkPath,
  };
}
