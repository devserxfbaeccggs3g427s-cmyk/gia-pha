package com.familya.media.application.usecase;

import com.familya.media.application.port.in.CreateAlbumCommand;
import com.familya.media.application.port.out.AlbumRepository;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.domain.event.AlbumCreated;
import com.familya.media.domain.model.Album;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Use case tạo mới một album trong một family-tree.
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Ghi nhận metric mutation được chấp nhận.</li>
 *   <li>Tra cứu quyền của {@code actingUser} trên tree (kèm revision kỳ
 *       vọng); từ chối nếu user không có vai trò ADMIN/EDITOR.</li>
 *   <li>Sinh {@code UUID} mới cho album, khởi tạo version {@code 0},
 *       chèn vào {@code AlbumRepository}.</li>
 *   <li>Phát sự kiện {@code AlbumCreated} để search index, projection
 *       liên quan cập nhật.</li>
 * </ol>
 *
 * <p>Album vừa là tập media, vừa là target cho tham chiếu media loại
 * {@code ALBUM}.</p>
 */
@Service
public class CreateAlbumUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateAlbumUseCase.class);

    private final AlbumRepository repo;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final PlatformMetrics metrics;

    public CreateAlbumUseCase(AlbumRepository repo, MediaAuthorization authz,
                              MediaChangePublisher publisher, PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh tạo album; xem {@link CreateAlbumCommand}.
     * @return UUID album vừa tạo.
     * @throws ForbiddenException nếu user không có quyền.
     */
    @Transactional
    public UUID execute(CreateAlbumCommand cmd) {
        // Bước 1: metric đầu vào.
        metrics.mutationAccepted("media-service", "createAlbum");
        // Bước 2: kiểm tra quyền kèm revision kỳ vọng.
        MediaAuthorization.Decision d = authz.authorize(cmd.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot create album: " + d.reason());
        }
        // Bước 3: tạo aggregate Album với version=0 (mới), null tombstonedAt.
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Album album = new Album(id, cmd.treeId(), cmd.name(), cmd.description(), cmd.coverMediaId(), now, now, 0L, null);
        repo.insert(album);
        // Bước 4: phát sự kiện downstream; sequence bắt đầu từ 1.
        publisher.publish(new AlbumCreated(cmd.treeId(), id, 1L, now, cmd.name()));
        LOG.info("Created album id={} tree={} name={}", id, cmd.treeId(), cmd.name());
        return id;
    }
}
