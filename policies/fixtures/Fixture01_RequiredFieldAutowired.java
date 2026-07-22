package com.example.platform.fixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * FIXTURE 1: Required production @Autowired field → MUST FAIL
 * The guard must detect this as PRODUCTION_FIELD_AUTOWIRED.
 */
@Service
public class RequiredFieldAutowiredFixture {

    @Autowired
    private SomeDependency dependency;

    public void doSomething() {
        dependency.execute();
    }
}
