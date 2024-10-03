# Documentation development

The Trino Gateway documentation uses markdown source files, MkDocs with the 
material theme for rendering, and GitHub pages for hosting.

The following sections contain minimal helpful tips to get started. More 
details are available in the following resources:

* [Material for MkDocs website](https://squidfunk.github.io/mkdocs-material/)
* [MkDocs website](https://www.mkdocs.org/)

## Writing documentation

Content is written as markdown files in the `docs` folder.

Writing style guidelines are identical to the
[Trino documentation](https://github.com/trinodb/trino/tree/master/docs) and 
website.

Refer to the 
[reference docs from Material for MkDocs for syntax information](https://squidfunk.github.io/mkdocs-material/reference/).  

## Running site locally

Install `python` and `pipx`, for example with brew.

Install `mkdocs-material` and all required dependencies in the `mkdocs-material` 
virtual environment for Python:

```shell
pipx install --include-deps mkdocs-material 
```

Add plugins to the virtual environment:

```shell
pipx inject --include-deps mkdocs-material cairosvg
```

Start the local site in the project root folder:

```shell
cd trino-gateway
mkdocs serve
```

Access the site in your browser at http://127.0.0.1:8000/

Edit the site sources as desired and refresh pages as needed. Some changes 
require a restart of mkdocs.

Alternatively, use a container to run mkdocs and avoid the need to install 
mkdocs-material and all dependencies locally.

```shell
docker run --rm -it -v ${PWD}:/docs -p8000:8000 squidfunk/mkdocs-material 
```

## Configuring MKDocs

MkDocs is configured in `mkdocs.yml`. Refer to the source, the 
[Material for MkDocs website](https://squidfunk.github.io/mkdocs-material/) 
and the [MkDocs website](https://www.mkdocs.org/) for more information.
