import { useId, useRef, useState, type ChangeEvent } from 'react';
import './FormDatePicker.css';

export interface FormDatePickerProps {
  label: string;
  /** Value in ISO format: YYYY-MM-DD, or '' when empty */
  value?: string;
  onChange?: (value: string) => void;
  name?: string;
  placeholder?: string;
  disabled?: boolean;
  min?: string;
  max?: string;
}

/** ISO (YYYY-MM-DD) -> display (MM/DD/YYYY) */
const isoToDisplay = (iso: string): string => {
  const [y, m, d] = iso.split('-');
  return y && m && d ? `${m}/${d}/${y}` : '';
};

/** display (MM/DD/YYYY) -> ISO (YYYY-MM-DD), or '' if incomplete/invalid */
const displayToIso = (display: string): string => {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(display);
  if (!match) return '';
  const [, m, d, y] = match;
  const date = new Date(`${y}-${m}-${d}T00:00:00`);
  return Number.isNaN(date.getTime()) || date.getDate() !== Number(d)
    ? ''
    : `${y}-${m}-${d}`;
};

/** Auto-insert slashes while typing: 12252025 -> 12/25/2025 */
const applyMask = (raw: string): string => {
  const digits = raw.replace(/\D/g, '').slice(0, 8);
  const parts = [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)];
  return parts.filter(Boolean).join('/');
};

export default function FormDatePicker({
  label,
  value,
  onChange,
  name,
  placeholder = '00/00/0000',
  disabled = false,
  min,
  max,
}: FormDatePickerProps) {
  const id = useId();
  const nativeRef = useRef<HTMLInputElement>(null);

  // Supports controlled (value prop) and uncontrolled usage
  const [internalIso, setInternalIso] = useState('');
  const iso = value ?? internalIso;
  const [text, setText] = useState(isoToDisplay(iso));
  const [lastIso, setLastIso] = useState(iso);

  // Keep the text in sync when the value changes from outside
  if (iso !== lastIso) {
    setLastIso(iso);
    setText(isoToDisplay(iso));
  }

  const commit = (nextIso: string) => {
    setLastIso(nextIso);
    if (value === undefined) setInternalIso(nextIso);
    onChange?.(nextIso);
  };

  const handleTextChange = (e: ChangeEvent<HTMLInputElement>) => {
    const masked = applyMask(e.target.value);
    setText(masked);
    const nextIso = displayToIso(masked);
    if (nextIso !== iso) commit(nextIso);
  };

  const handleNativeChange = (e: ChangeEvent<HTMLInputElement>) => {
    setText(isoToDisplay(e.target.value));
    commit(e.target.value);
  };

  const handleClear = () => {
    setText('');
    commit('');
  };

  const openCalendar = () => {
    const el = nativeRef.current;
    if (!el) return;
    if (typeof el.showPicker === 'function') el.showPicker();
    else el.click();
  };

  return (
    <fieldset className="form-date-picker" disabled={disabled}>
      <legend className="form-date-picker__label">
        <label htmlFor={id}>{label}</label>
      </legend>

      <div className="form-date-picker__body">
        <input
          id={id}
          className="form-date-picker__input"
          type="text"
          inputMode="numeric"
          autoComplete="off"
          placeholder={placeholder}
          value={text}
          onChange={handleTextChange}
        />

        {text && (
          <button
            type="button"
            className="form-date-picker__clear"
            onClick={handleClear}
            aria-label="Clear date"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M7 7l10 10M17 7L7 17"
                stroke="currentColor"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
            </svg>
          </button>
        )}
      </div>

      <button
        type="button"
        className="form-date-picker__calendar"
        onClick={openCalendar}
        aria-label="Open calendar"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="3" y="5" width="18" height="16" rx="2" fill="none" stroke="currentColor" strokeWidth="2" />
          <path d="M3 9h18M8 3v4M16 3v4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          {[8, 12, 16].flatMap((cx) =>
            [13, 17].map((cy) => <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="1.2" fill="currentColor" />),
          )}
        </svg>
      </button>

      {/* Hidden native input: provides the browser's calendar popup + form value */}
      <input
        ref={nativeRef}
        className="form-date-picker__native"
        type="date"
        name={name}
        value={iso}
        min={min}
        max={max}
        onChange={handleNativeChange}
        tabIndex={-1}
        aria-hidden="true"
      />
    </fieldset>
  );
}
