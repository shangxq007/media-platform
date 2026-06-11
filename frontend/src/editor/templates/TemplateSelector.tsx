export function TemplateSelector({ selected, onSelect }: {
  selected: string | null;
  onSelect: (id: string) => void;
}) {
  const templates = [
    { id: 'subtitle-basic', name: 'Basic Subtitle' },
    { id: 'tiktok-style', name: 'TikTok Style' },
  ];

  return (
    <div className="p-3 border-b border-gray-800">
      <h3 className="text-sm font-medium mb-2">Templates</h3>
      <div className="space-y-1">
        {templates.map((t) => (
          <button
            key={t.id}
            onClick={() => onSelect(t.id)}
            className={`w-full text-left text-xs px-2 py-1.5 rounded ${
              selected === t.id
                ? 'bg-blue-600 text-white'
                : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
            }`}
          >
            {t.name}
          </button>
        ))}
      </div>
    </div>
  );
}
