## Icons

Ente Auth supports the icon pack provided by [simple-icons](https://github.com/simple-icons/simple-icons).

If you would like to add your own custom icon, please open a pull-request with the relevant SVG placed within `mobile/apps/auth/assets/custom-icons/icons` and add the corresponding entry within `mobile/apps/auth/assets/custom-icons/_data/custom-icons.json`. Please note icon names may only contain lowercase characters.

Please be careful to upload small and optimized icon files. Icons exceeding 20KB will not be accepted.

Note that the correspondence between the icon and the issuer is based on the name of the issuer provided by the user, excluding spaces. Only the text before the first dot "." or left parentheses "(" will be used for icon matching. e.g. Issuer name provided: "github.com (Main account)" - Then "github" will be used for matching.

This JSON file contains the following attributes:

| Attribute  | Usecase                                                                                | Required |
| ---------- | -------------------------------------------------------------------------------------- | -------- |
| `title`    | Name of the service.                                                                   | Yes      |
| `slug`     | If the icon's SVG file has a name different from the `title`                           | No       |
| `hex`      | Color code for the icon                                                                | No       |
| `altNames` | If the same service goes by different names or has different instances (e.g. Mastodon) | No       |

Here is an [example PR](https://github.com/ente/ente/pull/9121).

## Website icons

Auth can fetch a website's favicon instead of using a bundled icon. In an
account's add/edit screen, enter **Website domains**, such as
`example.com, login.example.org`, then enable **Settings → General → Use website
icons**. The setting is off by default and explains that fetching contacts the
providers and shares the requested domain and device's IP address. DuckDuckGo is
tried first, then Kagi when no usable icon is returned. The first available icon is used;
failed lookups retain the existing bundled icon.

Domains live in the entry's encrypted `CodeDisplay.domains` list and travel through
existing sync and exports. The setting is local to the device. Favicons are cached
in memory and reused across accounts sharing a domain. The cache holds up to 128
domain/list results, keeps recently used entries, and expires successful domains
after a day and failures after ten minutes. It is cleared on disable, logout or app
restart; offline after a restart, Auth uses bundled icons. Favicons are never
uploaded and do not require a server migration or object storage.
These domains can support future AutoFill matching; this feature does not enable
iOS AutoFill or add a Credential Provider extension.

The implementation lives in [`favicon_service.dart`](../lib/services/favicon_service.dart).
It requests `https://icons.duckduckgo.com/ip3/{domain}.ico`, then
`https://news.kagi.com/api/favicon-proxy?domain={domain}&quality=best` on failure.
Auth does not scrape websites or follow redirects. Downloads are limited to 2 MiB.
Up to four domains are fetched concurrently with a 20-second deadline per lookup.

PNG, JPEG, WebP and ICO images are normalized off the UI thread, selecting the
largest usable ICO frame. SVGs up to 256 KiB and 2,048 elements are rendered once
using Flutter's existing SVG renderer. SVGs must be self-contained vector artwork;
embedded images, external references and document types are unsupported.
All cached icons are PNGs no larger than 128×128 and 64 KiB. Mostly black or white
transparent artwork receives a contrasting background; switching themes requires
no extra processing. Unsupported formats and invisible images use the fallback.
