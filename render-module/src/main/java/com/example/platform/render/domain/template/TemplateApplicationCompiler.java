package com.example.platform.render.domain.template;

/**
 * Compiler interface for template application.
 *
 * <p>Compiles a TemplateDefinition + TemplateApplicationRequest into a
 * provider-neutral TemplateApplicationResult.</p>
 *
 * <p>Internal domain interface — does not produce FFmpeg commands
 * or Remotion props.</p>
 */
public interface TemplateApplicationCompiler {

    /**
     * Returns true if this compiler supports the given template definition.
     */
    boolean supports(TemplateDefinition definition);

    /**
     * Compile a template application into a result.
     *
     * @param definition the template definition
     * @param request    the application request
     * @return provider-neutral application result
     */
    TemplateApplicationResult compile(
            TemplateDefinition definition,
            TemplateApplicationRequest request);
}
