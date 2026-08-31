package com.example.platform.studio.digest;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.shotscene.ShotSceneVersion;
import com.example.platform.studio.storyboard.StoryboardVersion;
public final class VerifiedStudioVersion{private VerifiedStudioVersion(){}public static<T>T verify(T version,ContentDigest providedDigest){if(version==null)throw new IllegalArgumentException("version required");byte[]bytes;if(version instanceof CanonicalStudioVersion canonical)bytes=canonical.canonicalBytes();else if(version instanceof StoryboardVersion storyboard)bytes=storyboard.canonicalBytes();else if(version instanceof ShotSceneVersion shotScene)bytes=shotScene.canonicalBytes();else throw new IllegalArgumentException("unsupported Studio version type");StudioDigest.verify(providedDigest,bytes);return version;}}
