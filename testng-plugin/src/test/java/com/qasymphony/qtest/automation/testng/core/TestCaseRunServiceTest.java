package com.qasymphony.qtest.automation.testng.core;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class TestCaseRunServiceTest {
  
  private TestCaseRunService testCaseRunService;
  
  @Before
  public void init() {
    testCaseRunService = new TestCaseRunService();
  }

  @Test
  public void TestGeneratedXmlFile() throws Exception {
    Set<String> testRuns = new HashSet<>();
    
    testRuns.add("sample.testng.demo.HelloWord#sayHi");
    testRuns.add("sample.com.demo.HelloWord");
    
    testCaseRunService.generateTestNGXml(testRuns, "D:\\scanner TestNG\\TestNG_Sample\\TestNGHelloWord\\testng_package.xml", 
       "C:\\Users\\thongmgnguyen\\Desktop\\xml");
    
//  testCaseRunService.generateTestNGXml(testRuns, "C:\\Users\\thongmgnguyen\\Desktop\\xml");
    
  }
}
