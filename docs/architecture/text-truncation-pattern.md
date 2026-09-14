# Common Components: Text Truncation Pattern

**Status**: Proposed
**Applies To**: All data grids, tables, cards, and list views displaying lengthy text content

## Overview

The **Text Truncation with Hover Tooltip** pattern provides a consistent, user-friendly way to display full-length text within space-constrained UI contexts. It ensures users can always reach the complete value through a non-intrusive hover or focus interaction while keeping layouts clean and scannable.

## Pattern Scope

### When to Apply

Use this pattern for any text field where:
- **Content may exceed the available width** (company names, descriptions, document titles)
- **Column width is constrained** to keep horizontal scrolling within limits
- **Full text matters** but UI space is limited
- **The user needs to understand truncated content** before clicking or acting on it

### Common Use Cases

- **Data grids**: entity names, descriptions, document titles
- **Tables**: member lists, role assignments, contact information
- **Card layouts**: summary sections with long labels or values
- **Search results**: abbreviated results that expand on hover

### When NOT to Apply

- **User input fields**: never truncate what a user is typing
- **Headings and titles**: display in full; redesign the layout if it does not fit
- **Guaranteed-short content**: no pattern needed below roughly 20 characters
- **Narrow viewports**: prefer wrapping to a second line over truncation on small screens

---

## Technical Specification

### CSS Implementation

```css
/* src/index.css */
.truncated-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  max-width: 100%;
  display: block;      /* or inline-block, depending on the container */
}
```

`display: block` (or `inline-block`) is not decorative — `text-overflow` has no effect on a default inline element, which is the single most common reason this pattern silently fails.

### React Component

```jsx
// src/components/truncated-text.jsx

/**
 * Renders text clipped to its container with a native tooltip carrying the full value.
 *
 * @param {object}  props
 * @param {string}  props.text          The text to display.
 * @param {string} [props.tooltipText]  Tooltip content; defaults to `text`.
 * @param {string} [props.maxWidth]     CSS max-width for the clipping box.
 * @param {string} [props.className]    Additional class names.
 */
export function TruncatedText({
  text,
  tooltipText = text,
  maxWidth = '100%',
  className = '',
}) {
  return (
    <span
      title={tooltipText}
      style={{ maxWidth }}
      className={`truncated-text ${className}`.trim()}
    >
      {text}
    </span>
  )
}
```

**Note on `style={{ maxWidth }}`**: [frontend-patterns.md STD-FE-002](frontend-patterns.md) prohibits inline styles for design-token values. `maxWidth` here is a per-instance layout constraint passed by the caller, not a token, which is the narrow exception. Everything else — overflow, ellipsis, white-space — lives in the CSS class.

### Accessibility Requirements

1. **`title` attribute** — always carries the full text, so both a mouse user and a screen reader can reach it:
   ```jsx
   <span title="Community General Liability Insurance">Community General…</span>
   ```

2. **`aria-label` for ambiguous contexts** — when the value alone is not self-describing:
   ```jsx
   <span
     title={fullText}
     aria-label={`${label}: ${fullText}`}
     className="truncated-text"
   >
     {text}
   </span>
   ```

3. **Keyboard reachability** — a native `title` tooltip does **not** appear on keyboard focus in any major browser. If the truncated value is important, the element must either be focusable with a visible focus style and a custom tooltip, or the full value must be reachable another way (expand the row, open the detail page):
   ```css
   .truncated-text:focus-visible {
     outline: 2px solid var(--color-accent);
     outline-offset: 2px;
   }
   ```

4. **Screen reader announcement** — the full text is announced via `title` (or `aria-label` when present). No further ARIA is needed for the simple case.

> **The honest limitation**: browser-native `title` tooltips are mouse-only, have an uncontrollable delay, and are unavailable on touch. They are the right default for a low-stakes value. If the truncated text is load-bearing — the only way to distinguish two rows — a real tooltip component or an alternative access path is required, not this pattern.

---

## Interaction Behavior

### Desktop

| State | Behavior |
|-------|----------|
| **Hover** | Browser-native tooltip appears after a browser-controlled delay (roughly 400–500 ms) showing the full text |
| **Focus** | The accessible name is announced; the native tooltip does **not** appear |
| **Pointer rest** | The native tooltip stays until the pointer moves or the user clicks |

### Mobile / Touch

- **Long press**: may open an OS-level tooltip; behaviour is not guaranteed
- **Tap**: no tooltip. Consider expanding the row or card on tap instead
- **Accessibility**: the full text MUST be reachable some other way (search, expanded row, detail page)

### Tooltip Styling (custom tooltip only)

- **Position**: prefer a positioning library over hand-rolled coordinates
- **Stacking**: ensure the tooltip renders above surrounding content (a portal is more reliable than a z-index arms race)
- **Duration**: visible while hovered or focused; hidden on leave, blur, or `Escape`
- **Max width**: wrap beyond roughly 300 px

---

## Data Grid Integration Example

Once a grid component exists (see [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md) Decision 4), wire the pattern into the cell renderer rather than into every consuming page:

```jsx
const columns = [
  { field: 'code',        headerName: 'Code',            width: 80 },
  {
    field: 'description',
    headerName: 'Description',
    width: 200,
    renderCell: (params) => (
      <TruncatedText text={params.value} maxWidth="200px" />
    ),
  },
  { field: 'status',      headerName: 'Status',          width: 120 },
  { field: 'effectiveDate', headerName: 'Effective Date', width: 120 },
]
```

With a plain `<table>`, apply the class to the cell and set `table-layout: fixed` — without it, the table expands to fit its content and nothing ever truncates:

```css
.data-table { table-layout: fixed; width: 100%; }
.data-table td { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
```

---

## Design Guidelines

### Column Widths

| Field type | Typical width | Approx. characters visible |
|------------|---------------|----------------------------|
| Code / ID | 80–120 px | 8–15 |
| Description | 200–250 px | 25–35 |
| Title / Name | 250–300 px | 35–45 |
| Long text | 300–400 px | 45–60 |

Character counts are approximate — they depend on the font and on the actual glyphs.

### Ellipsis

- Always use CSS `text-overflow: ellipsis`, which renders a single `…` character
- **Never** truncate in JavaScript and append `'...'` — it clips at a character count rather than at the rendered width, so it either cuts text that would have fit or overflows anyway, and it puts the wrong string in the DOM for copy-paste and search

### Visual Hierarchy

- Truncated text should not itself be the primary action target
- If an action is needed, put it in a separate column (edit button, expand icon)
- Use the same font, color, and weight as untruncated text — truncation is not a state change

---

## Accessibility Checklist

- [ ] `title` attribute contains the full text
- [ ] Text remains readable at 200% zoom
- [ ] The full value is reachable without a mouse (focusable element with a custom tooltip, or an alternative path)
- [ ] Color contrast meets WCAG AA (4.5:1 for normal text)
- [ ] A screen reader announces the full text
- [ ] No information critical to understanding is hidden behind hover alone
- [ ] Touch users have a path to the full value

---

## Testing Approach

### Manual

1. **Hover** — the tooltip appears with the full text
2. **Keyboard** — Tab to the element; confirm the full text is announced and the focus ring is visible
3. **Screen reader** — verify the announcement (VoiceOver on macOS, NVDA on Windows)
4. **Zoom** — at 200%, text stays readable and truncation still behaves
5. **Touch** — confirm the full value is reachable some other way

### Automated

```jsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TruncatedText } from './truncated-text'

describe('TruncatedText', () => {
  it('should expose the full text via the title attribute', () => {
    render(<TruncatedText text="Original full text" />)
    expect(screen.getByTitle('Original full text')).toBeInTheDocument()
  })

  it('should apply the truncation class', () => {
    render(<TruncatedText text="Very long text" />)
    expect(screen.getByTitle('Very long text')).toHaveClass('truncated-text')
  })
})
```

> **What a unit test cannot verify**: jsdom does not lay out or paint, so it cannot tell you whether the text actually clipped. Assert on the class and the `title` here, and verify the visual result with a Playwright screenshot or by hand.

---

## Browser Support

| Browser | Support | Notes |
|---------|---------|-------|
| Chrome / Edge | ✅ Full | `text-overflow: ellipsis` fully supported |
| Firefox | ✅ Full | Native tooltip behaviour reliable |
| Safari | ✅ Full | Tooltip on hover; long-press on iOS is inconsistent |
| Mobile browsers | ⚠️ Partial | CSS truncation works; tooltips largely do not |

---

## Related Standards

- [frontend-patterns.md STD-FE-001](frontend-patterns.md) — component library policy and semantic HTML
- [frontend-patterns.md STD-FE-002](frontend-patterns.md) — styling and CSS custom properties
- [testing-guide.md STD-TST-008](testing-guide.md) — query priority and `data-testid`
