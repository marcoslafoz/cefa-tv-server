package es.cefa.tv.core.folder.service;

import es.cefa.tv.core.authorization.model.Permission;
import es.cefa.tv.core.authorization.service.AccessService;
import es.cefa.tv.core.folder.dto.FolderRef;
import es.cefa.tv.core.folder.dto.FolderRequest;
import es.cefa.tv.core.folder.dto.FolderResponse;
import es.cefa.tv.core.folder.dto.FolderUpdateRequest;
import es.cefa.tv.core.folder.model.Folder;
import es.cefa.tv.core.folder.repository.FolderCount;
import es.cefa.tv.core.folder.repository.FolderRepository;
import es.cefa.tv.core.media.repository.MediaAssetRepository;
import es.cefa.tv.core.shared.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de las carpetas de la biblioteca de medios: árbol con nombres únicos entre hermanas,
 * profundidad limitada y sin ciclos.
 */
@Service
@RequiredArgsConstructor
public class FolderService {

  /** Profundidad máxima del árbol; una carpeta de la raíz tiene profundidad 1. */
  public static final int MAX_DEPTH = 10;

  private static final int PATH_GUARD = 64;
  private static final String PATH_SEPARATOR = " / ";

  private final FolderRepository folders;
  private final MediaAssetRepository assets;
  private final AccessService access;

  /**
   * Lista las carpetas con el número de medios y subcarpetas de cada una.
   *
   * @return las carpetas ordenadas por nombre
   */
  @Transactional(readOnly = true)
  public List<FolderResponse> list() {
    List<Folder> all = folders.findAllByOrderByNameAsc();
    Map<UUID, Long> items = itemCounts();
    Map<UUID, Long> children = new HashMap<>();
    all.stream()
        .filter(folder -> folder.getParentId() != null)
        .forEach(folder -> children.merge(folder.getParentId(), 1L, Long::sum));
    return all.stream().map(folder -> toResponse(folder, items, children)).toList();
  }

  /**
   * Devuelve la ruta de una carpeta desde la raíz, para las migas de pan.
   *
   * @param id carpeta
   * @return las carpetas desde la de primer nivel hasta la indicada
   */
  @Transactional(readOnly = true)
  public List<FolderRef> path(UUID id) {
    require(id);
    return buildPath(byId(), id);
  }

  /**
   * Crea una carpeta.
   *
   * @param request nombre y padre
   * @param username usuario que la crea
   * @return la carpeta creada
   */
  @Transactional
  public FolderResponse create(FolderRequest request, String username) {
    access.requireAnyPermission(Permission.MEDIA_MANAGE);
    String name = cleanName(request.name());
    Map<UUID, Folder> all = byId();
    if (request.parentId() != null) {
      Folder parent = requireIn(all, request.parentId());
      if (depth(all, parent.getId()) + 1 > MAX_DEPTH) {
        throw tooDeep();
      }
    }
    requireFreeName(request.parentId(), name, null);
    Folder folder = new Folder();
    folder.setId(UUID.randomUUID());
    folder.setName(name);
    folder.setParentId(request.parentId());
    folder.setCreatedAt(Instant.now());
    folder.setCreatedBy(username);
    return responseFor(folders.save(folder));
  }

  /**
   * Renombra o mueve una carpeta; su contenido y las referencias a él no cambian.
   *
   * @param id carpeta
   * @param request nuevo nombre y/o nuevo padre
   * @return la carpeta actualizada
   */
  @Transactional
  public FolderResponse update(UUID id, FolderUpdateRequest request) {
    access.requireAnyPermission(Permission.MEDIA_MANAGE);
    Folder folder = require(id);
    Map<UUID, Folder> all = byId();
    String name =
        request.name() == null || request.name().isBlank()
            ? folder.getName()
            : cleanName(request.name());
    UUID parentId = newParent(folder, request);
    if (!Objects.equals(parentId, folder.getParentId())) {
      checkMove(all, folder, parentId);
    }
    boolean changed =
        !name.equals(folder.getName()) || !Objects.equals(parentId, folder.getParentId());
    if (changed) {
      requireFreeName(parentId, name, folder.getId());
    }
    folder.setName(name);
    folder.setParentId(parentId);
    return responseFor(folder);
  }

  /**
   * Borra una carpeta.
   *
   * @param id carpeta
   * @param moveContentsToParent si es {@code true}, los medios y las subcarpetas suben a la carpeta
   *     padre en lugar de impedir el borrado
   */
  @Transactional
  public void delete(UUID id, boolean moveContentsToParent) {
    access.requireAnyPermission(Permission.MEDIA_MANAGE);
    Folder folder = require(id);
    boolean hasItems = assets.existsByFolderId(id);
    boolean hasChildren = folders.existsByParentId(id);
    if ((hasItems || hasChildren) && !moveContentsToParent) {
      throw ApiException.conflict("FOLDER_NOT_EMPTY", "La carpeta no está vacía");
    }
    UUID parentId = folder.getParentId();
    if (hasChildren) {
      requireChildrenFit(folder);
    }
    if (hasItems) {
      assets.moveFolderContents(id, parentId);
    }
    if (hasChildren) {
      folders.findByParentId(id).forEach(child -> child.setParentId(parentId));
      folders.flush();
    }
    folders.deleteById(id);
  }

  /**
   * Calcula la ruta de cada carpeta, indexada por identificador.
   *
   * @return la ruta de cada carpeta desde la raíz
   */
  @Transactional(readOnly = true)
  public Map<UUID, List<FolderRef>> paths() {
    Map<UUID, Folder> all = byId();
    Map<UUID, List<FolderRef>> result = new HashMap<>();
    all.keySet().forEach(id -> result.put(id, buildPath(all, id)));
    return result;
  }

  /**
   * Calcula la ruta de una carpeta.
   *
   * @param folderId carpeta, o {@code null} para la raíz
   * @return la ruta; vacía para la raíz
   */
  @Transactional(readOnly = true)
  public List<FolderRef> pathOf(UUID folderId) {
    if (folderId == null) {
      return List.of();
    }
    require(folderId);
    return buildPath(byId(), folderId);
  }

  /**
   * Describe una carpeta como texto, para la auditoría.
   *
   * @param folderId carpeta, o {@code null} para la raíz
   * @return los nombres separados por barras, o {@code null} para la raíz
   */
  @Transactional(readOnly = true)
  public String pathText(UUID folderId) {
    if (folderId == null) {
      return null;
    }
    return folders
        .findById(folderId)
        .map(
            folder ->
                String.join(
                    PATH_SEPARATOR,
                    buildPath(byId(), folderId).stream().map(FolderRef::name).toList()))
        .orElse(null);
  }

  /**
   * Obtiene los identificadores de una carpeta y todas sus descendientes.
   *
   * @param folderId carpeta
   * @return la carpeta y sus subcarpetas a cualquier profundidad
   */
  @Transactional(readOnly = true)
  public Set<UUID> subtree(UUID folderId) {
    require(folderId);
    return subtree(byId(), folderId);
  }

  /**
   * Exige que una carpeta exista.
   *
   * @param id carpeta
   * @return la carpeta
   */
  @Transactional(readOnly = true)
  public Folder require(UUID id) {
    return folders.findById(id).orElseThrow(() -> ApiException.notFound("Carpeta"));
  }

  /** Carga las carpetas indexadas por identificador. */
  private Map<UUID, Folder> byId() {
    Map<UUID, Folder> all = new HashMap<>();
    folders.findAllByOrderByNameAsc().forEach(folder -> all.put(folder.getId(), folder));
    return all;
  }

  /** Busca una carpeta entre las cargadas, con 404 si no existe. */
  private Folder requireIn(Map<UUID, Folder> all, UUID id) {
    Folder folder = all.get(id);
    if (folder == null) {
      throw ApiException.notFound("Carpeta");
    }
    return folder;
  }

  /** Determina el padre resultante de una modificación. */
  private UUID newParent(Folder folder, FolderUpdateRequest request) {
    if (Boolean.TRUE.equals(request.moveToRoot())) {
      return null;
    }
    return request.parentId() != null ? request.parentId() : folder.getParentId();
  }

  /**
   * Valida que una carpeta pueda colgar del nuevo padre: sin ciclo y sin pasarse de profundidad.
   */
  private void checkMove(Map<UUID, Folder> all, Folder folder, UUID parentId) {
    if (parentId == null) {
      return;
    }
    requireIn(all, parentId);
    if (subtree(all, folder.getId()).contains(parentId)) {
      throw ApiException.badRequest(
          "FOLDER_CYCLE", "Una carpeta no se puede mover dentro de sí misma ni de sus subcarpetas");
    }
    if (depth(all, parentId) + height(all, folder.getId()) > MAX_DEPTH) {
      throw tooDeep();
    }
  }

  /** Comprueba que las subcarpetas de la que se borra no choquen de nombre al subir un nivel. */
  private void requireChildrenFit(Folder folder) {
    Set<String> taken = new HashSet<>();
    siblings(folder.getParentId()).stream()
        .filter(sibling -> !sibling.getId().equals(folder.getId()))
        .forEach(sibling -> taken.add(sibling.getName().toLowerCase(Locale.ROOT)));
    for (Folder child : folders.findByParentId(folder.getId())) {
      if (!taken.add(child.getName().toLowerCase(Locale.ROOT))) {
        throw nameExists();
      }
    }
  }

  /** Exige que no haya otra carpeta con ese nombre bajo el mismo padre. */
  private void requireFreeName(UUID parentId, String name, UUID excludedId) {
    boolean taken =
        siblings(parentId).stream()
            .filter(sibling -> !sibling.getId().equals(excludedId))
            .anyMatch(sibling -> sibling.getName().equalsIgnoreCase(name));
    if (taken) {
      throw nameExists();
    }
  }

  /** Lista las carpetas que cuelgan de un padre, o de la raíz si es nulo. */
  private List<Folder> siblings(UUID parentId) {
    return parentId == null ? folders.findByParentIdIsNull() : folders.findByParentId(parentId);
  }

  /** Normaliza el nombre de una carpeta. */
  private String cleanName(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty()) {
      throw ApiException.badRequest("FOLDER_NAME_REQUIRED", "El nombre no puede estar vacío");
    }
    return name;
  }

  /** Cuenta los medios directos de cada carpeta. */
  private Map<UUID, Long> itemCounts() {
    Map<UUID, Long> counts = new HashMap<>();
    for (FolderCount count : assets.countByFolder()) {
      counts.merge(count.getFolderId(), count.getTotal(), Long::sum);
    }
    return counts;
  }

  /** Construye la respuesta de una carpeta calculando sus contadores. */
  private FolderResponse responseFor(Folder folder) {
    Map<UUID, Long> children = new HashMap<>();
    folders
        .findByParentId(folder.getId())
        .forEach(child -> children.merge(folder.getId(), 1L, Long::sum));
    return toResponse(folder, itemCounts(), children);
  }

  /** Convierte una carpeta en su DTO. */
  private static FolderResponse toResponse(
      Folder folder, Map<UUID, Long> items, Map<UUID, Long> children) {
    return new FolderResponse(
        folder.getId(),
        folder.getName(),
        folder.getParentId(),
        items.getOrDefault(folder.getId(), 0L),
        children.getOrDefault(folder.getId(), 0L),
        folder.getCreatedAt());
  }

  /** Construye la ruta de una carpeta desde la raíz. */
  private static List<FolderRef> buildPath(Map<UUID, Folder> all, UUID id) {
    List<FolderRef> path = new ArrayList<>();
    UUID current = id;
    for (int guard = 0; current != null && guard < PATH_GUARD; guard++) {
      Folder folder = all.get(current);
      if (folder == null) {
        break;
      }
      path.add(new FolderRef(folder.getId(), folder.getName()));
      current = folder.getParentId();
    }
    Collections.reverse(path);
    return path;
  }

  /** Calcula la profundidad de una carpeta; las de la raíz tienen profundidad 1. */
  private static int depth(Map<UUID, Folder> all, UUID id) {
    return buildPath(all, id).size();
  }

  /** Calcula cuántos niveles ocupa una carpeta con todo lo que cuelga de ella. */
  private static int height(Map<UUID, Folder> all, UUID id) {
    int deepest = 0;
    Set<UUID> descendants = subtree(all, id);
    for (UUID descendant : descendants) {
      deepest = Math.max(deepest, depth(all, descendant) - depth(all, id));
    }
    return deepest + 1;
  }

  /** Obtiene una carpeta y todas sus descendientes. */
  private static Set<UUID> subtree(Map<UUID, Folder> all, UUID id) {
    Map<UUID, List<UUID>> children = new HashMap<>();
    all.values()
        .forEach(
            folder ->
                children
                    .computeIfAbsent(folder.getParentId(), key -> new ArrayList<>())
                    .add(folder.getId()));
    Set<UUID> result = new LinkedHashSet<>();
    List<UUID> pending = new ArrayList<>(List.of(id));
    while (!pending.isEmpty()) {
      UUID current = pending.remove(pending.size() - 1);
      if (result.add(current)) {
        pending.addAll(children.getOrDefault(current, List.of()));
      }
    }
    return result;
  }

  /** Construye el 400 de profundidad excesiva. */
  private static ApiException tooDeep() {
    return ApiException.badRequest(
        "FOLDER_TOO_DEEP", "Las carpetas admiten como máximo " + MAX_DEPTH + " niveles");
  }

  /** Construye el 409 de nombre duplicado. */
  private static ApiException nameExists() {
    return ApiException.conflict(
        "FOLDER_NAME_EXISTS", "Ya existe una carpeta con ese nombre en esa ubicación");
  }
}
