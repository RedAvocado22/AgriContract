package com.agricontract.reputation.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FrameworkBoundaryTest {
    @Test
    void applicationAndDomainHaveNoTransactionOrSpringDataDaoDependencies() throws Exception {
        var forbidden = Pattern.compile("@Transactional|TransactionTemplate|TransactionSynchronization|"
                + "org\\.springframework\\.(transaction|data|dao)");
        Path root = Path.of("src/main/java/com/agricontract/reputation");
        for (String layer : new String[]{"application", "domain"}) {
            try (var files = Files.walk(root.resolve(layer))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    assertThat(forbidden.matcher(Files.readString(file)).find()).as("D44: %s", file).isFalse();
                }
            }
        }
    }
}
