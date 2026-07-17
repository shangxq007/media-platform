# Generated Document Lifecycle Policy

## Rules
- generated: true in metadata
- generated_by: generator identifier
- source_inputs: input list
- generated_at: timestamp
- reproducible: boolean
- regeneration_command_or_process: how to regenerate
- do_not_edit: boolean
- retention_class: REGENERABLE

## Authority
- Generated artifact must not automatically become canonical authority
- Generated OpenAPI/schema can be executable source, but authority determined by Source-of-Truth Matrix
- Generated documentation: GENERATED/REGENERABLE by default
