package com.agentx.coding.patch;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedDiffTest {

    @Test
    void appliesSingleHunk() {
        String diff = """
                --- a/App.java
                +++ b/App.java
                @@ -1,3 +1,3 @@
                 class App {
                -  int x = 1;
                +  int x = 2;
                 }
                """;
        var patches = UnifiedDiff.parse(diff);
        assertThat(patches).hasSize(1);
        assertThat(patches.get(0).path()).isEqualTo("App.java");

        List<String> original = List.of("class App {", "  int x = 1;", "}");
        List<String> result = UnifiedDiff.apply(original, patches.get(0));
        assertThat(result).containsExactly("class App {", "  int x = 2;", "}");
    }

    @Test
    void appliesMultipleHunks() {
        String diff = """
                --- a/f.txt
                +++ b/f.txt
                @@ -1,2 +1,2 @@
                -line1
                +LINE1
                 line2
                @@ -4,2 +4,2 @@
                 line4
                -line5
                +LINE5
                """;
        var patch = UnifiedDiff.parse(diff).get(0);
        List<String> original = List.of("line1", "line2", "line3", "line4", "line5");
        List<String> result = UnifiedDiff.apply(original, patch);
        assertThat(result).containsExactly("LINE1", "line2", "line3", "line4", "LINE5");
    }

    @Test
    void addsNewLines() {
        String diff = """
                --- a/f.txt
                +++ b/f.txt
                @@ -1,1 +1,3 @@
                 a
                +b
                +c
                """;
        var patch = UnifiedDiff.parse(diff).get(0);
        assertThat(UnifiedDiff.apply(List.of("a"), patch)).containsExactly("a", "b", "c");
        assertThat(patch.added()).isEqualTo(2);
    }

    @Test
    void rejectsMismatchedContext() {
        String diff = """
                --- a/f.txt
                +++ b/f.txt
                @@ -1,2 +1,2 @@
                 wrong context
                -x
                +y
                """;
        var patch = UnifiedDiff.parse(diff).get(0);
        assertThatThrownBy(() -> UnifiedDiff.apply(List.of("actual", "x"), patch))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parsesMultipleFiles() {
        String diff = """
                --- a/one.txt
                +++ b/one.txt
                @@ -1,1 +1,1 @@
                -a
                +A
                --- a/two.txt
                +++ b/two.txt
                @@ -1,1 +1,1 @@
                -b
                +B
                """;
        assertThat(UnifiedDiff.parse(diff)).hasSize(2)
                .extracting(UnifiedDiff.FilePatch::path)
                .containsExactly("one.txt", "two.txt");
    }
}
