package vn.giapha.research.media.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.media.application.port.out.AlbumRepository;
import vn.giapha.research.media.application.port.out.BinaryMediaPort;
import vn.giapha.research.media.application.port.out.FamilyTreeRepository;
import vn.giapha.research.media.application.port.out.FileCleanupEnqueuer;
import vn.giapha.research.media.application.port.out.MediaRepository;
import vn.giapha.research.media.application.port.out.TreeAuthorizationPort;
import vn.giapha.research.media.application.port.out.TreeAuthorizationPort.Action;
import vn.giapha.research.media.shared.error.NotFoundException;
import vn.giapha.research.media.shared.error.ValidationException;
import vn.giapha.research.media.shared.id.Ids;
import vn.giapha.research.media.shared.principal.Principal;
import vn.giapha.research.media.domain.Album;
import vn.giapha.research.media.domain.FamilyTree;
import vn.giapha.research.media.domain.MediaObject;
import vn.giapha.research.media.domain.MediaStatus;

/**
 * Media and album APIs (Task 26, Req 7). Every binary mutation goes through
 * the binary-storage upload-intent lifecycle; this service handles the
 * metadata-facing CRUD (filter, detail, delete, thumbnail) and album
 * detach-on-delete.
 */
@Service
public class MediaService {

    private final FamilyTreeRepository trees;
    private final MediaRepository media;
    private final AlbumRepository albums;
    private final TreeAuthorizationPort authorization;
    private final BinaryMediaPort readService;
    private final FileCleanupEnqueuer cleanupEnqueuer;

    public MediaService(FamilyTreeRepository trees, MediaRepository media,
            AlbumRepository albums, TreeAuthorizationPort authorization,
            BinaryMediaPort readService, FileCleanupEnqueuer cleanupEnqueuer) {
        this.trees = trees;
        this.media = media;
        this.albums = albums;
        this.authorization = authorization;
        this.readService = readService;
        this.cleanupEnqueuer = cleanupEnqueuer;
    }

    @Transactional(readOnly = true)
    public List<MediaObject> list(Principal principal, String treeExternalId,
            MediaFilter filter) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return media.listAll(tree.treeKey(), filter);
    }

    @Transactional(readOnly = true)
    public MediaObject detail(Principal principal, String treeExternalId, String externalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return media.findByExternalId(tree.treeKey(), externalId)
                .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND",
                        "Media does not exist"));
    }

    @Transactional
    public void delete(Principal principal, String treeExternalId, String externalId,
            Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        MediaObject object = media.findByExternalId(tree.treeKey(), externalId)
                .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND",
                        "Media does not exist"));
        if (object.status() == MediaStatus.DELETED || object.status() == MediaStatus.DELETING) {
            return;
        }
        media.markTombstoned(tree.treeKey(), object.mediaKey(), now);
        cleanupEnqueuer.enqueueForMedia(object.mediaKey());
    }

    @Transactional(readOnly = true)
    public List<Album> listAlbums(Principal principal, String treeExternalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, false);
        return albums.listByTree(tree.treeKey());
    }

    @Transactional
    public Album createAlbum(Principal principal, String treeExternalId, String name,
            Instant now) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Album name is required");
        }
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        Album album = new Album(0, tree.treeKey(), Ids.newId(), name.trim(), null,
                1L, now, now);
        return albums.insert(album);
    }

    @Transactional
    public void deleteAlbum(Principal principal, String treeExternalId, String albumExternalId,
            Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        Album album = albums.findByExternalId(tree.treeKey(), albumExternalId)
                .orElseThrow(() -> new NotFoundException("ALBUM_NOT_FOUND",
                        "Album does not exist"));
        albums.detachMedia(album.albumKey(), now);
        albums.delete(tree.treeKey(), album.albumKey());
    }

    public BinaryMediaPort readService() {
        return readService;
    }

    private FamilyTree requireTree(String externalId) {
        return trees.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND",
                        "Tree does not exist"));
    }

    public record MediaFilter(String mimePrefix, Long albumKey, Long memberKey) {}
}
