package vn.giapha.research.audit.application.service;

/**
 * Manifest signer (Task 44.2). The default implementation signs with a
 * development HMAC; production wires in a KMS-backed asymmetric signer so
 * the signature can be verified offline by an auditor without sharing the
 * signing secret.
 */
public interface ManifestSigner {

    String sign(byte[] manifest);

    boolean verify(byte[] manifest, String signature);
}
