package es.cefa.tv.core.audit.snapshot;

import es.cefa.tv.core.folder.service.FolderService;
import es.cefa.tv.core.media.model.MediaAsset;
import es.cefa.tv.core.media.repository.MediaAssetRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Estado auditable de un medio de la biblioteca. */
@Component
@RequiredArgsConstructor
public class MediaSnapshot implements AuditSnapshotSource {

  private final MediaAssetRepository assets;
  private final FolderService folderService;

  @Override
  public String entityType() {
    return "MEDIA";
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(UUID id) {
    return assets.findById(id).map(this::describe).orElse(null);
  }

  /** Describe un medio con sus campos auditables. */
  private Map<String, Object> describe(MediaAsset asset) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("name", asset.getName());
    state.put("type", asset.getType().name());
    state.put("sizeBytes", asset.getSizeBytes());
    state.put("url", asset.getUrl());
    state.put("refreshSec", asset.getRefreshSec());
    state.put("folder", folderService.pathText(asset.getFolderId()));
    return state;
  }
}
