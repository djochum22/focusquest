package com.example.focusquest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// An in-memory database, so the test never opens (or migrates) the file database a developer runs the app on.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:context-loads;DB_CLOSE_DELAY=-1")
class FocusQuestApplicationTests {

    @Test
    void contextLoads() {
    }
}
