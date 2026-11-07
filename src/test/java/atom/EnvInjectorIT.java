package atom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenkinschina.atom.sdk.testing.Atom;
import com.jenkinschina.atom.sdk.testing.AtomResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The env-injector atom: a .env file becomes variables of the same name, illegal keys do not, and a secret value never
 * reaches the log. The old {@code tools/smoke.sh} made exactly these checks.
 */
class EnvInjectorIT {

    private Atom atom;

    @BeforeEach
    void setUp() throws Exception {
        atom = Atom.of();
    }

    @Test
    @DisplayName("comments, quotes and an illegal key: four legal variables, quotes stripped, the illegal key dropped")
    void legalKeysBecomeVariables() throws Exception {
        AtomResult run = atom.file(".env", """
                        # staging settings
                        APP_ENV=staging
                        DB_HOST="db.internal"
                        API_TOKEN=s3cr3t
                        not-a-key=ignored
                        EMPTY=
                        """)
                .input("{\"files\": \".env\", \"failOnMissing\": \"true\"}")
                .run();

        run.assertOk();
        assertEquals("success", run.status());
        assertEquals("staging", run.field("APP_ENV"));
        assertEquals("db.internal", run.field("DB_HOST"), "quotes must be stripped");
        assertEquals("s3cr3t", run.field("API_TOKEN"));
        assertEquals("4", run.field("count"), "three keys plus the empty one; the comment and the illegal key do not count");
        assertEquals("", run.field("not-a-key"), "an illegal key must not be exported");
        assertFalse(run.file("output.json").contains("\"not-a-key\""),
                "an illegal key must not appear in output.json:\n" + run.file("output.json"));
        assertFalse(run.console().contains("s3cr3t"), "a TOKEN value must not be echoed to the log");
    }

    @Test
    @DisplayName("a prefix keeps the names in one namespace")
    void prefix() throws Exception {
        AtomResult run = atom.file("vars.env", "REGION=eu\n")
                .input("{\"files\": \"vars.env\", \"prefix\": \"APP_\"}")
                .run();

        run.assertOk();
        assertEquals("eu", run.field("APP_REGION"), "prefix: " + run.file("output.json"));
    }

    @Test
    @DisplayName("several files are read in order, so the later value wins")
    void laterFileWins() throws Exception {
        AtomResult run = atom.file("one.env", "A=1\nB=1\n")
                .file("two.env", "B=2\n")
                .input("{\"files\": \"one.env, two.env\"}")
                .run();

        run.assertOk();
        assertEquals("2", run.field("B"), "later file must win: " + run.file("output.json"));
        assertEquals("1", run.field("A"));
    }

    @Test
    @DisplayName("a missing file is an error when failOnMissing is on")
    void missingFileFails() throws Exception {
        AtomResult run = atom.input("{\"files\": \"nope.env\", \"failOnMissing\": \"true\"}").run();

        run.assertRefused();
        assertTrue(run.file("output.json").contains("no such file"), "message not readable");
    }
}
