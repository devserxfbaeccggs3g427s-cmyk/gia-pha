package vn.giapha.research.binarystorage.application.port.in;

import vn.giapha.research.binarystorage.domain.model.NewUploadIntent;
import vn.giapha.research.binarystorage.domain.model.UploadGrant;

/**
 * Starts the two-phase upload handshake (design.md §Upload Flow steps 1-2):
 * persist the intent with server-generated exact paths and frozen
 * constraints, then issue a single-use browser PUT capability bound to the
 * quarantine path. Called by the media and import flows (Tasks 26/29).
 */
public interface RequestUploadUseCase {

    UploadGrant requestUpload(NewUploadIntent intent);
}
