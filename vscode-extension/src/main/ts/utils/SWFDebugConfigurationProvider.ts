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
import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";
import * as json5 from "json5";

const FILE_EXTENSION_SWF = ".swf";
const FILE_EXTENSION_ANE = ".ane";
const FILE_EXTENSION_XML = ".xml";
const SUFFIX_AIR_APP = "-app.xml";
const FILE_NAME_UNPACKAGED_ANES = ".as3mxml-unpackaged-anes";
const FILE_NAME_ASCONFIG_JSON = "asconfig.json";

const CONFIG_AIR = "air";
const CONFIG_AIRMOBILE = "airmobile";

const PROFILE_MOBILE_DEVICE = "mobileDevice";

interface SWFDebugConfiguration extends vscode.DebugConfiguration {
  program?: string;
  profile?: string;
  screenDPI?: number;
  screensize?: string;
  args?: string[];
  versionPlatform?: string;
  runtimeExecutable?: string;
  runtimeArgs?: string[];
  extdir?: string;
  connect?: boolean;
  port?: number;
  platform?: string;
  bundle?: string;
  applicationID?: string;
}

export type SWFDebugConfigurationPathsCallback = () => {
  javaPath: string;
  sdkPath?: string;
};

export default class SWFDebugConfigurationProvider
  implements vscode.DebugConfigurationProvider {
  constructor(public pathsCallback: SWFDebugConfigurationPathsCallback) {}

  provideDebugConfigurations(
    workspaceFolder: vscode.WorkspaceFolder | undefined,
    token?: vscode.CancellationToken
  ): vscode.ProviderResult<SWFDebugConfiguration[]> {
    if (workspaceFolder === undefined) {
      return [];
    }
    const initialConfigurations = [
      //this is enough to resolve the rest based on asconfig.json
      {
        type: "swf",
        request: "launch",
        name: "Launch SWF"
      }
    ];
    return initialConfigurations;
  }

  async resolveDebugConfiguration?(
    workspaceFolder: vscode.WorkspaceFolder | undefined,
    debugConfiguration: SWFDebugConfiguration,
    token?: vscode.CancellationToken
  ): Promise<SWFDebugConfiguration> {
    let paths = this.pathsCallback();
    if (!paths) {
      throw new Error("SWF debugger launch failed. Internal error.");
    }
    if (!paths.javaPath) {
      throw new Error("SWF debugger launch failed. Java path not found.");
    }

    let asconfigJSON: any = null;
    if (workspaceFolder !== undefined) {
      let workspaceFolderPath = workspaceFolder.uri.fsPath;
      let asconfigPath = path.resolve(
        workspaceFolderPath,
        FILE_NAME_ASCONFIG_JSON
      );
      if (fs.existsSync(asconfigPath)) {
        try {
          let asconfigFile = fs.readFileSync(asconfigPath, "utf8");
          asconfigJSON = json5.parse(asconfigFile);
        } catch (error) {
          //something went terribly wrong!
          vscode.window.showErrorMessage(
            `Failed to debug SWF. Error reading file: ${asconfigPath}`
          );
          console.error(error);
          return undefined;
        }
      }
    }

    if (!debugConfiguration.type) {
      debugConfiguration.type = "swf";
    }
    if (!debugConfiguration.request) {
      //attach is an advanced option, so it should be configured manually
      //by the user in launch.json
      debugConfiguration.request = "launch";
    }
    if (debugConfiguration.request === "attach") {
      //nothing else to resolve
      return this.resolveAttachDebugConfiguration(
        workspaceFolder,
        asconfigJSON,
        debugConfiguration
      );
    }
    return this.resolveLaunchDebugConfiguration(
      workspaceFolder,
      asconfigJSON,
      paths.sdkPath,
      debugConfiguration
    );
  }

  private resolveAttachDebugConfiguration(
    workspaceFolder: vscode.WorkspaceFolder,
    asconfigJSON: any,
    debugConfiguration: SWFDebugConfiguration
  ): SWFDebugConfiguration {
    let applicationID = debugConfiguration.applicationID;
    let bundle = debugConfiguration.bundle;

    let platform = debugConfiguration.platform;
    if (platform) {
      if (!applicationID) {
        let appDescriptorPath: string = null;
        if (asconfigJSON && "application" in asconfigJSON) {
          if (typeof asconfigJSON.application === "string") {
            appDescriptorPath = asconfigJSON.application;
          } else if (
            debugConfiguration.platform &&
            debugConfiguration.platform in asconfigJSON.application
          ) {
            appDescriptorPath =
              asconfigJSON.application[debugConfiguration.platform];
          }
        }
        if (appDescriptorPath) {
          appDescriptorPath = path.resolve(
            workspaceFolder.uri.fsPath,
            appDescriptorPath
          );
          try {
            let appDescriptorContent = fs.readFileSync(
              appDescriptorPath,
              "utf8"
            );
            applicationID = findApplicationID(appDescriptorContent);
          } catch (e) {
            //something went terribly wrong!
            vscode.window.showErrorMessage(
              `Failed to debug SWF. Error reading file: ${appDescriptorPath}`
            );
            return undefined;
          }
        }
      }
      if (!bundle) {
        if (asconfigJSON && "airOptions" in asconfigJSON) {
          let airOptions = asconfigJSON.airOptions;
          if (platform in airOptions) {
            let platformOptions = airOptions[platform];
            if ("output" in platformOptions) {
              bundle = platformOptions.output;
            }
          }
          if (!bundle && "output" in airOptions) {
            bundle = airOptions.output;
          }
        }
      }

      if (!applicationID) {
        vscode.window.showErrorMessage(
          `Error reading Adobe AIR application <id> for platform "${platform}".`
        );
        return undefined;
      }
      if (!bundle) {
        vscode.window.showErrorMessage(
          `Error reading Adobe AIR output path for platform "${platform}".`
        );
        return undefined;
      }
      debugConfiguration.applicationID = applicationID;
      debugConfiguration.bundle = bundle;
    }
    return debugConfiguration;
  }

  private resolveLaunchDebugConfiguration(
    workspaceFolder: vscode.WorkspaceFolder,
    asconfigJSON: any,
    sdkPath: string | undefined,
    debugConfiguration: SWFDebugConfiguration
  ): SWFDebugConfiguration {
    let program = debugConfiguration.program;
    let appDescriptorPath: string = null;
    let outputPath: string = null;
    let mainClassPath: string = null;
    let animateFilePath: string = null;
    let libraryPath: string[] = null;
    let externalLibraryPath: string[] = null;
    let requireAIR = false;
    let isMobile = false;
    if (asconfigJSON && "config" in asconfigJSON) {
      isMobile = asconfigJSON.config === CONFIG_AIRMOBILE;
      requireAIR = isMobile || asconfigJSON.config === CONFIG_AIR;
    }
    if (asconfigJSON && "application" in asconfigJSON) {
      requireAIR = true;
      if (typeof asconfigJSON.application === "string") {
        appDescriptorPath = asconfigJSON.application;
      } else {
        let application = asconfigJSON.application;
        switch (debugConfiguration.versionPlatform) {
          case "AND": {
            if ("android" in application) {
              appDescriptorPath = application.android;
            }
            break;
          }
          case "IOS": {
            if ("ios" in application) {
              appDescriptorPath = application.ios;
            }
            break;
          }
          case "WIN": {
            if ("windows" in application) {
              appDescriptorPath = application.windows;
            }
            break;
          }
          case "MAC": {
            if ("mac" in application) {
              appDescriptorPath = application.mac;
            }
            break;
          }
        }
      }
    }
    if (
      "profile" in debugConfiguration ||
      "screensize" in debugConfiguration ||
      "screenDPI" in debugConfiguration ||
      "versionPlatform" in debugConfiguration ||
      "extdir" in debugConfiguration ||
      "args" in debugConfiguration
    ) {
      //if any of these fields are specified in the debug
      //configuration, then AIR is required!
      requireAIR = true;
    }
    if (asconfigJSON && "compilerOptions" in asconfigJSON) {
      let compilerOptions = asconfigJSON.compilerOptions;
      if ("output" in compilerOptions) {
        outputPath = asconfigJSON.compilerOptions.output;
      }
      if ("library-path" in compilerOptions) {
        libraryPath = asconfigJSON.compilerOptions["library-path"];
      }
      if ("external-library-path" in compilerOptions) {
        externalLibraryPath =
          asconfigJSON.compilerOptions["external-library-path"];
      }
    }
    if (asconfigJSON && "files" in asconfigJSON) {
      let files = asconfigJSON.files;
      if (Array.isArray(files) && files.length > 0) {
        //the last entry in the files field is the main
        //class used as the entry point.
        mainClassPath = files[files.length - 1];
      }
    }
    if (asconfigJSON && "animateOptions" in asconfigJSON) {
      let animateOptions = asconfigJSON.animateOptions;
      if ("file" in animateOptions) {
        animateFilePath = animateOptions.file;
      }
    }
    if (program && program.endsWith(FILE_EXTENSION_XML)) {
      requireAIR = true;
    }
    if (!program) {
      if (appDescriptorPath !== null) {
        //start by checking if this is an AIR app
        if (outputPath !== null) {
          //if an output compiler option is specified, use the
          //version of the AIR application descriptor that is copied
          //to the output directory.
          let appDescriptorBaseName = path.basename(appDescriptorPath);
          let outputDir = path.dirname(outputPath);
          program = path.join(outputDir, appDescriptorBaseName);
        } else {
          //if there is no output compiler option, default to the
          //original path of the AIR application descriptor.
          program = appDescriptorPath;
        }
      } else if (requireAIR) {
        //it's an AIR app, but no application descriptor is specified.
        //the build will copy the descriptor from the SDK instead, and
        //we can generate the name automatically.
        program = generateApplicationDescriptorProgram(
          outputPath,
          mainClassPath
        );
      } else if (outputPath !== null) {
        //if the output compiler option is specified, then that's what
        //we'll launch.
        program = outputPath;
      } else if (animateFilePath !== null) {
        let extension = path.extname(animateFilePath);
        program =
          animateFilePath.substr(0, animateFilePath.length - extension.length) +
          FILE_EXTENSION_SWF;
      } else if (mainClassPath !== null) {
        //if no output compiler option is specified, the compiler
        //defaults to using the same file name as the main class, but
        //with the .swf extension instead. the .swf file goes into the
        //the same directory as the main class
        let extension = path.extname(mainClassPath);
        program =
          mainClassPath.substr(0, mainClassPath.length - extension.length) +
          FILE_EXTENSION_SWF;
      }
    }
    if (!program) {
      vscode.window.showErrorMessage(
        `Missing "program" path for SWF debug configuration. Must be a .swf file or an Adobe AIR application descriptor.`
      );
      return undefined;
    }
    if (requireAIR && !sdkPath && !debugConfiguration.runtimeExecutable) {
      vscode.window.showErrorMessage(
        `Missing "runtime executable" path for SWF debug configuration. Requires "adl" from an Adobe AIR SDK.`
      );
      return undefined;
    }

    if (requireAIR && !debugConfiguration.extdir) {
      let programDir = path.dirname(program);
      if (!path.isAbsolute(programDir)) {
        programDir = path.resolve(workspaceFolder.uri.fsPath, programDir);
      }
      let unpackagedDir = path.resolve(programDir, FILE_NAME_UNPACKAGED_ANES);
      //if ANEs haven't been unpackaged, don't bother checking the library
      //path or external library path
      if (
        fs.existsSync(unpackagedDir) &&
        fs.statSync(unpackagedDir).isDirectory()
      ) {
        let reduceCallback = (result: string[], newItem: string) => {
          if (!path.isAbsolute(newItem)) {
            newItem = path.resolve(workspaceFolder.uri.fsPath, newItem);
          }
          if (newItem.endsWith(FILE_EXTENSION_ANE)) {
            result.push(newItem);
          } else if (
            fs.existsSync(newItem) &&
            fs.statSync(newItem).isDirectory()
          ) {
            result.push(
              ...fs
                .readdirSync(newItem)
                .filter(child => child.endsWith(FILE_EXTENSION_ANE))
                .map(child => path.resolve(newItem, child))
            );
          }
          return result;
        };
        let anePaths = [];
        if (libraryPath !== null) {
          libraryPath.reduce(reduceCallback, anePaths);
        }
        if (externalLibraryPath !== null) {
          externalLibraryPath.reduce(reduceCallback, anePaths);
        }
        if (anePaths.length > 0) {
          //if we found any ANEs in the library path or external
          //library path, populate extdir
          debugConfiguration.extdir = unpackagedDir;
        }
      }
    }
    if (!debugConfiguration.profile) {
      //save the user from having to specify the profile manually, in a
      //couple of special cases
      if (isMobile) {
        debugConfiguration.profile = PROFILE_MOBILE_DEVICE;
      } else if (!isMobile && debugConfiguration.extdir) {
        //required for native extensions on desktop
        debugConfiguration.profile = "extendedDesktop";
      }
    }
    debugConfiguration.program = program;
    return debugConfiguration;
  }
}

function generateApplicationDescriptorProgram(
  outputPath: string,
  mainClassPath: string
) {
  if (outputPath === null || mainClassPath === null) {
    return null;
  }
  if (outputPath !== null) {
    let descriptorName = path.basename(outputPath);
    let index = descriptorName.indexOf(".");
    if (index !== -1) {
      descriptorName = descriptorName.substr(0, index);
    }
    return path.join(path.dirname(outputPath), descriptorName + SUFFIX_AIR_APP);
  }

  let extension = path.extname(mainClassPath);
  return (
    mainClassPath.substr(0, mainClassPath.length - extension.length) +
    SUFFIX_AIR_APP
  );
}

function findApplicationID(appDescriptorContent: string): string {
  let result = appDescriptorContent.match(/<id>([\w+\.]+)<\/id>/);
  if (result) {
    return result[1];
  }
  return null;
}
