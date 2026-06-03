# Platform Spatial Coordinate System v1

## Overview

The Platform Spatial Coordinate System v1 provides a unified coordinate system for spatial operations across the platform. This system enables consistent positioning, sizing, and transformation of visual elements across different coordinate spaces and units.

## Core Principles

1. **Normalized Units**: Primary coordinate unit is `normalized_ppm` (parts per million)
2. **Top-Left Origin**: Coordinate system starts from top-left corner
3. **Flexible Spaces**: Support for source, clip, canvas, and safe-area coordinate spaces
4. **Precise Rounding**: Edge-based rounding for pixel-perfect operations
5. **Overflow Control**: Configurable overflow policies for compositing operations

## Coordinate System Specification

### Default Coordinate System
```json
{
  "unit": "normalized_ppm",
  "range": [0, 1000000],
  "origin": "top-left",
  "x_direction": "right",
  "y_direction": "down"
}
```

### Supported Coordinate Spaces

#### 1. Source Space
**Purpose**: Original media frame coordinates
**Use Cases**: 
- Crop operations
- Source region selection
- Object detection regions
- Original frame analysis

**Characteristics**:
- Represents the original input media dimensions
- Used for operations that extract from source
- Crop operations operate in this space
- Boundaries: 0 to source_width, 0 to source_height

**Example**:
```json
{
  "space": "source",
  "unit": "normalized_ppm", 
  "origin": "top-left",
  "x": 125000,      // 12.5% from left
  "y": 0,           // 0% from top
  "width": 750000,  // 75% of source width
  "height": 1000000 // 100% of source height
}
```

#### 2. Clip Space
**Purpose**: Clip-local coordinate system
**Use Cases**:
- Effects within a clip
- Local transformations
- Clip-level compositing
- Internal effect calculations

**Characteristics**:
- Represents the clip's working area
- May be different from source space due to previous operations
- Used for effects that operate on clip content
- Boundaries: 0 to clip_width, 0 to clip_height

**Example**:
```json
{
  "space": "clip",
  "unit": "normalized_ppm",
  "origin": "top-left", 
  "x": 0,
  "y": 0,
  "width": 1000000,
  "height": 1000000
}
```

#### 3. Canvas Space
**Purpose**: Output canvas coordinate system
**Use Cases**:
- Overlay positioning
- Watermark placement
- Text positioning
- Picture-in-picture positioning
- External element placement

**Characteristics**:
- Represents the final output dimensions
- Used for positioning elements within the output
- May be different from source dimensions
- Supports overflow policies for compositing

**Example**:
```json
{
  "space": "canvas",
  "unit": "normalized_ppm",
  "origin": "top-left",
  "x": 0,
  "y": 0,
  "width": 1920000,  // 1920px width in normalized_ppm
  "height": 1080000  // 1080px height in normalized_ppm
}
```

#### 4. Safe Area Space
**Purpose**: Broadcast and UI safe area coordinates
**Use Cases**:
- Subtitle placement
- UI element positioning
- Critical content placement
- Safe zone calculations

**Characteristics**:
- Represents the safe area within the canvas
- Ensures content is visible on all displays
- Typically 10% margin from edges
- Used for text and UI elements

**Example**:
```json
{
  "space": "safe-area",
  "unit": "normalized_ppm",
  "origin": "top-left",
  "x": 100000,      // 10% from left
  "y": 100000,      // 10% from top
  "width": 1800000, // 80% of canvas width
  "height": 880000  // 80% of canvas height
}
```

## Coordinate Units

### normalized_ppm (Primary Unit)
**Definition**: Parts per million (0-1,000,000)
**Precision**: Integer values for storage, float for calculations
**Conversion**: `value / 1000000.0 = normalized_value`

**Advantages**:
- High precision for pixel-perfect operations
- Easy conversion to percentages
- Consistent across different resolutions
- No floating-point precision issues

**Examples**:
```javascript
// Convert normalized_ppm to percentage
function normalizedToPercent(normalized) {
  return normalized / 10000; // 1000000 / 100 = 10000
}

// Convert percentage to normalized_ppm  
function percentToNormalized(percent) {
  return Math.round(percent * 10000);
}

// Convert normalized_ppm to pixels
function normalizedToPixels(normalized, dimension) {
  return Math.round((normalized / 1000000) * dimension);
}

// Convert pixels to normalized_ppm
function pixelsToNormalized(pixels, dimension) {
  return Math.round((pixels / dimension) * 1000000);
}
```

### Absolute Pixels (Secondary Unit)
**Definition**: Pixel coordinates
**Use Cases**: Legacy data, execution layer, UI interactions
**Conversion**: Requires target dimension for conversion

**Example**:
```json
{
  "unit": "absolute_px",
  "space": "canvas",
  "origin": "top-left",
  "x": 192,    // 192px from left
  "y": 108,   // 108px from top  
  "width": 640, // 640px width
  "height": 360 // 360px height
}
```

## Rect Model

### Basic Rect Structure
```typescript
interface Rect {
  space: 'source' | 'clip' | 'canvas' | 'safe-area';
  unit: 'normalized_ppm' | 'absolute_px';
  origin: 'top-left' | 'bottom-left' | 'top-right' | 'bottom-right';
  x: number;        // Coordinate x
  y: number;        // Coordinate y
  width: number;    // Width dimension
  height: number;   // Height dimension
}
```

### Rect Validation Rules
1. **Positive Dimensions**: width and height must be > 0
2. **Boundary Check**: x + width ≤ max_dimension, y + height ≤ max_dimension
3. **Unit Consistency**: All coordinates must use same unit
4. **Space Validity**: Must be one of the supported coordinate spaces

## Crop Parameter Model

### Crop Operation Types

#### 1. Source Region (Canonical Model)
**Description**: Direct x, y, width, height specification
**Use Cases**: Primary storage, API requests, canonical representation

```json
{
  "operation": "crop",
  "space": "source",
  "unit": "normalized_ppm",
  "sourceRegion": {
    "x": 125000,
    "y": 0,
    "width": 750000,
    "height": 1000000
  },
  "clampToFrame": true,
  "overflowPolicy": "clip"
}
```

#### 2. Edge Insets (Convenience Model)
**Description**: Top, right, bottom, left margin specification
**Use Cases**: DaVinci Resolve, Final Cut Pro, GStreamer, MLT compatibility

```json
{
  "operation": "crop",
  "space": "source", 
  "unit": "normalized_ppm",
  "edgeInsets": {
    "top": 0,
    "right": 125000,
    "bottom": 0,
    "left": 125000
  },
  "clampToFrame": true,
  "overflowPolicy": "clip"
}
```

#### 3. Absolute Pixels (Legacy Model)
**Description**: Pixel-based crop specification
**Use Cases**: Legacy data, execution layer, direct FFmpeg commands

```json
{
  "operation": "crop",
  "space": "source",
  "unit": "absolute_px",
  "sourceRegion": {
    "x": 240,
    "y": 0,
    "width": 1440,
    "height": 1080
  },
  "clampToFrame": true,
  "overflowPolicy": "clip"
}
```

### Crop Conversion Functions

#### Source Region to Edge Insets
```javascript
function sourceRegionToEdgeInsets(region, width, height) {
  return {
    top: region.y,
    right: width - (region.x + region.width),
    bottom: height - (region.y + region.height),
    left: region.x
  };
}

function edgeInsetsToSourceRegion(insets, width, height) {
  return {
    x: insets.left,
    y: insets.top,
    width: width - insets.left - insets.right,
    height: height - insets.top - insets.bottom
  };
}
```

#### Normalized_ppm to Pixels
```javascript
function normalizedToPixelRect(rect, width, height) {
  if (rect.unit === 'normalized_ppm') {
    return {
      ...rect,
      unit: 'absolute_px',
      x: Math.round((rect.x / 1000000) * width),
      y: Math.round((rect.y / 1000000) * height),
      width: Math.round((rect.width / 1000000) * width),
      height: Math.round((rect.height / 1000000) * height)
    };
  }
  return rect;
}

function pixelToNormalizedRect(rect, width, height) {
  if (rect.unit === 'absolute_px') {
    return {
      ...rect,
      unit: 'normalized_ppm',
      x: Math.round((rect.x / width) * 1000000),
      y: Math.round((rect.y / height) * 1000000),
      width: Math.round((rect.width / width) * 1000000),
      height: Math.round((rect.height / height) * 1000000)
    };
  }
  return rect;
}
```

## Rounding Policy

### Edge-Based Rounding
**Definition**: Round coordinates based on edge positions
**Purpose**: Ensure pixel-perfect operations and consistent results

```javascript
function roundNormalizedToPixels(rect, width, height) {
  const scaleX = width / 1000000;
  const scaleY = height / 1000000;
  
  const left = Math.round(rect.x * scaleX);
  const top = Math.round(rect.y * scaleY);
  const right = Math.round((rect.x + rect.width) * scaleX);
  const bottom = Math.round((rect.y + rect.height) * scaleY);
  
  const pixelWidth = Math.max(1, right - left);
  const pixelHeight = Math.max(1, bottom - top);
  
  return {
    x: left,
    y: top,
    width: pixelWidth,
    height: pixelHeight
  };
}
```

### Rounding Rules
1. **Left Edge**: Round x coordinate to nearest pixel
2. **Top Edge**: Round y coordinate to nearest pixel  
3. **Right Edge**: Round (x + width) to nearest pixel
4. **Bottom Edge**: Round (y + height) to nearest pixel
5. **Width**: Calculate as right - left, minimum 1 pixel
6. **Height**: Calculate as bottom - top, minimum 1 pixel

## Overflow Policies

### Supported Policies
1. **clip**: Crop to frame boundaries (default)
2. **allow**: Allow overflow (for special effects)
3. **reject**: Reject if overflow occurs (strict mode)

```json
{
  "operation": "crop",
  "space": "source",
  "unit": "normalized_ppm",
  "sourceRegion": {
    "x": 125000,
    "y": 0,
    "width": 750000,
    "height": 1000000
  },
  "clampToFrame": true,
  "overflowPolicy": "clip"
}
```

### Policy Examples
```javascript
// Policy: clip (default)
// Result: Content outside frame is cropped
{
  "overflowPolicy": "clip"
}

// Policy: allow  
// Result: Content outside frame is preserved
{
  "overflowPolicy": "allow"
}

// Policy: reject
// Result: Operation fails if overflow would occur
{
  "overflowPolicy": "reject"
}
```

## Implementation Examples

### Crop Effect Implementation
```typescript
interface CropEffect {
  effectKey: 'video.crop';
  parameters: {
    sourceRegion: {
      space: 'source';
      unit: 'normalized_ppm';
      x: number;
      y: number;
      width: number;
      height: number;
    };
    clampToFrame: boolean;
    overflowPolicy: 'clip' | 'allow' | 'reject';
  };
}
```

### Transform Effect Implementation
```typescript
interface TransformEffect {
  effectKey: 'video.scale';
  parameters: {
    scale: number; // Scale factor (1.0 = 100%)
    position: {
      space: 'canvas';
      unit: 'normalized_ppm';
      x: number;
      y: number;
    };
    anchor: {
      space: 'canvas';
      unit: 'normalized_ppm';
      x: number;
      y: number;
    };
  };
}
```

### Composite Effect Implementation
```typescript
interface CompositeEffect {
  effectKey: 'video.overlay';
  parameters: {
    source: {
      space: 'canvas';
      unit: 'normalized_ppm';
      x: number;
      y: number;
      width: number;
      height: number;
    };
    opacity: number; // 0.0 to 1.0
    blendMode: 'normal' | 'multiply' | 'screen' | 'overlay';
    overflowPolicy: 'clip' | 'allow';
  };
}
```

## Multi-Provider Mapping

### FFmpeg Mapping
```bash
# Crop operation
ffmpeg -i input.mp4 -vf "crop=750000:1000000:125000:0" output.mp4

# Transform operation  
ffmpeg -i input.mp4 -vf "scale=1280:720:320:180" output.mp4

# Composite operation
ffmpeg -i input.mp4 -i overlay.png -filter_complex "overlay=320:180" output.mp4
```

### GStreamer Mapping
```bash
# Crop operation
gst-launch-1.0 filesrc location=input.mp4 ! decodebin ! videoconvert ! videocrop left=125000 right=125000 ! videoconvert ! filesink location=output.mp4

# Transform operation
gst-launch-1.0 filesrc location=input.mp4 ! decodebin ! videoconvert ! videoscale method=1 ! videoconvert ! filesink location=output.mp4

# Composite operation
gst-launch-1.0 filesrc location=input.mp4 ! decodebin ! videoconvert ! filesrc location=overlay.png ! pngdec ! compositor name=mix ! videoconvert ! filesink location=output.mp4
```

### MLT Mapping
```xml
<!-- Crop operation -->
<filter id="crop">
  <property name="start">125000</property>
  <property name="end">875000</property>
  <property name="top">0</property>
  <property name="bottom">0</property>
</filter>

<!-- Transform operation -->
<filter id="scale">
  <property name="width">1280</property>
  <property name="height">720</property>
  <property name="x">320</property>
  <property name="y">180</property>
</filter>

<!-- Composite operation -->
<filter id="composite">
  <property name="x">320</property>
  <property name="y">180</property>
  <property name="opacity">1.0</property>
</filter>
```

## Validation and Testing

### Coordinate Validation
```javascript
function validateRect(rect, width, height) {
  const errors = [];
  
  if (rect.space === 'absolute_px') {
    if (rect.x < 0 || rect.y < 0) {
      errors.push('Coordinates cannot be negative');
    }
    if (rect.x + rect.width > width || rect.y + rect.height > height) {
      errors.push('Rectangle exceeds boundaries');
    }
  }
  
  if (rect.width <= 0 || rect.height <= 0) {
    errors.push('Width and height must be positive');
  }
  
  return errors;
}
```

### Crop Validation
```javascript
function validateCrop(crop, sourceWidth, sourceHeight) {
  const errors = [];
  
  // Validate sourceRegion
  if (crop.sourceRegion) {
    const regionErrors = validateRect(crop.sourceRegion, sourceWidth, sourceHeight);
    errors.push(...regionErrors);
  }
  
  // Validate edgeInsets
  if (crop.edgeInsets) {
    if (crop.edgeInsets.top < 0 || crop.edgeInsets.right < 0 || 
        crop.edgeInsets.bottom < 0 || crop.edgeInsets.left < 0) {
      errors.push('Edge insets cannot be negative');
    }
  }
  
  // Validate overflow policy
  if (!['clip', 'allow', 'reject'].includes(crop.overflowPolicy)) {
    errors.push('Invalid overflow policy');
  }
  
  return errors;
}
```

This coordinate system provides a robust foundation for spatial operations across the platform, ensuring consistency and precision in all visual effects and compositing operations.