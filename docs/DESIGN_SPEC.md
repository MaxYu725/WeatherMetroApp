# Metro design and interaction contract

## Pivot

- Page titles are English, lowercase, 52sp/light, and intentionally continue
  beyond the right edge to suggest the next page.
- The virtual pager uses `Int.MAX_VALUE`, aligned to the page count, so swiping
  in either direction appears endless.
- There is no top logo, time strip, toolbar, or persistent bottom navigation.
  System bars are hidden and can be temporarily revealed by an edge swipe.

## Tiles

- Square corners, black gutters, flat saturated accent colours and typographic
  hierarchy replace shadows, gradients and Material cards.
- Every tile's geometry is generated from its stable content seed. Coordinates
  are computed against a fixed virtual height, not the measured tile height.
  A collapsed tile clips the pattern; expansion reveals the remainder in place.
- Expanding a tile waits for its size transition and requests bring-into-view so
  its top becomes the immediate reading position. Reduced-motion mode removes
  the long transition delay.

## Alerts

- Compact warning tiles are grouped in rows of exactly four slots.
- Only one selected alert detail is open at a time.
- The full-width detail is inserted below the row containing the selected tile,
  never beside a tile or after a later row.
- HKO icons are preferred; severity colours are red, amber, green and magenta.

## Typography and accessibility

- The default interface uses a light system sans-serif approximation of Windows
  Phone typography, with large lowercase page names and concise tile labels.
- User text scale ranges from 85% to 150% through Compose density.
- High contrast raises secondary text opacity. Reduced motion shortens Pivot and
  expansion animation. Every actionable tile exposes button semantics.

## Reference images

The supplied images were used only to understand layout language, clipping,
grouping and information hierarchy. No sample screenshot is shipped as an app
asset and no pixel-for-pixel image reproduction is used.
