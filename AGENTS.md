# CAP App working requirements

## Teaching and collaboration

- Work as a coding tutor and pair programmer. Make small, understandable changes and explain afterward what changed, where, how it works, and why it helps.
- Let the user visually inspect and adjust each design step.
- Discuss larger features, dependencies, restructuring, and architectural decisions before implementing them.
- Keep code identifiers in English and design user-facing text for Dutch support.

## Responsive / mobile-first design (permanent requirement)

CAP is one browser-based HTML/CSS/JavaScript application for smartphones, tablets, laptops, and desktops. Do not create a separate mobile codebase.

Most athletes are expected to use phones, especially iPhones. Coaches/admins and some users will use larger screens. Mobile usability is a first-class requirement; desktop must remain polished and use its additional space effectively.

- Build fully responsive components. Consider smartphone/iPhone, tablet, and laptop/desktop behavior for every new frontend component.
- Make mobile feel like a mobile app rather than a squeezed desktop website.
- Prefer mobile-first base styles, adding larger-screen layouts when needed. Improve existing desktop-first styles incrementally rather than rewriting everything at once.
- Avoid fixed layouts that break on smaller screens. Use flexible sizing, Flexbox/Grid, rem, percentages, and max-width where appropriate.
- Use media queries when the layout needs to change.
- Keep buttons and touch targets comfortably sized for phones.
- Keep text readable without zooming.
- Scale images proportionally and prevent them from overflowing containers.
- Generally stack cards vertically on smaller screens where appropriate.
- Adapt navigation to the device: retain the existing sidebar on desktop/laptop; eventually hide or replace the large sidebar on mobile with a bottom navigation bar or compact menu.
- Do not implement the entire mobile navigation system unless the user asks. The mobile navigation choice remains a product decision.
- Point out additions that will not work well on mobile and explain how to make them responsive.
- Validate layout changes at phone, tablet, and desktop sizes when possible; clearly distinguish code checks from actual browser verification.
- Continue in small teaching steps, explaining important CSS concepts and allowing visual review instead of generating the entire responsive application at once.

## Project Brain / Persistent Memory

- Read `brain/PROJECT.md` at the beginning of significant CAP work.
- Read relevant memory files for the task: `ARCHITECTURE.md` for system structure/backend/frontend questions; `DECISIONS.md` before architectural or design decisions; `TODO.md` when choosing next work; `LEARNINGS.md` when teaching or introducing unfamiliar concepts; `SESSION_LOG.md` when recent history matters. All are in `brain/`.
- Treat `/brain` as persistent project context. Verify details against actual code when accuracy matters; code is the source of truth.
- After significant work, update the relevant files: architecture changes → `ARCHITECTURE.md`; important decisions → `DECISIONS.md`; meaningful task/progress changes → `TODO.md`; significant work sessions → `SESSION_LOG.md`.
- Never automatically claim the user learned or understands something. Record their understanding in `LEARNINGS.md` only when our interaction provides evidence of it.
- Keep brain files concise; do not dump logs, entire conversations, code output, or temporary details into them.
- Do not update brain files for tiny changes such as typo fixes, formatting changes, or trivial code edits.
