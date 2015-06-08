package com.qasymphony.qtest.automation.testng.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.qasymphony.qtest.automation.domain.testcase.TestCase;
import com.qasymphony.qtest.automation.domain.testcase.TestStep;
import com.qasymphony.qtest.automation.util.scanner.BCELClassScanner;

/**
 * @author thongmgnguyen
 *
 */
public class TestNGClassScanner extends BCELClassScanner {
  
  public TestNGClassScanner(File templateDir) {
    super(templateDir);
  }




  private final String TESTNG_ANNOTATION_CLASS_NAME = "org.testng.annotations.Test";
  
  @Override
  protected boolean isTestMethod(JavaClass clazz, Method method) {
    Set<String> annotations = getMethodAnnotation(method);
    return annotations.contains(TESTNG_ANNOTATION_CLASS_NAME);
  }

  @Override
  protected TestCase scanClassFile(File scanFile) throws IOException, ClassNotFoundException {
    // get root classloader path and qualify class name
    String fullFilePath = scanFile.getPath();
    ClassParser parser = new ClassParser(fullFilePath);
    JavaClass jClass = parser.parse();
    
    String packageName = jClass.getPackageName();
    String className = jClass.getClassName();
    if (packageName.length() > 0) {
      className = className.substring(packageName.length() + 1);
    }
    
    List<TestStep> steps = getTestStepInfomationByAnnotation(jClass);
    TestCase testCase = null;
    if (steps.size() > 0) {
      testCase = new TestCase();
      testCase.setClassName(className);
      testCase.setTestSteps(steps);
      testCase.setPackageName(jClass.getPackageName());
      testCase.setName(jClass.getClassName());
      testCase.setContent(jClass.getClassName());
    }
    return testCase;
  }

  /**
   * get all test method in class, then build as test step.
   * 
   * @param clazz
   * @return list test step of class.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public List<TestStep> getTestStepInfomationByAnnotation(JavaClass clazz) {
    Method[] methods = clazz.getMethods();
    List<TestStep> testSteps = new ArrayList<>();
    for (Method method : methods) {
      if (isTestMethod(clazz, method) || isClassAnnotationPresent(clazz)) {
        String methodName = method.getName();
        TestStep step = new TestStep();
        step.setName(methodName);
        step.setDescription(methodName);
        testSteps.add(step);
      }
    }
    return testSteps;
  }
  
  private boolean isClassAnnotationPresent(JavaClass clazz) {
    Set<String> annotations = getClassAnnotation(clazz);
    return annotations.contains(TESTNG_ANNOTATION_CLASS_NAME);
  }
  /**
   * read testNG xml file.
   * @param scanFile
   * @return map of test class and package name as key. And value true if key name is a packgage, Otherwise, key name is class name 
   * @throws ParserConfigurationException
   * @throws SAXException
   * @throws IOException
   */
  public Map<String, Boolean> scanXmlFile(File scanFile) throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    docFactory.setIgnoringComments(true);
    docFactory.setValidating(false);
    docFactory.setIgnoringElementContentWhitespace(true);
    DocumentBuilder builder = docFactory.newDocumentBuilder();
    Document document = builder.parse(scanFile);
    NodeList classNodes = document.getElementsByTagName("class");
    NodeList packageNodes = document.getElementsByTagName("package");
    
    Map<String, Boolean> classSets = new HashMap<>();
    
    //parse xml and get tag class value put to set
    for (int i = 0; i < classNodes.getLength() ;  i++) {
      Element node = (Element) classNodes.item(i);
      String className = node.getAttribute("name");
      if (StringUtils.isNotEmpty(className)) {
        classSets.put(className, false);
      }
    }
    
    //get all tag package value and scan file content in each packge
    for (int i = 0; i < packageNodes.getLength(); i++) {
      Element node = (Element) packageNodes.item(i);
      String packageName = node.getAttribute("name");
      classSets.put(packageName, true);
    }
    
    return classSets;
  }
  
  

  
  /**
   * build testcase from testNG class 
   * @param scanDir
   * @param includePattern
   * @param excludePattern
   * @param libDirectory
   * @param isJarScan
   * @return
   * @throws Exception
   */
  @Override
  public List<TestCase> scan(String scanDir, String includePattern, String excludePattern, String libDirectory,
      boolean isJarScan) throws Exception {
    String tempIncludePattern = "**/*.class," + includePattern;
    Map<String, String> matchFiles = scanDirectory(scanDir, tempIncludePattern, excludePattern, libDirectory, isJarScan);
    Map<String, TestCase> testCases = new HashMap<>();
    List<String> xmlPackages = new ArrayList<>();
    List<String> xmlClasses = new ArrayList<>();
    // class not match pattern of user define but maybe exist in xml.
    Map<String, TestCase> tmpClasses = new HashMap<>();
    
    for (String fileName : matchFiles.keySet()) {
      
      String filePath = matchFiles.get(fileName);
      File scanFile = new File(filePath);
      
      boolean matchPattern = checkMatchPattern(includePattern, fileName);
      String extension = FilenameUtils.getExtension(fileName);
      if (matchPattern) {
        
        if (extension.equalsIgnoreCase("class")) {
          //load class file to build testcase test step information
          TestCase testCase = scanClassFile(scanFile);
          if (testCase != null) {
            testCases.put(testCase.getName(), testCase);
          }
        } else if (extension.equalsIgnoreCase("xml")) {
          //scan xml file
          Map<String, Boolean> classes = scanXmlFile(scanFile);
          for (String className : classes.keySet()) {
            if (classes.get(className)) {
              //className is a package
              xmlPackages.add(className);
            } else {
              xmlClasses.add(className);
            }
          }
        }
      } else if (!matchPattern && extension.equalsIgnoreCase("class")) {
      //load class file to build testcase test step information
        TestCase testCase = scanClassFile(scanFile);
        if (testCase != null) {
          tmpClasses.put(testCase.getName(), testCase);
        }
      }
    }
    
    //scan xml class
    for (String xmlClass : xmlClasses) {
      TestCase testCase = tmpClasses.get(xmlClass);
      if (testCase != null) {
        testCases.put(testCase.getName(), testCase);
        tmpClasses.remove(xmlClass);
      }
    }
    
    //scan package
    for (String packageName : xmlPackages) {
      for (String className : tmpClasses.keySet()) {
        if (match(className, packageName)) {
          TestCase testCase = tmpClasses.get(className);
          testCases.put(testCase.getName(), testCase);
        }
      }
    }
    
    return new ArrayList<TestCase>(testCases.values());
  }
  
  private  boolean match(String text, String pattern)
  {
    return text.matches(pattern.replace("?", ".?").replace("*", ".*?"));
  }
}
