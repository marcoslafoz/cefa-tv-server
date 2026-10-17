package es.cefa.tv.core.microsoft.config;

import es.cefa.tv.core.shared.config.CoreProperties;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activa la configuración de Microsoft solo cuando están rellenos el tenant (GUID), el cliente y el
 * secreto, de modo que sin credenciales reales la aplicación arranca igual y la función queda
 * apagada.
 */
public class MicrosoftEnabledCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var environment = context.getEnvironment();
    return new CoreProperties.Microsoft(
            environment.getProperty("cefa.tv.microsoft.tenant-id", ""),
            environment.getProperty("cefa.tv.microsoft.client-id", ""),
            environment.getProperty("cefa.tv.microsoft.client-secret", ""),
            "",
            false,
            "")
        .enabled();
  }
}
