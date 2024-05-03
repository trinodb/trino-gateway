# Documentation development

The Trino Gateway documentation uses markdown source files, MkDocs with the 
material theme for rendering, and GitHub pages for hosting.

The following sections contain minimal helpful tips to get started. More 
details are available in the following resources:

* [Material for MkDocs website](https://squidfunk.github.io/mkdocs-material/)
* [MkDocs website](https://www.mkdocs.org/)

## Usage with local Python

Install `python` and `pipx`, for example with brew.

Install mkdocs-material and all required dependencies:
```
pipx install --install-deps mkdocs-material
```

Start the local site in the project root folder:

```
cd trino-gateway
mkdocs serve
```

Access the site in your browser at http://127.0.0.1:8000/

Edit the site sources as desired and refresh pages as needed. Some changes 
require a restart of mkdocs.
