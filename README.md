# mir-alias-plugin

This MIR plugin provides human-readable alias URLs for MIR documents.

Every MIR document is addressed by its MyCoRe object ID, e.g. `https://example.org/receive/mir_mods_00000003`.
With this plugin, a document can additionally be reached via an alias, e.g. `https://example.org/go/oa/publicationsfonds`.

The alias is maintained in the admin editor form and stored as a service flag of the object. It is indexed in Solr
and resolved by a dedicated servlet.

---

## Features

The plugin offers the following features:

- Alias Resolution: Documents are reachable via `/go/{alias}`.
- Configurable Prefixes: One or more URL prefixes can be configured (default: `go`).
- Hierarchical Aliases: Aliases are combined along `mods:relatedItem` relations, e.g. `/go/{parent-alias}/{child-alias}`.
- File Access: Files of a document's derivates can be addressed via `/go/{alias}/{filename}`.
- Layout Transformation: Files matching a configurable pattern (by default `*.xml`) are rendered via the MyCoRe layout service.
- Editor Integration: An alias field is added to the MIR admin editor form, including a preview of the generated URLs.
- Access Control: Read permissions on objects and derivates are checked before content is delivered.

---

## How it works

### Basic alias resolution

The alias is entered in the admin editor form (`editor-admins.xed`) and stored as
`service/servflags/servflag[@type='alias']`. After saving, the document with alias `sozial.geschichte-online`
is available via:

```text
https://example.org/go/sozial.geschichte-online
```

Alias lookup is case-insensitive.

### Hierarchical aliases with related items

The plugin recognizes alias structures in related items. If a document with alias `child` is related
(`mods:relatedItem`) to a document with alias `parent`, it can be reached via:

```text
https://example.org/go/parent/child
```

The resolver starts with the longest matching alias of the requested path and walks down the related items until the
remaining path is fully resolved. The editor shows all URLs generated this way.

### Files in derivates

If the last path segment is not an alias, it is treated as a filename and looked up in the derivates of the resolved
document, e.g.:

```text
https://example.org/go/parent/child/index.xml
```

Files matching `MCR.Alias.Filepattern` are transformed via the layout service (the parameters `XSL.MCRObjectID` and
`XSL.MCRDerivateID` are set). All other files are delivered as is.

---

## Installation & Configuration

### Build and provide plugin

Build the JAR file with `mvn clean install` and copy it to `~/.mycore/(dev-)mir/lib/`.

### Configure `mycore.properties`

The plugin ships its default configuration, which registers the servlet, the editor extension and the Solr indexing.
Optionally, the following properties can be adjusted in `~/.mycore/(dev-)mir/mycore.properties`:

```text
# Comma-separated list of URL prefixes the alias servlet is mapped to (default: go).
# Possible patterns: {prefix}, /{prefix}, {prefix}/, /{prefix}/
MCR.Alias.prefix=publikationen/,quellen/

# Regular expression for files which are transformed via the layout service.
MCR.Alias.Filepattern=^(.+?)(\\.xml)
```

> ⚠️ Note: Changes to `MCR.Alias.prefix` require a restart of the application, because the servlet mappings are
> registered on startup.

### Update Solr schema and reindex

The plugin uses an `alias` field in the Solr `main` core and brings its own schema extension.
Therefore, the Solr configuration must be reloaded and the index rebuilt. For further information, see the
[MyCoRe Solr documentation](https://www.mycore.de/documentation/search/search_solr_use/).

Run the following commands in the MIR CLI (assuming the main core is named `main`):

```text
reload solr configuration main in core main
rebuild solr metadata and content index in core main
```

> 💡 Tip: You can check in the Solr admin UI (schema view of the core) whether the `alias` field is present.
