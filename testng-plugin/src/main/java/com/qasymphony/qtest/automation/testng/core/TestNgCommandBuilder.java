package com.qasymphony.qtest.automation.testng.core;

import com.qasymphony.qtest.automation.domain.execution.AntExecutionMode;
import com.qasymphony.qtest.automation.domain.execution.CommandLineExecutionMode;
import com.qasymphony.qtest.automation.domain.execution.MavenExecutionMode;
import com.qasymphony.qtest.automation.domain.job.AutomationMaterial;
import com.qasymphony.qtest.automation.plugin.access.atm.model.BuildCommandRequest;
import com.qasymphony.qtest.automation.plugin.access.atm.model.CommandResponse;
import com.qasymphony.qtest.automation.plugin.api.logging.Logger;
import com.qasymphony.qtest.automation.util.Lists;
import com.qasymphony.qtest.automation.util.SystemEnvironment;
import com.qasymphony.qtest.automation.util.Systems;
import com.qasymphony.qtest.automation.util.command.CommandLines;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.qas.api.internal.util.google.base.Function;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * TestNgCommandBuilder
 *
 * @author Dzung Nguyen
 * @version $Id TestNgCommandBuilder 2015-06-08 09:35:30z dzungvnguyen $
 * @since 1.0
 */
public class TestNgCommandBuilder {
  //~ class properties ========================================================
  private static final String TESTNG_LOG_COLLECTOR_NAME = "testng-plugin-log-collector.jar";
  private static final Logger LOG = Logger.getLogger(TestNgCommandBuilder.class);
  private final TestCaseRunService testCaseRunService;
  private final SystemEnvironment systemEnvironment;

  //~ class members ===========================================================
  /**
   * Creates {@link TestNgCommandBuilder TestNg command builder} instance.
   */
  public TestNgCommandBuilder(SystemEnvironment systemEnvironment) {
    this.testCaseRunService = new TestCaseRunService();
    this.systemEnvironment = systemEnvironment;
  }

  /**
   * Builds the command command request.
   *
   * @param commandRequest the given command build request.
   * @return the given command build response.
   */
  public CommandResponse buildCommand(BuildCommandRequest commandRequest) {
    // build command.
    CommandResponse commandResponse = internalBuildCommand(commandRequest);
    if (commandResponse.hasError()) return commandResponse;

    // setup environment.
    Map<String, String> environmentVariables = new HashMap<>();
    setupEnvironment(commandRequest, environmentVariables);
    commandResponse.withTaskEnvironmentVariables(environmentVariables);

    return commandResponse;
  }

  /**
   * Cleanup environment.
   *
   * @param commandRequest the given command-request instance.
   * @param environmentVariables the given environment variable.
   */
  public void cleanupEnvironment(BuildCommandRequest commandRequest, Map<String, String> environmentVariables) {
    if (environmentVariables == null || environmentVariables.isEmpty()) return;

    File templateFile = getTemplateFile(commandRequest, environmentVariables);
    if (templateFile != null) {
      File originalFile = createFileFromOriginal(templateFile);
      try {
        if (originalFile != null && originalFile.exists()) {
          FileUtils.copyFile(originalFile, templateFile);
          originalFile.delete();
        }
      } catch (IOException ioe) {
        LOG.warn("[TestNG CommandBuilder] could not cleanup environment, message: " + ioe.getMessage());
      }

      if ("true".equals(environmentVariables.get("DELETE_TEMPLATE"))) {
        if (templateFile.exists()) templateFile.delete();
      }
    }
  }

  /**
   * Setups environment.
   *
   * @param commandRequest the given command request object.
   * @param environmentVariables the given environment variables.
   */
  public void setupEnvironment(BuildCommandRequest commandRequest, Map<String, String> environmentVariables) {
    File templateFile = getTemplateFile(commandRequest, environmentVariables);
    if (templateFile != null) {
      try {
        String targetFile = testCaseRunService.generateTestNGXml(new Function<List<AutomationMaterial>, Set<String>>(){
          @Override
          public Set<String> apply(List<AutomationMaterial> automationMaterials) {
            Set<String> automationContents = new HashSet<>();

            for (AutomationMaterial am : automationMaterials) {
              automationContents.add(am.getAutomationContent());
            }

            return automationContents;
          }
        }.apply(commandRequest.getJobDetail().getMaterials()), templateFile.getAbsolutePath(), templateFile.getParent());

        // replace original file with the new file.
        File originalFile = createFileFromOriginal(templateFile);
        FileUtils.copyFile(templateFile, originalFile);

        // replace the template file.
        File newTemplateFile = new File(targetFile);
        try {
          FileUtils.copyFile(newTemplateFile, templateFile);
        } finally {
          newTemplateFile.delete();
        }
      } catch (Exception e) {
        LOG.warn("[TestNG CommandBuilder] Could not setup environment, message: " + e.getMessage());
      }
    }
  }

  /**
   * @return the command based on request.
   */
  private CommandResponse internalBuildCommand(BuildCommandRequest commandRequest) {
    switch(commandRequest.getExecutionMode().getId()) {
      case AntExecutionMode.ID:
        return createTestNgAntTask((AntExecutionMode) commandRequest.getExecutionMode(), commandRequest);
      case MavenExecutionMode.ID:
        return createTestNgMavenTask((MavenExecutionMode) commandRequest.getExecutionMode(), commandRequest);
      default:
        return createTestNgExecTask((CommandLineExecutionMode) commandRequest.getExecutionMode(), commandRequest);
    }
  }

  /**
   * Builds the command line classpath.
   *
   * @param commandRequest the given command request.
   * @return the command line classpath.
   */
  private List<String> buildCommandLineClassPath(BuildCommandRequest commandRequest) {
    File testDirectory = new File(commandRequest.getTestScript().getTestDirectory());

    // process libraries.
    String libs = commandRequest.getTestScript().getLibraryDirectory();
    libs = (libs == null ? "" : libs.trim());


    if ("".equals(libs)) return new LinkedList<>();

    List<String> classPathLibs = new LinkedList<>();
    String[] arrLib = libs.split(",");
    for (String lib : arrLib) {
      File libFile = new File(lib);
      if (!libFile.exists()) libFile = new File(testDirectory, lib);

      if (libFile.exists()) {
        if (libFile.isDirectory()) buildDirectoryClassPath(libFile, classPathLibs);
        else classPathLibs.add(libFile.getAbsolutePath());
      }
    }

    return classPathLibs;
  }

  /**
   * Builds directory classpath.
   *
   * @param directory the given directory to build.
   * @param classPathLibs the given classpath libs to storage classpath.
   */
  private void buildDirectoryClassPath(File directory, List<String> classPathLibs) {
    classPathLibs.add(directory.getAbsolutePath() + File.separator + "*");
    File[] children = directory.listFiles();

    for (File child : children) {
      if (child.isDirectory()) buildDirectoryClassPath(child, classPathLibs);
    }
  }

  /**
   * @return the ant executive file from home.
   */
  private CommandResponse createAntTaskWithHome(CommandResponse antTask, String antHome) {
    if (StringUtils.isNotEmpty(antHome)) {
      File antBin = new File(antHome, "bin");
      if (antBin.exists()) {
        File antExec;
        if (Systems.isWindows()) {
          antExec = new File(antBin, "ant.bat");
          if (antExec.exists() && antExec.canExecute()) return antTask.withCommand(antExec.getAbsolutePath());

          antExec = new File(antBin, "ant.cmd");
          if (antExec.exists() && antExec.canExecute()) return antTask.withCommand(antExec.getAbsolutePath());
        }

        antExec = new File(antBin, "ant");
        if (antExec.exists() && antExec.canExecute()) return antTask.withCommand(antExec.getAbsolutePath());
      }
    }

    return antTask;
  }

  /**
   * Creates TestNG ant task.
   *
   * @param antExecutionMode the given ant execution mode.
   * @param commandRequest the given command request used to build.
   * @return the ant task for TestNG.
   */
  private CommandResponse createTestNgAntTask(AntExecutionMode antExecutionMode, BuildCommandRequest commandRequest) {
    CommandResponse commandResponse = new CommandResponse();
    commandResponse.withTask("ant");

    String antLogCollectorPath = getLogCollectorLibPath(TESTNG_LOG_COLLECTOR_NAME);
    String antCommandOption = antExecutionMode.getOption();

    if (antCommandOption == null) antCommandOption = "";
    antCommandOption += " -lib \"" + antLogCollectorPath + "\"";

    if (antCommandOption.indexOf("-Dbuild.sysclasspath") == -1) {
      antCommandOption += " -Dbuild.sysclasspath=first";
    }

    commandResponse.withOption(antCommandOption)
                   .withWorkingDirectory(commandRequest.getTestScript().getTestDirectory())
                   .addTaskAttribute("build_file", antExecutionMode.getBuildFile())
                   .addTaskAttribute("target", antExecutionMode.getTarget());

    return createAntTaskWithHome(commandResponse, antExecutionMode.getHome());
  }

  /**
   * Creates maven command from the given command request object.
   *
   * @param mavenExecutionMode the given execution mode.
   * @param commandRequest the build command request.
   * @return the maven command.
   */
  private CommandResponse createTestNgMavenTask(MavenExecutionMode mavenExecutionMode, BuildCommandRequest commandRequest) {
    CommandResponse commandResponse = new CommandResponse();
    commandResponse.withTask("maven");

    String mvnLogCollectorPath = getLogCollectorLibPath(TESTNG_LOG_COLLECTOR_NAME);
    String mvnCommandOption = mavenExecutionMode.getOption();

    try {
      if (StringUtils.isEmpty(mvnCommandOption)) {
        mvnCommandOption = "-fn -Dmaven.test.additionalClasspath=\"" + mvnLogCollectorPath + "\"";
      } else {
        if (mvnCommandOption.indexOf("-fn") == -1) mvnCommandOption += " -fn";
        if (mvnCommandOption.indexOf("-Dmaven.test.additionalClasspath") >= 0) {
          List<String> newArgs = new LinkedList<>();
          for (String arg : CommandLines.translateCommandLine(mvnCommandOption)) {
            if (arg.startsWith("-Dmaven.test.additionalClasspath")) {
              int equalPos = arg.indexOf("=") + 1;
              arg = arg.substring(0, equalPos)
                + mvnLogCollectorPath
                + (Systems.isWindows() ? ";" : ":")
                + arg.substring(equalPos);
            }

            newArgs.add(arg);
          }
          mvnCommandOption = CommandLines.toString(newArgs.toArray(new String[newArgs.size()]), true);
        } else {
          mvnCommandOption += " -Dmaven.test.additionalClasspath=\"" + mvnLogCollectorPath + "\"";
        }
      }

      // build maven command response.
      commandResponse.addTaskAttribute("build_file", mavenExecutionMode.getPomFile())
                     .addTaskAttribute("target", mavenExecutionMode.getGoal())
                     .withWorkingDirectory(commandRequest.getTestScript().getTestDirectory())
                     .withOption(mvnCommandOption);

      commandResponse = createMavenTaskWithHome(commandResponse, mavenExecutionMode.getHome());
    } catch (Exception e) {
      commandResponse.addError(e.getMessage());
    }

    return commandResponse;
  }

  /**
   * @return the maven task with home.
   */
  private CommandResponse createMavenTaskWithHome(CommandResponse mavenTask, String maventHome) {
    if (StringUtils.isNotEmpty(maventHome)) {
      File mvnBin = new File(maventHome, "bin");
      if (mvnBin.exists()) {
        File mvnExec;

        if (Systems.isWindows()) {
          // the mvn.bat is existing?
          mvnExec = new File(mvnBin, "mvn.bat");
          if (mvnExec.exists() && mvnExec.canExecute()) return mavenTask.withCommand(mvnExec.getAbsolutePath());

          // the mvn.cmd is existing?
          mvnExec = new File(mvnBin, "mvn.cmd");
          if (mvnExec.exists() && mvnExec.canExecute()) return mavenTask.withCommand(mvnExec.getAbsolutePath());
        }

        mvnExec = new File(mvnBin, "mvn");
        if (mvnExec.exists() && mvnExec.canExecute()) return mavenTask.withCommand(mvnExec.getAbsolutePath());
      }
    }

    return mavenTask;
  }

  /**
   * Build command line TestNG task.
   *
   * @param commandLineExecutionMode the given command line mode.
   * @param commandRequest the given command request.
   * @return the command response.
   */
  private CommandResponse createTestNgExecTask(CommandLineExecutionMode commandLineExecutionMode,
                                               BuildCommandRequest commandRequest) {
    // add class path.
    List<String> classPathLibs = buildCommandLineClassPath(commandRequest);
    classPathLibs.add(".");

    classPathLibs.add(getLogCollectorLibPath(TESTNG_LOG_COLLECTOR_NAME));
    return createTestNgExecTask(
      commandLineExecutionMode,
      commandRequest.getTestScript().getTestDirectory(),
      classPathLibs
    );
  }

  /**
   * Creates TestNg command line command.
   *
   * @param commandLineExecutionMode the given command line execution mode.
   * @param workingDir the given work directory.
   * @param classPathLibs the given class path lib.
   * @return the TestNg command line command.
   */
  private CommandResponse createTestNgExecTask(CommandLineExecutionMode commandLineExecutionMode,
                                               String workingDir,
                                               List<String> classPathLibs) {
    CommandResponse commandResponse = new CommandResponse();
    commandResponse.withTask("exec");

    try {
      String template = commandLineExecutionMode.getTemplate();

      // remove the execution point and its parameter.
      String cmdOption = commandLineExecutionMode.getOption();
      int expectOptionPos = cmdOption.indexOf("org.testng.TestNG");
      boolean hasTemplate = false;
      if (expectOptionPos >= 0) {
        cmdOption = cmdOption.substring(0, expectOptionPos);
      } else {
        expectOptionPos = cmdOption.indexOf(template);
        if (expectOptionPos >= 0) {
          hasTemplate = true;
          cmdOption = cmdOption.substring(0, expectOptionPos) + cmdOption.substring(expectOptionPos + template.length());
        }
      }

      // build command options.
      List<String> commandOptions = mergeWithAdditionalCommandOptions(
        classPathLibs,
        cmdOption
      );

      // add execution point.
      if (!hasTemplate) commandOptions.add("org.testng.TestNG");
      commandOptions.add(template);

      commandResponse.withCommand(commandLineExecutionMode.getCommand())
                     .withOption(CommandLines.toString(commandOptions.toArray(new String[commandOptions.size()]), true))
                     .withWorkingDirectory(workingDir);
    } catch (Exception e) {
      commandResponse.addError(e.getMessage());
    }

    return commandResponse;
  }

  /**
   * Merges classpath to current command options.
   *
   * @param classpathLibs the given classpath libraries.
   * @param cmdArgumentList the given command options.
   * @param cmdOptions the list of additional command options.
   * @return the command options.
   */
  private List<String> mergeWithAdditionalCommandOptions(List<String> classpathLibs,
                                                      String cmdArgumentList,
                                                      String...cmdOptions) {
    List<String> result = new LinkedList<>();

    if (cmdArgumentList == null) cmdArgumentList = "";
    List<String> cmdOptionList = new ArrayList<>(Arrays.asList(CommandLines.translateCommandLine(cmdArgumentList)));

    if (cmdArgumentList.toLowerCase().indexOf("-cp") >= 0
      || cmdArgumentList.toLowerCase().indexOf("-classpath") >= 0) {
      int classpathPos = cmdOptionList.indexOf("-cp");
      if (classpathPos == -1) {
        classpathPos = cmdOptionList.indexOf("-classpath");
      }

      // classpath lib.
      String currentClasspathLib = cmdOptionList.get(classpathPos + 1);
      String[] classpaths;
      if (Systems.isWindows() && currentClasspathLib.indexOf(";") >= 0) {
        classpaths = currentClasspathLib.split(";");
      } else if (!Systems.isWindows() && currentClasspathLib.indexOf(":") >= 0){
        classpaths = currentClasspathLib.split(":");
      } else {
        classpaths = new String[] { currentClasspathLib };
      }

      for (String classpath : classpaths) {
        classpathLibs.add(CommandLines.quoteArgument(classpath));
      }

      // remove class path out of the command option.
      cmdOptionList.remove(classpathPos);
      cmdOptionList.remove(classpathPos);
    }

    if (!classpathLibs.isEmpty()) {
      result.add("-classpath");
      result.add(Lists.join(classpathLibs, (Systems.isWindows() ? ";" : ":")));
    }

    // append the additional command options.
    if (cmdOptions.length > 0) {
      for (String cmdOption : cmdOptions) result.add(cmdOption);
    }

    // append to option.
    if (!cmdOptionList.isEmpty()) {
      for (String cmdOption : cmdOptionList) result.add(cmdOption);
    }

    return result;
  }

  /**
   * @return the log collector instance.
   */
  private String getLogCollectorLibPath(String collectorName) {
    // get the collector lib path.
    File collectorLibPath = new File(
      systemEnvironment.getUserDirectory(),
      systemEnvironment.getLogCollectorLibPath().getPath()
    );

    collectorLibPath = new File(collectorLibPath, collectorName);
    if (collectorLibPath.exists()) return collectorLibPath.getAbsolutePath();
    return "";
  }

  /**
   * @return the template file.
   */
  private File getTemplateFile(BuildCommandRequest commandRequest, Map<String, String> environmentVariables) {
    String template = commandRequest.getExecutionMode().getTemplate();
    if (template == null) {
      if (commandRequest.getExecutionMode().getId() == CommandLineExecutionMode.ID) {
        template = UUID.randomUUID().toString() + ".xml";
        environmentVariables.put("DELETE_TEMPLATE", "true");
        environmentVariables.put("TEMPLATE_FILE", template);
        commandRequest.getExecutionMode().setTemplate(template);
      } else {
        return null;
      }
    }

    File templateFile = new File(template);
    if (templateFile.exists()) return templateFile;

    templateFile = new File(commandRequest.getTestScript().getTestDirectory(), template);
    if (templateFile.exists()) return templateFile;

    try {
      templateFile.createNewFile();
      return templateFile;
    } catch (IOException ioe) {
      return null;
    }
  }

  /**
   * @return the file.
   */
  private File createFileFromOriginal(File templateFile) {
    if (templateFile.exists()) {
      String filename = templateFile.getName() + ".original";
      return new File(templateFile.getParent(), filename);
    }

    return null;
  }
}
