# Local Models

JARM can import GGUF language models for on-device llama.cpp inference.

## Import

Open **Settings → Providers → Local** and import a GGUF file. JARM copies the model into app-managed storage; an optional multimodal projection file can be associated when supported. Imported models are enabled automatically and can be selected from **Settings → Models** or the chat model picker.

## Configure and use

Model-specific context and generation capability depend on the GGUF and available device memory. Large context sizes and models require more RAM and may be slow or fail on constrained devices.

## Delete

Deleting an imported local model removes JARM's managed GGUF and associated projection file. It does not delete an unrelated source file outside JARM's managed storage.

Review [Models](models.md), [Generation](generation.md), and [Privacy & Security](privacy.md). The separate Alpine sandbox is F-Droid-only, but it is not required for llama.cpp model inference.
