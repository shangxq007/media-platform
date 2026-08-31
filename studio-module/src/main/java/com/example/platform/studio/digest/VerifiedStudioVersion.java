package com.example.platform.studio.digest;
import com.example.platform.shared.digest.ContentDigest;
public final class VerifiedStudioVersion{private VerifiedStudioVersion(){}public static<T extends CanonicalStudioVersion>T verify(T version,ContentDigest providedDigest){if(version==null)throw new IllegalArgumentException("version required");StudioDigest.verify(providedDigest,version.canonicalBytes());return version;}}
