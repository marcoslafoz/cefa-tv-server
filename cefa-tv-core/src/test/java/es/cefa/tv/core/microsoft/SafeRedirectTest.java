package es.cefa.tv.core.microsoft;

import static org.assertj.core.api.Assertions.assertThat;

import es.cefa.tv.core.microsoft.security.SafeRedirect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de la validación de destinos de redirección. */
class SafeRedirectTest {

  @ParameterizedTest
  @ValueSource(strings = {"/es/schedule", "/en/players/123?tab=metrics", "/dashboard"})
  void acceptsRelativePathsOfTheSameSite(String path) {
    assertThat(SafeRedirect.sanitizePath(path)).contains(path);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "//evil.example",
        "https://evil.example",
        "http://evil.example/es",
        "/\\evil.example",
        "evil.example",
        "/es/../../etc",
        "/es/%2f%2fevil.example",
        "/es/\nSet-Cookie: a=b",
        "javascript:alert(1)",
        ""
      })
  void rejectsAnythingThatCouldLeaveTheSite(String path) {
    assertThat(SafeRedirect.sanitizePath(path)).isEmpty();
  }

  @Test
  void rejectsTooLongPaths() {
    assertThat(SafeRedirect.sanitizePath("/" + "a".repeat(600))).isEmpty();
  }

  @Test
  void fallsBackToTheDashboardInTheRequestedLocale() {
    assertThat(SafeRedirect.targetPath("//evil.example", "en")).isEqualTo("/en/dashboard");
    assertThat(SafeRedirect.targetPath(null, "es")).isEqualTo("/es/dashboard");
  }

  @Test
  void ignoresUnknownLocalesAndUsesTheNeutralDashboard() {
    assertThat(SafeRedirect.targetPath(null, "../../x")).isEqualTo("/dashboard");
    assertThat(SafeRedirect.targetPath(null, null)).isEqualTo("/dashboard");
  }

  @Test
  void keepsAValidRequestedPathOverTheLocale() {
    assertThat(SafeRedirect.targetPath("/es/schedule", "en")).isEqualTo("/es/schedule");
  }

  @Test
  void buildsAbsoluteUrlsWithoutDoubleSlash() {
    assertThat(SafeRedirect.absolute("http://localhost:4000/", "/es/dashboard"))
        .isEqualTo("http://localhost:4000/es/dashboard");
    assertThat(SafeRedirect.absolute("https://tv.cefa.es", "/login?error=x"))
        .isEqualTo("https://tv.cefa.es/login?error=x");
  }
}
