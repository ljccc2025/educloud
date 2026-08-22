import { X } from 'lucide-react';

interface SelectedTagFilterProps {
  tag: string;
  onClear: () => void;
}

export default function SelectedTagFilter({ tag, onClear }: SelectedTagFilterProps) {
  return (
    <div className="ml-2 flex shrink-0 items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-800">
      <span>#{tag}</span>
      <button
        type="button"
        onClick={onClear}
        className="rounded-full p-0.5 text-indigo-500 transition-colors hover:bg-indigo-100 hover:text-indigo-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-700 focus-visible:ring-offset-1"
        aria-label={`清除标签：${tag}`}
      >
        <X size={13} aria-hidden="true" />
      </button>
    </div>
  );
}
