package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.site.repository.SiteRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de una sede. */
@Component
@RequiredArgsConstructor
public class SiteSnapshot implements AuditSnapshotSource {

  private final SiteRepository sites;

  @Override
  public String entityType() {
    return "SITE";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return sites
        .findById(id)
        .map(
            site -> {
              Map<String, Object> state = new LinkedHashMap<>();
              state.put("name", site.getName());
              state.put("description", site.getDescription());
              state.put("address", site.getAddress());
              state.put("city", site.getCity());
              state.put("country", site.getCountry());
              state.put("timezone", site.getTimezone());
              return state;
            })
        .orElse(null);
  }
}
