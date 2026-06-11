/**
 * Remotion Player preview skeleton.
 *
 * This is a placeholder for the actual Remotion Player integration.
 * The real implementation will use @remotion/player to render
 * compositions with caption overlays.
 */
export function RemotionPreview({ captions, templateId, isPlaying, currentTime }: {
  captions: Array<{ id: string; text: string; startTime: number; endTime: number }>;
  templateId: string | null;
  isPlaying: boolean;
  currentTime: number;
}) {
  return (
    <div className="w-full max-w-2xl aspect-video bg-gray-800 rounded-lg flex flex-col items-center justify-center border border-gray-700">
      <div className="text-center p-8">
        <div className="text-2xl font-bold text-gray-400 mb-2">Preview</div>
        <div className="text-sm text-gray-500">
          {templateId ? `Template: ${templateId}` : 'No template selected'}
        </div>
        <div className="text-sm text-gray-500 mt-1">
          Captions: {captions.length}
        </div>
        <div className="text-sm text-gray-500 mt-1">
          {isPlaying ? `Playing at ${currentTime.toFixed(1)}s` : 'Paused'}
        </div>
      </div>
    </div>
  );
}
