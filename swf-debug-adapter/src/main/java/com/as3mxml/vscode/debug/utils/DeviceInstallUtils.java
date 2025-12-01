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
package com.as3mxml.vscode.debug.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;

public class DeviceInstallUtils {
	private static final String PLATFORM_IOS = "ios";
	private static final String PLATFORM_IOS_SIMULATOR = "ios_simulator";
	private static final String ENV_AIR_IOS_SIMULATOR_DEVICE = "AIR_IOS_SIMULATOR_DEVICE";

	public static class DeviceCommandResult {
		public DeviceCommandResult(boolean error) {
			this.error = error;
		}

		public DeviceCommandResult(boolean error, String message) {
			this.error = error;
			this.message = message;
		}

		public boolean error;
		public String message;
	}

	public static DeviceCommandResult runUninstallCommand(String platform, String appID, Path workspacePath,
			Path adtPath, Path platformSdkPath) {
		boolean iosSimulator = PLATFORM_IOS_SIMULATOR.equals(platform);
		String adtPlatform = iosSimulator ? PLATFORM_IOS : platform;
		ArrayList<String> options = new ArrayList<>();
		options.add(adtPath.toString());
		options.add("-uninstallApp");
		options.add("-platform");
		options.add(adtPlatform);
		if (platformSdkPath != null) {
			options.add("-platformsdk");
			options.add(platformSdkPath.toString());
		}
		if (iosSimulator) {
			options.add("-device");
			options.add("ios-simulator");
		}
		options.add("-appid");
		options.add(appID);

		File cwd = workspacePath.toFile();
		int status = -1;
		try {
			ProcessBuilder builder = new ProcessBuilder().command(options).directory(cwd).inheritIO();
			String simulatorDeviceName = null;
			if (PLATFORM_IOS_SIMULATOR.equals(platform)) {
				simulatorDeviceName = System.getenv(ENV_AIR_IOS_SIMULATOR_DEVICE);
				if (simulatorDeviceName == null) {
					simulatorDeviceName = findSimulatorName(workspacePath);
				}
			}
			if (simulatorDeviceName != null) {
				builder.environment().put(ENV_AIR_IOS_SIMULATOR_DEVICE, simulatorDeviceName);
			}
			Process process = builder.start();
			status = process.waitFor();
		} catch (InterruptedException e) {
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform
					+ "\" and application ID \"" + appID + "\" with error: " + e.toString());
		} catch (IOException e) {
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform
					+ "\" and application ID \"" + appID + "\" with error: " + e.toString());
		}
		// 14 means that the app isn't installed on the device, and that's fine
		if (status != 0 && status != 14) {
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform
					+ "\" and application ID \"" + appID + "\" with status code " + status + ".");
		}
		return new DeviceCommandResult(false);
	}

	public static DeviceCommandResult runInstallCommand(String platform, Path packagePath, Path workspacePath,
			Path adtPath, Path platformSdkPath) {
		boolean iosSimulator = PLATFORM_IOS_SIMULATOR.equals(platform);
		String adtPlatform = iosSimulator ? PLATFORM_IOS : platform;
		ArrayList<String> options = new ArrayList<>();
		options.add(adtPath.toString());
		options.add("-installApp");
		options.add("-platform");
		options.add(adtPlatform);
		if (platformSdkPath != null) {
			options.add("-platformsdk");
			options.add(platformSdkPath.toString());
		}
		if (iosSimulator) {
			options.add("-device");
			options.add("ios-simulator");
		}
		options.add("-package");
		options.add(packagePath.toString());

		File cwd = workspacePath.toFile();
		int status = -1;
		try {
			ProcessBuilder builder = new ProcessBuilder().command(options).directory(cwd).inheritIO();
			String simulatorDeviceName = null;
			if (PLATFORM_IOS_SIMULATOR.equals(platform)) {
				simulatorDeviceName = System.getenv(ENV_AIR_IOS_SIMULATOR_DEVICE);
				if (simulatorDeviceName == null) {
					simulatorDeviceName = findSimulatorName(workspacePath);
				}
			}
			if (simulatorDeviceName != null) {
				builder.environment().put(ENV_AIR_IOS_SIMULATOR_DEVICE, simulatorDeviceName);
			}
			Process process = builder.start();
			status = process.waitFor();
		} catch (InterruptedException e) {
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform
					+ "\" and path \"" + packagePath.toString() + "\" with error: " + e.toString());
		} catch (IOException e) {
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform
					+ "\" and path \"" + packagePath.toString() + "\" with error: " + e.toString());
		}
		if (status != 0) {
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform
					+ "\" and path \"" + packagePath.toString() + "\" with status code: " + status + ".");
		}
		return new DeviceCommandResult(false);
	}

	public static DeviceCommandResult runLaunchCommand(String platform, String appID, Path workspacePath, Path adtPath,
			Path platformSdkPath) {
		boolean iosSimulator = PLATFORM_IOS_SIMULATOR.equals(platform);
		String adtPlatform = iosSimulator ? PLATFORM_IOS : platform;
		ArrayList<String> options = new ArrayList<>();
		options.add(adtPath.toString());
		options.add("-launchApp");
		options.add("-platform");
		options.add(adtPlatform);
		if (platformSdkPath != null) {
			options.add("-platformsdk");
			options.add(platformSdkPath.toString());
		}
		if (iosSimulator) {
			options.add("-device");
			options.add("ios-simulator");
		}
		options.add("-appid");
		options.add(appID);

		File cwd = workspacePath.toFile();
		int status = -1;
		try {
			ProcessBuilder builder = new ProcessBuilder().command(options).directory(cwd).inheritIO();
			String simulatorDeviceName = null;
			if (PLATFORM_IOS_SIMULATOR.equals(platform)) {
				simulatorDeviceName = System.getenv(ENV_AIR_IOS_SIMULATOR_DEVICE);
				if (simulatorDeviceName == null) {
					simulatorDeviceName = findSimulatorName(workspacePath);
				}
			}
			if (simulatorDeviceName != null) {
				builder.environment().put(ENV_AIR_IOS_SIMULATOR_DEVICE, simulatorDeviceName);
			}
			Process process = builder.start();
			status = process.waitFor();
		} catch (InterruptedException e) {
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform
					+ "\" and application ID \"" + appID + "\" with error: " + e.toString());
		} catch (IOException e) {
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform
					+ "\" and application ID \"" + appID + "\" with error: " + e.toString());
		}
		if (status != 0) {
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform
					+ "\" and application ID \"" + appID + "\" with status code: " + status + ".");
		}
		return new DeviceCommandResult(false);
	}

	public static void stopForwardPortCommand(String platform, int port, Path workspacePath, Path adbPath,
			Path idbPath) {
		ArrayList<String> options = new ArrayList<>();
		if (platform.equals(PLATFORM_IOS)) {
			options.add(idbPath.toString());
			options.add("-stopforward");
			options.add(Integer.toString(port));
		} else if (platform.equals("android")) {
			options.add(adbPath.toString());
			options.add("forward");
			options.add("--remove");
			options.add("tcp:" + Integer.toString(port));
		}
		File cwd = workspacePath.toFile();
		try {
			Process process = new ProcessBuilder().command(options).directory(cwd).inheritIO().start();
			process.waitFor();
		} catch (InterruptedException e) {
		} catch (IOException e) {
		}
	}

	public static DeviceCommandResult forwardPortCommand(String platform, int port, Path workspacePath, Path adbPath,
			Path idbPath) {
		ArrayList<String> options = new ArrayList<>();
		if (platform.equals(PLATFORM_IOS)) {
			String deviceHandle = findDeviceHandle(workspacePath, idbPath);
			if (deviceHandle == null) {
				return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform
						+ "\" and port " + port + " because no connected devices could be found.");
			}
			options.add(idbPath.toString());
			options.add("-forward");
			options.add(Integer.toString(port));
			options.add(Integer.toString(port));
			options.add(deviceHandle);
		} else if (platform.equals("android")) {
			options.add(adbPath.toString());
			options.add("forward");
			options.add("tcp:" + port);
			options.add("tcp:" + port);
		}

		File cwd = workspacePath.toFile();
		int status = -1;
		try {
			Process process = new ProcessBuilder().command(options).directory(cwd).inheritIO().start();
			if (platform.equals(PLATFORM_IOS)) {
				// if idb starts successfully, it will continue running without
				// exiting. we'll stop it later!
				try {
					status = process.exitValue();
				} catch (IllegalThreadStateException e) {
					status = 0;
				}
			} else {
				status = process.waitFor();
			}
		} catch (InterruptedException e) {
			return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform
					+ "\" and port " + port + " with error: " + e.toString());
		} catch (IOException e) {
			return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform
					+ "\" and port " + port + " with error: " + e.toString());
		}
		if (status != 0) {
			return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform
					+ "\" and port " + port + " with status code: " + status + ".");
		}
		return new DeviceCommandResult(false);
	}

	private static String findDeviceHandle(Path workspacePath, Path idbPath) {
		ArrayList<String> options = new ArrayList<>();
		options.add(idbPath.toString());
		options.add("-devices");

		File cwd = workspacePath.toFile();
		Process process = null;
		int status = -1;
		try {
			process = new ProcessBuilder().command(options).directory(cwd).redirectInput(Redirect.INHERIT)
					.redirectError(Redirect.INHERIT).redirectOutput(Redirect.PIPE).start();
			status = process.waitFor();
		} catch (InterruptedException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
		if (status != 0) {
			return null;
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		try {
			// if no devices are attached, the output looks like this:

			// @formatter:off
			// No connected device found.
			// @formatter:on

			// otherwise, the output looks like this:

			// @formatter:off
			//List of attached devices:
			//Handle	DeviceClass	DeviceUUID					DeviceName
			//   1	iPhone  	0000000000000000000000000000000000000000	iPhone
			// @formatter:on
			String line = null;
			while ((line = reader.readLine()) != null) {
				// line may start with either 2 or 3 spaces
				if (line.startsWith("  ")) {
					line = line.trim(); // strip spaces from beginning
					int index = line.indexOf("\t");
					if (index != -1) {
						return line.substring(0, index);
					}
				}
			}
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	private static String findSimulatorName(Path workspacePath) {
		ArrayList<String> options = new ArrayList<>();
		options.add("xcrun");
		options.add("simctl");
		options.add("list");
		options.add("devices");

		File cwd = workspacePath.toFile();
		Process process = null;
		int status = -1;
		try {
			process = new ProcessBuilder().command(options).directory(cwd).redirectInput(Redirect.INHERIT)
					.redirectError(Redirect.INHERIT).redirectOutput(Redirect.PIPE).start();
			status = process.waitFor();
		} catch (InterruptedException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
		if (status != 0) {
			return null;
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		try {
			// the output looks like this:

			// @formatter:off
			// == Devices ==
			// -- iOS 14.5 --
			//     iPhone 8 (87A78656-482D-4769-A1DB-29A1947A55F0) (Shutdown) 
			//     iPhone 8 Plus (F0C315C4-EC26-4BAC-9424-9E7DEFF5C558) (Shutdown) 
			// @formatter:on
			String line = null;
			while ((line = reader.readLine()) != null) {
				// line may start with either 2 or 3 spaces
				if (line.startsWith("  ")) {
					line = line.trim(); // strip spaces from beginning
					if (line.startsWith("iPhone ")) {
						int index = line.indexOf("(");
						if (index != -1) {
							return line.substring(0, index - 1);
						}
					}
				}
			}
			return null;
		} catch (IOException e) {
			return null;
		}
	}
}
