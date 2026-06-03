# Demo Script

> **Last Updated:** 2026-05-18

## Prerequisites

- Backend running at `http://localhost:8080`
- Frontend running at `http://localhost:3000`
- API key configured

## Step-by-Step Demo

### Step 1: Access the Application
1. Open `http://localhost:3000`
2. Verify the user dashboard loads
3. Check the navigation menu shows available features

### Step 2: Create a New Project
1. Click "New Project"
2. Enter project name: "Demo Project"
3. Verify project appears in the list

### Step 3: Open the Video Editor
1. Click on the project to open the editor
2. Verify the editor layout loads (clip library, preview, timeline, properties)

### Step 4: Upload Media
1. Click "Upload" in the clip library
2. Drag and drop a video file
3. Verify upload progress and clip appears in library

### Step 5: Add Clip to Timeline
1. Drag a clip from the library to the timeline
2. Verify the clip appears on the timeline track
3. Click the clip to select it

### Step 6: Edit Clip Properties
1. With clip selected, view properties panel
2. Adjust start time, duration
3. Verify preview updates

### Step 7: Add Subtitles
1. Open the Subtitles panel
2. Click "Add Subtitle"
3. Enter text: "Hello World"
4. Set timing: 00:00 - 00:05
5. Verify subtitle appears on timeline

### Step 8: Apply Effects
1. Open the Effects panel
2. Select an effect (e.g., "Fade In")
3. Verify effect is applied to selected clip

### Step 9: Try Demo Project
1. Open a new editor tab
2. Click "Try Demo Project"
3. Verify demo clips populate the library
4. Verify demo timeline is created

### Step 10: Export Video
1. Click "Export" button
2. Select a preset (e.g., "default_1080p")
3. Verify budget estimate is shown
4. Click "Submit Render Job"
5. Verify job ID is returned

### Step 11: Monitor Render Job
1. Verify job status shows "QUEUED"
2. Wait for status to change to "RENDERING"
3. Wait for status to change to "COMPLETED"
4. Verify artifact is displayed

### Step 12: Preview Artifact
1. Click on the completed artifact
2. Verify preview modal opens
3. Verify video plays

### Step 13: Check Capabilities
1. Navigate to `/me/capabilities`
2. Verify current tier is displayed
3. Verify available features are listed

### Step 14: Check Usage
1. Navigate to `/me/usage`
2. Verify render minutes used
3. Verify quota remaining

### Step 15: Submit Feedback
1. Navigate to `/me/feedback`
2. Select type: "Bug"
3. Enter title and description
4. Click "Submit"
5. Verify success message

### Step 16: Admin Console (Admin Only)
1. Navigate to `/admin`
2. Verify admin dashboard loads
3. Navigate to Feature Flags
4. Verify feature flag list loads

### Step 17: Feature Flag Management
1. Click on a feature flag
2. Verify flag details load
3. Toggle the flag
4. Verify change is saved

### Step 18: Policy Simulation
1. Navigate to `/admin/policies/simulate`
2. Enter a tenant ID and feature
3. Click "Simulate"
4. Verify decision chain is displayed

### Step 19: Analytics Assistant
1. Navigate to `/me/analytics`
2. Enter a natural language query
3. Verify SQL is generated
4. Execute query
5. Verify results are displayed

### Step 20: Error State
1. Trigger an error (e.g., submit with invalid data)
2. Verify error state displays with error code
3. Verify i18n message is shown
