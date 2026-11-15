package com.agricontract.inspection;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionArchitectureTest {
    @Test
    void applicationAndDomainHaveNoTransactionOrSpringDataDaoDependencies() throws Exception {
        var root = Path.of("src/main/java/com/agricontract/inspection");
        var forbidden = Pattern.compile("@\\s*(?:[\\w$.]+\\.)?Transactional\\b"
                + "|TransactionTemplate|TransactionSynchronization"
                + "|org\\.springframework\\.(?:transaction|data|dao)\\b"
                + "|(?:jakarta|javax)\\.transaction\\b");
        var violations = new ArrayList<String>();
        for (String layer : new String[]{"application", "domain"}) {
            try (var files = Files.walk(root.resolve(layer))) {
                var sources = files.filter(path -> path.toString().endsWith(".java")).toList();
                assertThat(sources).as("production sources in %s", layer).isNotEmpty();
                for (var source : sources) {
                    var lines = Files.readAllLines(source);
                    for (int line = 0; line < lines.size(); line++) {
                        if (forbidden.matcher(lines.get(line)).find()) {
                            violations.add(source + ":" + (line + 1) + ": " + lines.get(line));
                        }
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }
}
