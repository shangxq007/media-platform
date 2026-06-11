export function Inspector({ captions, selectedTemplate }: {
  captions: Array<{ id: string; text: string; startTime: number; endTime: number }>;
  selectedTemplate: string | null;
}) {
  return (
    <div className="p-3">
      <h3 className="text-sm font-medium mb-3">Properties</h3>
      <div className="space-y-3 text-xs">
        <div>
          <label className="text-gray-400 block mb-1">Template</label>
          <div className="text-gray-200">{selectedTemplate ?? 'None'}</div>
        </div>
        <div>
          <label className="text-gray-400 block mb-1">Captions</label>
          <div className="text-gray-200">{captions.length} caption(s)</div>
        </div>
      </div>
    </div>
  );
}
