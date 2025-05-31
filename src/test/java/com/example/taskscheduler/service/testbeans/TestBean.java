package com.example.taskscheduler.service.testbeans;

import java.util.Map;

public class TestBean {
    public void doSomething() {
        System.out.println("TestBean.doSomething called");
    }

    public void doSomethingWithParams(String message, int count) {
        System.out.println("TestBean.doSomethingWithParams called with: " + message + ", " + count);
    }

    public void doSomethingWithMap(Map<String, Object> params) {
        System.out.println("TestBean.doSomethingWithMap called with: " + params);
    }

    public void doSomethingSlow() throws InterruptedException {
        Thread.sleep(100); // Keep it short for tests, but allow interruption
        System.out.println("TestBean.doSomethingSlow called and finished.");
    }

    public void throwExceptionMethod() {
        throw new RuntimeException("Test bean failure");
    }
}
