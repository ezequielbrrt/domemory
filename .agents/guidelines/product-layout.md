# Product Repository Layout

One product lives in one repository. This document is the single source of truth for what belongs at its root, in `marketing/`, and in `assets/`. The `product-repo-layout` skill audits a repository against it and the `product-repo-cleaner` agent migrates a repository onto it.

## Root

| Kind | Entries | Rule |
|---|---|---|
| Platform | `ios/`, `android/`, `unity/` | At least one is required. Each holds one complete platform implementation. |
| Product | `marketing/`, `assets/`, `metadata/`, `scripts/`, `localization/`, `.plans/` | Allowed. Shared by every platform. |
| Repository plumbing | `.git*`, `.github/`, `.agents/`, `.claude/`, `.codex/`, `.cursor/`, `.vscode/`, `README.md`, `CHANGELOG.md`, `CLAUDE.md`, `AGENTS.md`, `CONVENTIONS.md`, `LICENSE`, `.cursorrules`, `.mcp.json`, `.mise.toml`, `.tool-versions`, `.editorconfig` | Allowed. |
| Flagged | `firebase/`, `backend/`, `server/` | Reported for an owner decision and left in place. Moving backend configuration can break deploys. |

Anything else at the root is a violation. Git-ignored paths are local state and never part of the layout.

A repository with an Xcode, Swift package, or Tuist project at its root and no `ios/` folder uses the older single-target layout. Moving it into `ios/` is a structural migration of its own, outside this layout's cleanup.

## `marketing/`

```text
marketing/
├── README.md                     # optional
├── app-profile.json
└── campaigns/
    └── <yyyy-mm-slug>/
        ├── campaign.json
        ├── index.html            # generated from campaign.json
        └── *.md                  # optional notes
```

`marketing/` holds text only. Images, videos, and the scripts that compose them live in `assets/` and `scripts/`, and campaigns point at them by repository-relative path. That reference is what marks the media as used.

## `assets/`

```text
assets/
├── README.md, *.md               # optional documentation (art style, manifests)
├── images/<area>/                # approved stills: store art, UI, sprites, backgrounds, campaign images
├── videos/<area>/                # approved video: ads, promos, previews
├── audio/<area>/
├── animations/<area>/            # Lottie, Rive
├── fonts/
├── brand/                        # icon masters, logos, mascot, style sources
├── proposals/<yyyy-mm-slug>/     # candidates nobody has approved yet
└── archive/<yyyy-mm>/            # retired files, mirroring their original assets/ path
```

- **Media type decides the folder.** A video under `images/` or an image under `videos/` is misplaced.
- **Group by area once a folder grows**, using a short kebab-case area such as `store`, `ui`, `sprites`, `ads`, or `campaigns/<campaign-slug>`. Files may sit directly in a type folder while there are only a few.
- **Design sources** (`.psd`, `.xcf`, `.fig`, `.sketch`, `.ai`, `.afdesign`, `.blend`, …) sit beside the export they produce. Brand masters live in `brand/`.
- **No tooling.** Code, generators, and config belong in `scripts/<area>/`. Markdown documentation is allowed anywhere.
- **Proposals** may hold anything that helps someone decide (mockups, HTML prototypes, drafts). A proposal is promoted by moving its approved files into the matching type folder, and archived when it is rejected or goes stale.
- **Archive** keeps a retired file's original path under a month folder (`assets/images/ui/old.png` → `assets/archive/2026-10/images/ui/old.png`) so it can be restored by moving it back. Nothing is deleted automatically; deleting archived files is the owner's decision.

## Used versus unused

A file under `assets/images`, `videos`, `audio`, `animations`, or `fonts` is **used** when something outside `assets/archive` and `assets/proposals` references it: platform code or project files in `ios/`, `android/`, or `unity/`; a script in `scripts/`; a campaign in `marketing/`; or release material in `metadata/`. A file mentioned only in Markdown is **documented** but not used. `brand/`, `proposals/`, and `archive/` are exempt.

References are found by repository path, a containing directory path, the filename, or the name without extension and scale suffix (as asset catalogs, `R.drawable`, and sync scripts refer to it). The last two can only suggest use, and a script that builds paths at runtime defeats all four. Treat an unreferenced result as a lead to verify, never as proof.

## Naming

- Campaigns and proposals: `yyyy-mm-lowercase-kebab-slug`, dated by when the work started.
- New asset files: lowercase kebab-case. Do not rename existing files that code loads by name only to satisfy this; a rename is a code change.
- No OS metadata (`.DS_Store`, `Thumbs.db`) in the repository.
