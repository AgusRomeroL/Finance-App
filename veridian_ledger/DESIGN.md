```markdown
# Design System Specification: The Architectural Ledger

## 1. Overview & Creative North Star
**Creative North Star: The Architectural Ledger**
This design system moves away from the "app-grid" aesthetic and toward a high-end editorial experience. It treats the Pixel 9 Pro Fold’s expansive inner display as a canvas for data storytelling. By combining the precision of a professional financial ledger with the breathing room of an architectural magazine, we create a "High-End Editorial" experience. 

The system leverages the near-square aspect ratio of the Fold to implement asymmetrical "Bento" layouts. We prioritize **Tonal Layering** over structural lines, ensuring that data density does not lead to cognitive overload. Every element feels like a physical object—a slab of fine paper or a pane of frosted glass—resting within a meticulously organized space.

## 2. Colors & Surface Philosophy
The palette is rooted in `primary: #016e3e` (Accounting Green), signaling stability and growth. 

### The "No-Line" Rule
To achieve a premium feel, **1px solid borders are strictly prohibited** for sectioning or containment. Boundaries must be defined solely through:
- **Background Color Shifts:** Placing a `surface_container_lowest` card on a `surface_container_low` background.
- **Tonal Transitions:** Using depth and color blocks to imply separation.

### Surface Hierarchy & Nesting
Treat the UI as a series of nested physical layers. On the Pixel 9 Pro Fold, the "base" is the `surface`. Information is organized in containers that "lift" or "recede" based on their token:
1.  **Base Layer:** `surface` (#f9f9f9).
2.  **Structural Zones:** `surface_container_low` (#f3f4f4) for sidebars or secondary navigation.
3.  **Content Cards:** `surface_container_lowest` (#ffffff) for high-priority data modules.
4.  **Interactive Overlays:** `surface_container_highest` (#e0e3e4) for transient elements like menus.

### The "Glass & Gradient" Rule
To move beyond a flat, "out-of-the-box" Material feel:
- **CTAs:** Use a subtle linear gradient from `primary` (#016e3e) to `primary_dim` (#006035) at a 135° angle to provide visual "soul."
- **Floating Elements:** Use Glassmorphism for floating action buttons or temporary overlays. Apply a 70% opacity to the surface color with a `24px` backdrop blur to allow the dashboard colors to bleed through softly.

## 3. Typography: Roboto Flex Variable
We utilize **Roboto Flex** to exploit its variable weight axis, creating a high-contrast hierarchy that feels custom-tuned for financial clarity.

*   **Display (Editorial Impact):** `display-lg` (3.5rem) should use a `wght: 300` (Light) for large balance totals, providing an air of sophisticated wealth management.
*   **Headlines (Navigation):** `headline-sm` (1.5rem) at `wght: 600` (Semi-Bold) for section titles to ground the user.
*   **Body (Data):** `body-lg` (1rem) at `wght: 400` (Regular) for standard descriptions.
*   **Labels (The Ledger Look):** `label-md` (0.75rem) at `wght: 700` (Bold) and `ALL CAPS` with `0.05rem` letter spacing for data labels (e.g., "GROSS MARGIN," "EXPENSE TYPE"). This creates an authoritative, professional tone.

## 4. Elevation & Depth
Depth is achieved through **Tonal Layering** rather than traditional drop shadows.

*   **The Layering Principle:** Place `surface_container_lowest` components (Cards) on a `surface_container` background. The contrast between #ffffff and #edeeee provides a soft, natural lift.
*   **Ambient Shadows:** If a "floating" effect is required (e.g., a modal), use an ultra-diffused shadow: `box-shadow: 0 12px 40px rgba(0, 0, 0, 0.04)`. The shadow color must never be pure black; it should be a tinted version of `on_surface`.
*   **The "Ghost Border":** If accessibility requires a border, use the `outline_variant` token at **15% opacity**. Never use 100% opaque lines.

## 5. Components & Shape Logic
All major containers (Cards, Main Panels, Bottom Sheets) must use the **Extra-Large (28dp/1.75rem)** corner radius. Smaller elements (Buttons, Chips) use a **Full (9999px)** radius to create a "pill" contrast.

### Cards & Data Modules
- **Rule:** Forbid divider lines within cards.
- **Implementation:** Separate line items within a financial list using `8dp` or `16dp` of vertical whitespace or subtle alternating backgrounds (`surface_container_low` vs `surface_container_lowest`).
- **Interaction:** Cards should have a subtle scale-down effect (0.98x) on press to mimic physical compression.

### Buttons (The Statement Piece)
- **Primary:** Gradient fill (`primary` to `primary_dim`), `on_primary` text, 28dp radius, no shadow.
- **Secondary:** `surface_container_high` fill, `on_surface` text.
- **Tertiary/Ghost:** No fill, `primary` text weight 600.

### Input Fields
- **Style:** Use "Unfilled" style with a heavy bottom-weighted emphasis.
- **Visual:** A `surface_container_highest` background with a `2dp` bottom-only stroke using `primary` when focused. The 28dp radius applies to the top corners only for a unique "tabbed" input look.

### Financial Indicators
- **Success (Income):** `income` (#0F5A2E).
- **Error (Expense):** `expense/error` (#BA1A1A).
- **Warning:** `warning` (#8B5A00).
- These should always be accompanied by high-contrast `on_` text tokens to ensure readability against the accounting green theme.

## 6. Do's and Don'ts

### Do
- **DO** use the Fold's horizontal real estate to display a "Side-Car" navigation on the left and a "Bento-Box" dashboard on the right.
- **DO** use variable font weights to create hierarchy (e.g., a very thin balance amount next to a very bold "USD" label).
- **DO** use large amounts of "Negative Space" (minimum 24dp gutters) to allow complex financial data to breathe.

### Don't
- **DON'T** use 1px dividers to separate list items; use white space or tonal shifts.
- **DON'T** use standard Material 2 shadows. The aesthetic must feel like "Tonal Depth," not "Drop Shadows."
- **DON'T** use hard-edged corners. Every major structural element must honor the 28dp "Extra-Large" radius to maintain the fluid, modern aesthetic.
- **DON'T** clutter the inner display. Use the space for high-information density, but ensure the "Creative North Star" of editorial clarity is maintained through strict typographic alignment.