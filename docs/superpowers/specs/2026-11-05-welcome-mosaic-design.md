# Larger welcome logo

The user chose a compact text area so everything remains visible in closed chat.
Use the original 512x241 artwork as two 256x241 bitmap glyphs. Render at height 56, ascent 50, with exact fractional spacing between slices. Each glyph must fit the native 256x256 atlas.

The decorated welcome uses a personalized localized greeting and the existing three independently clickable social links on one centered line. The player name remains present, with reduced spaces between social links. The full icon bitmap uses ascent 12 so voice copy can follow on the adjacent row; the blank footer stays below that copy. Voice installation remains a separate full sentence and sand bullet, only for clients without the mod. Preserve fallback rendering, colors, links and multi-proxy behavior.

Alternatives: a larger single glyph exceeds the native atlas; a taller message clips in closed chat. The user selected compact copy. Implementation choice: 56px height gives 40 percent more size and fits ten rows with the existing 14px icons.
