/**
 * Storage provider SPI (backend-neutral provider identity, capabilities, write sessions, reads).
 *
 * <p>Part of the storage module's public {@code contract} named interface, like the sibling
 * {@code contract.replica}, {@code contract.identity} and {@code contract.namespace} packages: a
 * worker-role StorageProvider implementation is an explicitly supported consumption point
 * (COVER-PROVIDER-MODULITH-001).
 */
@org.springframework.modulith.NamedInterface("contract")
package com.example.platform.storage.contract.provider;
