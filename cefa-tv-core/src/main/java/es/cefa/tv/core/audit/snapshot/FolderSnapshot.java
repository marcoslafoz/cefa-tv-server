package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.folder.repository.FolderRepository;
import es.cefa.tv.core.folder.service.FolderService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de una carpeta, con la ruta de su carpeta padre. */
@Component
@RequiredArgsConstructor
public class FolderSnapshot implements AuditSnapshotSource {

  private final FolderRepository folders;
  private final FolderService folderService;

  @Override
  public String entityType() {
    return "FOLDER";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return folders
        .findById(id)
        .map(
            folder -> {
              Map<String, Object> state = new LinkedHashMap<>();
              state.put("name", folder.getName());
              state.put("parent", folderService.pathText(folder.getParentId()));
              return state;
            })
        .orElse(null);
  }
}
