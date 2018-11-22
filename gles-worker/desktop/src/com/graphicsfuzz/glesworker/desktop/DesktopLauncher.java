/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphicsfuzz.glesworker.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration.HdpiMode;
import com.badlogic.gdx.graphics.GL30;
import com.graphicsfuzz.glesworker.Main;
import com.graphicsfuzz.glesworker.PersistentData;
import com.graphicsfuzz.repackaged.org.apache.commons.io.FilenameUtils;
import com.graphicsfuzz.server.thrift.ImageJob;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class DesktopLauncher {

	private static Pattern startsWithCapsPattern = Pattern.compile("[AZ_]*", Pattern.DOTALL);
	private static String JVMCrashLogFilename = "crash.log";

	private static String getJar() {
		try {
			return new File(DesktopLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI())
					.getAbsoluteFile().toString();
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main (String[] args) throws IOException, InterruptedException {

		final String jarPath = getJar();

		if(!jarPath.endsWith(".jar")) {
			throw new RuntimeException("Starting from outside jar is currently disabled. Run/debug the jar instead.");
		}

		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			e.printStackTrace();
			System.exit(2);
		});

		ArgumentParser parser = ArgumentParsers.newArgumentParser("gles-worker-desktop")
				.defaultHelp(true);

		parser.addArgument("--local", "-local")
				.action(Arguments.storeTrue())
				.help("Connect to localhost:8080.");

		parser.addArgument("--start", "-start")
				.action(Arguments.storeTrue())
				.help("Start the (child) process that renders shaders (otherwise, start the parent process that starts the child).");

		parser.addArgument("--shortbackoff", "-shortbackoff")
				.action(Arguments.storeTrue())
				.help("Run the server with a very short backoff time (useful when developing).");

		parser.addArgument("--server", "-server")
				.help("URL of server. E.g. http://localhost:8080")
				.setDefault("http://localhost:8080")
				.type(String.class);

		parser.addArgument("--frag", "-frag")
				.help("Render a local fragment shader to a file.")
				.type(String.class);

		parser.addArgument("--comp", "-comp")
					.help("Execute a compute shader locally.")
					.type(String.class);

		parser.addArgument("--output", "-output")
				.help("Output PNG file for --frag option.")
				.setDefault("output.png")
				.type(String.class);

		Namespace ns = null;
		try {
			ns = parser.parseArgs(args);
		}
		catch (ArgumentParserException e) {
			e.getParser().handleError(e);
			return;
		}

		boolean local = ns.get("local");
		boolean start = ns.get("start");
		boolean shortBackoff = ns.get("shortbackoff");
		String server = ns.get("server");
		String frag = ns.get("frag");
		String comp = ns.get("comp");
		String output = ns.get("output");

		if(local) {
			server = "http://localhost:8080";
		}

		ImageJob standaloneRenderJob = null;

		if(frag != null) {

			if (comp != null) {
				throw new RuntimeException("Cannot pass both --frag and --comp");
			}

			standaloneRenderJob = new ImageJob();

			File fragFile = new File(frag);
			standaloneRenderJob.setName(fragFile.getName());
			standaloneRenderJob.setFragmentSource(FileUtils.readFileToString(fragFile));
			File uniformFile = new File(FilenameUtils.removeExtension(frag) + ".json");
			String uniformInfo = FileUtils.readFileToString(uniformFile);
			standaloneRenderJob.setUniformsInfo(uniformInfo);

			File vertexFile = new File(FilenameUtils.removeExtension(frag) + ".vert");
			if (vertexFile.isFile()) {
				standaloneRenderJob.setVertexSource(FileUtils.readFileToString(vertexFile));
			}
		}

		if(comp != null) {
			standaloneRenderJob = new ImageJob();
			File compFile = new File(comp);
			standaloneRenderJob.setName(compFile.getName());
			standaloneRenderJob.setComputeSource(FileUtils.readFileToString(compFile));
			File environmentFile = new File(FilenameUtils.removeExtension(comp) + ".json");
			String environmentInfo = FileUtils.readFileToString(environmentFile);
			standaloneRenderJob.setComputeInfo(environmentInfo);
		}

		// If doing a standalone shader job, or if the "start" flag was passed, start running.
		if(standaloneRenderJob != null || start) {
			start(server, standaloneRenderJob, null, shortBackoff);
			return;
		}

		// Otherwise, the current process will be the parent "monitor" process and the child will do the actual work.

		List<String> cmd = new ArrayList<>();
		cmd.addAll(Arrays.asList("java", "-XX:ErrorFile=" + JVMCrashLogFilename, "-jar", jarPath, "-start"));
		cmd.addAll(Arrays.asList(args));
		if(shortBackoff){
			cmd.add("--shortbackoff");
		}

		ProcessBuilder pb =
				new ProcessBuilder(cmd.toArray(new String [0]))
						.redirectInput(ProcessBuilder.Redirect.INHERIT)
						.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		PersistentData persistentData = new PersistentData();

		while(true) {
			Process p = pb.start();

			StringBuilder errMsgBuilder = new StringBuilder();

			BufferedReader errorReader =
					new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
			String line;

			errMsgBuilder.setLength(0);
			while ((line = errorReader.readLine()) != null) {
				if (line.contains("JobGetter: Got a job.")) {
					errMsgBuilder.setLength(0);
				} else {
					errMsgBuilder.append(line).append("\n");
				}
			}

			int res = p.waitFor();

			persistentData.appendErrMsg("### stderr from desktop program ###");
			persistentData.appendErrMsg(errMsgBuilder.toString());

			if (res == 0) {
				break;
			} else {
				File crashLogFile = new File(JVMCrashLogFilename);
				if (crashLogFile.exists()) {
					String crashLogString = FileUtils.readFileToString(crashLogFile);
					persistentData.appendErrMsg("### JVM crash log ###");
					persistentData.appendErrMsg(crashLogString);
					crashLogFile.delete();
				}
			}
		}
	}

	private static void start(
			String server,
			ImageJob standaloneRenderJob,
			String output,
			boolean shortBackoff) {
		Main main = new Main();
		if(standaloneRenderJob != null) {
			main.standaloneRenderJob = standaloneRenderJob;
			main.standaloneOutputFilename = output;
		}
		if(shortBackoff){
			main.setBackoffLimit(2);
		}

		main.setUrl(server);
    main.watchdog = new DesktopWatchdog();
    main.setOverrideLogger(new BothLogger());
    main.persistentData = new PersistentData();
		main.programBinaryGetter = new DesktopProgramBinaryGetter();
		main.computeJobManager = new DesktopComputeJobManager();

		try {
      Class<?> cl = DesktopLauncher.class.getClassLoader().loadClass("com.badlogic.gdx.backends.lwjgl3.Lwjgl3GL30");
      Constructor<?> co = cl.getDeclaredConstructor();
      co.setAccessible(true);
      GL30 gl30 = (GL30) co.newInstance();
      main.gl30 = gl30;
    }
    catch (InvocationTargetException |NoSuchMethodException|ClassNotFoundException|IllegalAccessException|InstantiationException e) {
      throw new RuntimeException(e);
    }

		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.disableAudio(true);

		//config.forceExit = false;
		config.useOpenGL3(false, 3, 2);
		config.setHdpiMode(HdpiMode.Pixels);
		config.setTitle("GLES worker");
		config.setResizable(false);
		//config.foregroundFPS = 10;
		//config.backgroundFPS = 10;
		new Lwjgl3Application(main, config);
	}
}
