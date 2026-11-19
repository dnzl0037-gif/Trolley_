import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Search, X } from 'lucide-react';

export type PickerOption = { value: string; label: string; hint?: string; keywords?: string };

type Props = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: PickerOption[];
  placeholder?: string;
  searchPlaceholder?: string;
  required?: boolean;
  disabled?: boolean;
  hint?: string;
  maxVisible?: number;
  compact?: boolean;
  icon?: React.ReactNode;
  testId?: string;
};

export function SearchSelect({ label, value, onChange, options, placeholder = 'Choose an option', searchPlaceholder = 'Type to search', required, disabled, hint, maxVisible = 80, compact, icon, testId = 'picker' }: Props) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [term, setTerm] = useState('');
  const [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLUListElement>(null);
  const selected = useMemo(() => options.find(o => o.value === value), [options, value]);

  const indexed = useMemo(() => options.map(o => ({ o, text: `${o.label} ${o.value} ${o.hint ?? ''} ${o.keywords ?? ''}`.toLowerCase() })), [options]);
  const matches = useMemo(() => {
    const q = term.trim().toLowerCase();
    if (!q) return options;
    const parts = q.split(/\s+/);
    return indexed.filter(i => parts.every(p => i.text.includes(p))).map(i => i.o);
  }, [indexed, options, term]);
  const visible = matches.slice(0, maxVisible);

  useEffect(() => {
    if (!open) return;
    const close = (e: PointerEvent) => { if (root.current && !root.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('pointerdown', close);
    return () => document.removeEventListener('pointerdown', close);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    setTerm('');
    const idx = options.findIndex(o => o.value === value);
    setActive(idx >= 0 && idx < maxVisible ? idx : 0);
    requestAnimationFrame(() => search.current?.focus());
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { list.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' }); }, [active, open]);

  function pick(v: string) { onChange(v); setOpen(false); trigger.current?.focus(); }

  function onKey(e: React.KeyboardEvent) {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(a => Math.min(a + 1, visible.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(a => Math.max(a - 1, 0)); }
    else if (e.key === 'Home') { e.preventDefault(); setActive(0); }
    else if (e.key === 'End') { e.preventDefault(); setActive(Math.max(visible.length - 1, 0)); }
    else if (e.key === 'Enter') { e.preventDefault(); const o = visible[active]; if (o) pick(o.value); }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); setOpen(false); trigger.current?.focus(); }
    else if (e.key === 'Tab') setOpen(false);
  }

  const listId = `${id}-list`;
  return <div className={`picker ${compact ? 'picker-compact' : ''} ${open ? 'is-open' : ''}`} ref={root}>
    <span className="picker-label" id={`${id}-label`}>{label}{required && <span className="picker-req" aria-hidden="true"> *</span>}</span>
    <button type="button" ref={trigger} className="picker-trigger" disabled={disabled} data-testid={`button-${testId}`}
      aria-haspopup="listbox" aria-expanded={open} aria-labelledby={`${id}-label ${id}-value`}
      onClick={() => setOpen(o => !o)}
      onKeyDown={e => { if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && !open) { e.preventDefault(); setOpen(true); } }}>
      {icon && <span className="picker-icon">{icon}</span>}
      <span id={`${id}-value`} className={`picker-value ${selected ? '' : 'is-placeholder'}`}>
        {selected ? <>{selected.hint && <small>{selected.hint}</small>}<b>{selected.label}</b></> : placeholder}
      </span>
      <ChevronDown size={16} className="picker-chevron"/>
    </button>
    {required && <input className="picker-proxy" tabIndex={-1} aria-hidden="true" required value={selected ? value : ''} onChange={() => undefined}
      onInvalid={e => { e.preventDefault(); trigger.current?.focus(); setOpen(true); }}/>}
    {hint && <small className="field-hint picker-hint">{hint}</small>}
    {open && <div className="picker-pop">
      <div className="picker-search"><Search size={15}/>
        <input ref={search} role="combobox" aria-expanded="true" aria-controls={listId} aria-autocomplete="list"
          aria-activedescendant={visible[active] ? `${id}-opt-${active}` : undefined} aria-label={`Search ${label}`}
          value={term} placeholder={searchPlaceholder} onKeyDown={onKey} data-testid={`input-${testId}-search`}
          onChange={e => { setTerm(e.target.value); setActive(0); }}/>
        {term && <button type="button" className="picker-clear" aria-label="Clear search" onClick={() => { setTerm(''); search.current?.focus(); }}><X size={14}/></button>}
      </div>
      <ul className="picker-list" role="listbox" id={listId} ref={list} aria-labelledby={`${id}-label`}>
        {visible.map((o, i) => <li key={o.value} id={`${id}-opt-${i}`} data-index={i} role="option" aria-selected={o.value === value}
          className={`picker-option ${i === active ? 'is-active' : ''} ${o.value === value ? 'is-selected' : ''}`}
          onMouseMove={() => setActive(i)} onMouseDown={e => e.preventDefault()} onClick={() => pick(o.value)}>
          <span>{o.hint && <small>{o.hint}</small>}<b>{o.label}</b></span>{o.value === value && <Check size={15}/>}
        </li>)}
      </ul>
      {!matches.length && <div className="picker-empty" role="status">No matches for "{term.trim()}". Try a shorter or different word.</div>}
      <div className="picker-foot" aria-live="polite">{matches.length > visible.length ? `Showing ${visible.length} of ${matches.length.toLocaleString()} — keep typing to narrow` : `${matches.length.toLocaleString()} ${matches.length === 1 ? 'option' : 'options'}`}</div>
    </div>}
  </div>;
}
