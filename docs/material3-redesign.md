# PocketShare Material 3 redesign

## Scope

Implemented in the Android layouts and shared styles: sharing, settings, logs,
automatic compression, account management, account editor, folder picker,
appearance/language, help, and the shared Material dialogs.

## References and constraints

- [Material 3 components](https://m3.material.io/components)
- [Google's Material 3 theming guide](https://developer.android.com/codelabs/m3-design-theming)
- [Material Theme Builder](https://github.com/material-foundation/material-theme-builder)
- Existing Figma file: https://www.figma.com/design/SStHo70h2GucsVv2VRbZGq

The Figma library query confirmed Material 3 Design Kit is attached to the existing
file. Asset search and canvas access returned the Starter plan tool-call limit;
this revision has **not** been synced to the Figma canvas. The older draft is not
an accurate preview of this implementation.

Theme Builder's semantic-role approach is used without generating a new palette.
All existing light/dark color resource files are byte-identical to their originals.
The existing optional dynamic-color setting remains available.

## Component rules

| Role | Implementation |
| --- | --- |
| Page title | Material Headline Large, normal weight, 32sp |
| Section title | Material Title Large, 22sp |
| Collapsible heading | Title Medium, 16sp, at least 56dp touch target |
| Supporting copy | Body Medium, 14sp |
| Primary action | Filled, rounded, Label Large, minimum 48dp |
| Secondary action | Outlined, rounded, Label Large, minimum 48dp |
| Navigation/settings row | Text button with trailing chevron, minimum 56dp |
| Cards | 28dp corners, 16dp content padding |
| Sharing hero | 28dp corners, 24dp content padding |
| Input field | Outlined, 28dp corners, helper/error text retained |
| Dialog | Material 3, 28dp corners, scrollable account form |
| Bottom navigation | Four destinations, minimum 80dp, 24dp icons |

## Screen decisions

- Sharing: status precedes the adjacent start/stop controls; balanced network
  metric cards; readable connection address and retained QR code.
- Settings: directory, network, access and permissions remain collapsible, with
  short summaries visible when closed. Port and speed refresh share one group.
  Save and reset remain side by side after all groups.
- Logs: compact consistent headings and quieter secondary actions; selectable
  monospaced logs retain their full content.
- Compression: monitor, locations, format/level and history use the same card,
  input and action hierarchy.
- Accounts: prominent add action above the account list; readable empty state;
  navigation rows and spaced, scrollable editing fields.
- Folder picker: vector folder icons and trailing chevrons replace text symbols;
  64dp rows with scrolling path/action header.
- Help: appearance, app information, and help/licenses are separate groups.

## Verification and limits

- Existing resource/UI checks pass, including translations and navigation IDs.
- Original view IDs and input types retained across all layouts.
- Palette byte comparison passes.
- No connected Android device or configured AVD was available for runtime visual
  checks. Device screenshots and large-font interaction checks remain unverified.
- Release output uses the existing signing key and timestamped output directory.

## Follow-up geometry

All app-defined container radii now use `@dimen/pocket_corner_radius` (28dp),
matching the sharing hero. Log and archive disclosure cards inherit the settings
card style and use the same header, 8dp outer inset and content inset. Settings
actions are ordered reset on the left and save on the right.
