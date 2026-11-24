# Canonical composition semantics R1

[OD-012](../../governance/OWNER_DECISIONS.md) defines the authority. Timeline owns the static composition space. The canonical Operation path authors aspect and clip geometry with normal revision validation, compare-and-swap and provenance. A RenderJob requests an output raster. Geometry is independent of the raster used for delivery, Browser preview or source decode.

## Composition and transform

The composition is a continuous rectangle with its origin at the top left. X increases right and Y increases down. Its authored aspect is landscape 4:3 or portrait 3:4. Omission means the schema-defined landscape 4:3 composition. Moving between the two requires an explicit Timeline Operation. A raster with a different aspect is rejected. The existing default requested raster is 320×240. The bounded raster contract uses even 16–1920 pixel dimensions and a maximum of 2,073,600 pixels. The BMF adapter and sandbox reject intermediate video planes exceeding 8,294,400 pixels after scale or rotation. Independent FFmpeg currently supports the default raster in its common sequential video subset.

The `composition-transform-micro-v1` representation encodes every geometry component as a signed exact integer. One million units represent one dimensionless unit or one degree. The Operation wire, canonical JSON, canonical digest and stored physical plan use integers. The Browser input rounds to the nearest millionth with ties toward positive infinity. The canonical API rejects fractional integers, overflow, unknown fields or formats, and unsupported ranges. Negative zero has the same identity as zero.

| Field | Semantics |
|---|---|
| Position | X and Y fractions of the output rectangle, each within ±16 dimensions. Position places the top left of the rotated bounds. |
| Scale | Positive fraction of the composition width and height, each up to 10. Scaling fits the cropped source to that rectangle. |
| Rotation | Clockwise degrees in [-180, 180), about the centre of the scaled crop. |
| Crop | Half-open rectangle in source-relative coordinates. Each pair must have strictly positive width or height and remain within [0,1]. |
| Opacity | Straight alpha multiplier in [0,1]. |

Evaluation is crop, scale, rotate, position, opacity, then bottom-to-top source-over composition. Areas outside the composition are clipped. Resampling and pixel quantization happen in execution. The identity occupies the entire composition, preserves all source pixels and has unit opacity. Missing or null `transform` represents identity. Reset commits that normalized identity through the same Operation. A historical output-pixel transform object is rejected by the versioned field reader.

Source and output rasters can differ in aspect. The supported source mapping stretches the cropped source into the authored scale rectangle. This preserves the existing crop-to-canvas fit. Rotation is performed in the composition's physical aspect, with raster rounding at the native boundary.

## Basic color and alpha

The versioned `video.basic` parameter format `rgb-basic-v1` uses gamma-encoded 8-bit RGB code values from decoded SDR media. Brightness multiplies each channel by `1 + brightness`, where brightness is in [-1,1]. Contrast in [0,2] is applied around 127.5. The combined brightness/contrast stage rounds and clamps to [0,255]. Saturation in [0,2] interpolates each channel against BT.601 luma with weights 0.299, 0.587, 0.114. Grayscale sets effective saturation to zero. Final RGB values are clamped to [0,255]. These pointwise operations leave alpha unchanged. Disabled and absent effects are identity. Color processing occurs before resampling.

The authored fade and transition intervals control linear alpha. Each positive fade duration must fit the clip's authored interval. Opacity, the fade factors and the linear cross-dissolve factor multiply. A partial extent changes output-local placement and samples from the authored interval. It preserves the authored phase. The effect and geometry paths require an exact output frame grid. Intermediate decoding, color conversion, resampling and delivery encoding can produce bounded numeric differences.

The working conformance criteria are 24 code values per channel for the controlled Browser/Render and equivalent-resolution interior samples, six code values for same-raster invariance, and twelve for full/partial temporal comparisons. Edge classification uses black probes outside the transformed crop and distinct retained interior samples. These fixture criteria are applied before verification. They do not establish arbitrary media color-management or output support.
