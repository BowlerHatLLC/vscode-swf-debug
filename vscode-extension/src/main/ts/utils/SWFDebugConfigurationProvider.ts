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
import json5 from "json5/dist/index.mjs";

const FILE_EXTENSION_SWF = ".swf";
const FILE_EXTENSION_ANE = ".ane";
const FILE_EXTENSION_XML = ".xml";
const FILE_EXTENSION_MXML = ".mxml";
const FILE_EXTENSION_AS = ".as";
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
  rootDirectory?: string;
  extdir?: string;
  connect?: boolean;
  port?: number;
  platform?: string;
  bundle?: string;
  applicationID?: string;
  asconfigPath?: string;
}

export type SWFDebugConfigurationPathsCallback = () => {
  javaPath: string | null | undefined;
  sdkPath?: string | null | undefined;
};

export default class SWFDebugConfigurationProvider
  implements vscode.DebugConfigurationProvider
{
  constructor(pathsCallback: SWFDebugConfigurationPathsCallback) {
    this.pathsCallback = pathsCallback;
  }

  pathsCallback: SWFDebugConfigurationPathsCallback;

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
        name: "Launch SWF",
      },
    ];
    return initialConfigurations;
  }

  resolveDebugConfiguration?(
    workspaceFolder: vscode.WorkspaceFolder | undefined,
    debugConfiguration: SWFDebugConfiguration,
    token?: vscode.CancellationToken
  ): SWFDebugConfiguration | undefined {
    let paths = this.pathsCallback();
    if (!paths) {
      throw new Error("SWF debugger launch failed. Internal error.");
    }
    if (!paths.javaPath) {
      throw new Error("SWF debugger launch failed. Java path not found.");
    }

    let asconfigPath = debugConfiguration.asconfigPath;

    if (
      workspaceFolder === undefined &&
      vscode.workspace.workspaceFolders !== undefined
    ) {
      // special case: launch configuration is defined in .code-workspace file
      if (asconfigPath === undefined) {
        vscode.window.showErrorMessage(
          `Failed to debug SWF. Launch configurations in workspace files must specify asconfigPath field.`
        );
        return undefined;
      }
      let asconfigPathParts = asconfigPath.split(/[\\\/]/g);
      if (asconfigPathParts.length < 2) {
        vscode.window.showErrorMessage(
          `Failed to debug SWF. Launch configurations in workspace files must specify asconfigPath starting with workspace folder name.`
        );
        return undefined;
      }
      let workspaceNameToFind = asconfigPathParts[0];
      workspaceFolder = vscode.workspace.workspaceFolders.find(
        (workspaceFolder) => workspaceFolder.name == workspaceNameToFind
      );
      if (!workspaceFolder) {
        vscode.window.showErrorMessage(
          `Failed to debug SWF. Workspace folder not found for file: ${asconfigPath}`
        );
        return undefined;
      }
      asconfigPath = asconfigPathParts.slice(1).join(path.sep);
    }
    let asconfigJSON: any = null;
    if (workspaceFolder !== undefined) {
      asconfigPath ??= FILE_NAME_ASCONFIG_JSON;
      if (asconfigPath && !path.isAbsolute(asconfigPath)) {
        asconfigPath = path.resolve(workspaceFolder.uri.fsPath, asconfigPath);
      }
      if (asconfigPath && fs.existsSync(asconfigPath)) {
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
    if (!asconfigPath) {
      return undefined;
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
        asconfigPath,
        debugConfiguration
      );
    }
    return this.resolveLaunchDebugConfiguration(
      workspaceFolder,
      asconfigJSON,
      asconfigPath,
      paths.sdkPath,
      debugConfiguration
    );
  }

  private resolveAttachDebugConfiguration(
    workspaceFolder: vscode.WorkspaceFolder | undefined,
    asconfigJSON: any,
    asconfigPath: string,
    debugConfiguration: SWFDebugConfiguration
  ): SWFDebugConfiguration | undefined {
    const projectRoot = path.dirname(asconfigPath);
    let applicationID = debugConfiguration.applicationID;
    let bundle = debugConfiguration.bundle;
    let platformsdk = debugConfiguration.platformsdk;

    let platform = debugConfiguration.platform;
    if (platform) {
      if (!applicationID) {
        let appDescriptorPath: string | null = null;
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
          if (!path.isAbsolute(appDescriptorPath)) {
            appDescriptorPath = path.resolve(projectRoot, appDescriptorPath);
          }
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
              if (bundle && !path.isAbsolute(bundle)) {
                bundle = path.resolve(projectRoot, bundle);
              }
            }
          }
          if (!bundle && "output" in airOptions) {
            bundle = airOptions.output;
            if (bundle && !path.isAbsolute(bundle)) {
              bundle = path.resolve(projectRoot, bundle);
            }
          }
        }
      }
      if (!platformsdk) {
        if (asconfigJSON && "airOptions" in asconfigJSON) {
          let airOptions = asconfigJSON.airOptions;
          if (platform in airOptions) {
            let platformOptions = airOptions[platform];
            if ("platformsdk" in platformOptions) {
              platformsdk = platformOptions.platformsdk;
            }
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
        let bundleMessage = `Error reading Adobe AIR output path for platform "${platform}"`;
        if (asconfigPath) {
          bundleMessage += ` from ${path.basename(asconfigPath)}`;
        }
        bundleMessage += ".";
        vscode.window.showErrorMessage(bundleMessage);
        return undefined;
      }
      debugConfiguration.applicationID = applicationID;
      debugConfiguration.bundle = bundle;
      debugConfiguration.platformsdk = platformsdk;
    }
    return debugConfiguration;
  }

  private resolveLaunchDebugConfiguration(
    workspaceFolder: vscode.WorkspaceFolder | undefined,
    asconfigJSON: any,
    asconfigPath: string,
    sdkPath: string | null | undefined,
    debugConfiguration: SWFDebugConfiguration
  ): SWFDebugConfiguration | undefined {
    const projectRoot = path.dirname(asconfigPath);
    let program = debugConfiguration.program;
    let appDescriptorPath: string | null = null;
    let outputPath: string | null = null;
    let mainClassPath: string | null = null;
    let animateFilePath: string | null = null;
    let sourcePath: string[] | null = null;
    let libraryPath: string[] | null = null;
    let externalLibraryPath: string[] | null = null;
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
        if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
          appDescriptorPath = path.resolve(projectRoot, appDescriptorPath);
        }
      } else {
        let application = asconfigJSON.application;
        switch (debugConfiguration.versionPlatform) {
          case "AND": {
            if ("android" in application) {
              appDescriptorPath = application.android;
              if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
                appDescriptorPath = path.resolve(
                  projectRoot,
                  appDescriptorPath
                );
              }
            }
            break;
          }
          case "IOS": {
            if ("ios" in application) {
              appDescriptorPath = application.ios;
              if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
                appDescriptorPath = path.resolve(
                  projectRoot,
                  appDescriptorPath
                );
              }
            }
            break;
          }
          case "WIN": {
            if ("windows" in application) {
              appDescriptorPath = application.windows;
              if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
                appDescriptorPath = path.resolve(
                  projectRoot,
                  appDescriptorPath
                );
              }
            }
            break;
          }
          case "MAC": {
            if ("mac" in application) {
              appDescriptorPath = application.mac;
              if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
                appDescriptorPath = path.resolve(
                  projectRoot,
                  appDescriptorPath
                );
              }
            }
            break;
          }
          default: {
            if (isMobile) {
              //if we know it's mobile, any mobile platform should be fine
              if ("ios" in application) {
                appDescriptorPath = application.ios;
                if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
                  appDescriptorPath = path.resolve(
                    projectRoot,
                    appDescriptorPath
                  );
                }
              } else if ("android" in application) {
                appDescriptorPath = application.android;
                if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
                  appDescriptorPath = path.resolve(
                    projectRoot,
                    appDescriptorPath
                  );
                }
              }
            } else {
              //if it's desktop, then try to use the existing platform
              if (process.platform === "win32") {
                if ("windows" in application) {
                  appDescriptorPath = application.windows;
                  if (
                    appDescriptorPath &&
                    !path.isAbsolute(appDescriptorPath)
                  ) {
                    appDescriptorPath = path.resolve(
                      projectRoot,
                      appDescriptorPath
                    );
                  }
                }
              } else if ("mac" in application) {
                appDescriptorPath = application.mac;
                if (appDescriptorPath && !path.isAbsolute(appDescriptorPath)) {
                  appDescriptorPath = path.resolve(
                    projectRoot,
                    appDescriptorPath
                  );
                }
              }
            }
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
        if (outputPath && !path.isAbsolute(outputPath)) {
          outputPath = path.resolve(projectRoot, outputPath);
        }
      }
      if ("source-path" in compilerOptions) {
        sourcePath = asconfigJSON.compilerOptions["source-path"];
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
        if (mainClassPath && !path.isAbsolute(mainClassPath)) {
          mainClassPath = path.resolve(projectRoot, mainClassPath);
        }
      }
    }
    if (asconfigJSON && "mainClass" in asconfigJSON) {
      let mainClass = asconfigJSON.mainClass;
      let mainClassParts = mainClass.split(".");
      if (sourcePath === null) {
        sourcePath = ["."];
      }
      let mainClassPrefix = mainClassParts.join(path.sep);
      for (let sourcePathEntry of sourcePath) {
        let filePath = path.join(
          sourcePathEntry,
          mainClassPrefix + FILE_EXTENSION_AS
        );
        let absoluteFilePath = filePath;
        if (!path.isAbsolute(absoluteFilePath)) {
          absoluteFilePath = path.resolve(projectRoot, absoluteFilePath);
        }
        if (fs.existsSync(absoluteFilePath)) {
          mainClassPath = absoluteFilePath;
        } else {
          let filePath = path.join(
            sourcePathEntry,
            mainClassPrefix + FILE_EXTENSION_MXML
          );
          let absoluteFilePath = filePath;
          if (!path.isAbsolute(absoluteFilePath)) {
            absoluteFilePath = path.resolve(projectRoot, absoluteFilePath);
          }
          if (fs.existsSync(absoluteFilePath)) {
            mainClassPath = absoluteFilePath;
          }
        }
      }
    }
    if (asconfigJSON && "animateOptions" in asconfigJSON) {
      let animateOptions = asconfigJSON.animateOptions;
      if ("file" in animateOptions) {
        animateFilePath = animateOptions.file;
        if (animateFilePath && !path.isAbsolute(animateFilePath)) {
          animateFilePath = path.resolve(projectRoot, animateFilePath);
        }
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
        programDir = path.resolve(projectRoot, programDir);
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
            newItem = path.resolve(projectRoot, newItem);
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
                .filter((child) => child.endsWith(FILE_EXTENSION_ANE))
                .map((child) => path.resolve(newItem, child))
            );
          }
          return result;
        };
        let anePaths: string[] = [];
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
  outputPath: string | null | undefined,
  mainClassPath: string | null | undefined
): string | undefined {
  let descriptorName: string | null = null;
  if (mainClassPath) {
    descriptorName = path.basename(mainClassPath);
  } else if (outputPath) {
    descriptorName = path.basename(outputPath);
  }

  if (!descriptorName || !outputPath) {
    return undefined;
  }

  let index = descriptorName.indexOf(".");
  if (index !== -1) {
    descriptorName = descriptorName.substr(0, index);
  }
  return path.join(path.dirname(outputPath), descriptorName + SUFFIX_AIR_APP);
}

function findApplicationID(appDescriptorContent: string): string | undefined {
  // https://help.adobe.com/en_US/air/build/WSfffb011ac560372f2fea1812938a6e463-8000.html#WSfffb011ac560372f2fea1812938a6e463-7ffe
  let result = appDescriptorContent.match(/<id>([A-Za-z0-9\-\.]+)<\/id>/);
  if (result) {
    return result[1];
  }
  return undefined;
}
