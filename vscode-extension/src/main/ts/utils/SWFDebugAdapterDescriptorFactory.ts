/*
Copyright 2016-2019 Bowler Hat LLC

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
import * as path from "path";
import * as vscode from "vscode";
import getJavaClassPathDelimiter from "../utils/getJavaClassPathDelimiter";

export type SWFDebugAdapterPathsCallback = () => {
  javaPath: string | null | undefined;
  frameworkSdkPath?: string | null | undefined;
  editorSdkPath?: string | null | undefined;
};

export default class SWFDebugAdapterDescriptorFactory
  implements vscode.DebugAdapterDescriptorFactory
{
  constructor(
    extensionContext: vscode.ExtensionContext,
    pathsCallback: SWFDebugAdapterPathsCallback
  ) {
    this.extensionContext = extensionContext;
    this.pathsCallback = pathsCallback;
  }

  extensionContext: vscode.ExtensionContext;
  pathsCallback: SWFDebugAdapterPathsCallback;

  createDebugAdapterDescriptor(
    session: vscode.DebugSession,
    executable: vscode.DebugAdapterExecutable
  ): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
    let paths = this.pathsCallback();
    if (!paths) {
      throw new Error("SWF debugger launch failed. Internal error.");
    }
    if (!paths.javaPath) {
      throw new Error("SWF debugger launch failed. Java path not found.");
    }
    let args = [
      //uncomment to debug the SWF debugger JAR
      //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",

      "-cp",
      this.getClassPath(),
      "com.as3mxml.vscode.SWFDebug",
    ];
    if (session.workspaceFolder) {
      args.unshift("-Dworkspace=" + session.workspaceFolder.uri.fsPath);
    }
    if (paths.frameworkSdkPath) {
      //don't pass in an SDK unless we have one set
      args.unshift(
        "-Dflexlib=" + path.resolve(paths.frameworkSdkPath, "frameworks")
      );
    }
    return new vscode.DebugAdapterExecutable(paths.javaPath, args);
  }

  private getClassPath() {
    return (
      path.resolve(this.extensionContext.extensionPath, "bin", "*") +
      getJavaClassPathDelimiter() +
      path.resolve(this.extensionContext.extensionPath, "bundled-debugger", "*")
    );
  }
}
