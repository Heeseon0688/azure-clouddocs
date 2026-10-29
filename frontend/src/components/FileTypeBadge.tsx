const LABELS: Record<string, string> = { pdf: 'PDF', png: 'PNG', jpg: 'JPG', jpeg: 'JPG', txt: 'TXT' };

export function FileTypeBadge({ extension }: { extension: string }) {
  const ext = extension.toLowerCase();
  return <span className={`file-badge file-badge-${ext === 'jpeg' ? 'jpg' : ext}`}>{LABELS[ext] ?? ext.toUpperCase()}</span>;
}
