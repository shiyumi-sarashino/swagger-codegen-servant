package io.swagger.codegen.languages.html;

import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.CodegenModel;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenParameter;
import io.swagger.codegen.CodegenProperty;
import io.swagger.codegen.CodegenResponse;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.SupportingFile;
import io.swagger.codegen.languages.DefaultCodegenConfig;
import io.swagger.codegen.utils.Markdown;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class StaticHtmlCodegen extends DefaultCodegenConfig implements CodegenConfig {
    protected String invokerPackage = "io.swagger.client";
    protected String groupId = "io.swagger";
    protected String artifactId = "swagger-client";
    protected String artifactVersion = "1.0.0";

    public StaticHtmlCodegen() {
        super();
        outputFolder = "docs";

        defaultIncludes = new HashSet<String>();

        cliOptions.add(new CliOption("appName", "short name of the application"));
        cliOptions.add(new CliOption("appDescription", "description of the application"));
        cliOptions.add(new CliOption("infoUrl", "a URL where users can get more information about the application"));
        cliOptions.add(new CliOption("infoEmail", "an email address to contact for inquiries about the application"));
        cliOptions.add(new CliOption("licenseInfo", "a short description of the license"));
        cliOptions.add(new CliOption("licenseUrl", "a URL pointing to the full license"));
        cliOptions.add(new CliOption(CodegenConstants.INVOKER_PACKAGE, CodegenConstants.INVOKER_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.GROUP_ID, CodegenConstants.GROUP_ID_DESC));
        cliOptions.add(new CliOption(CodegenConstants.ARTIFACT_ID, CodegenConstants.ARTIFACT_ID_DESC));
        cliOptions.add(new CliOption(CodegenConstants.ARTIFACT_VERSION, CodegenConstants.ARTIFACT_VERSION_DESC));

        additionalProperties.put("appName", "Swagger Sample");
        additionalProperties.put("appDescription", "A sample swagger server");
        additionalProperties.put("infoUrl", "https://helloreverb.com");
        additionalProperties.put("infoEmail", "hello@helloreverb.com");
        additionalProperties.put("licenseInfo", "All rights reserved");
        additionalProperties.put("licenseUrl", "http://apache.org/licenses/LICENSE-2.0.html");
        additionalProperties.put(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put(CodegenConstants.GROUP_ID, groupId);
        additionalProperties.put(CodegenConstants.ARTIFACT_ID, artifactId);
        additionalProperties.put(CodegenConstants.ARTIFACT_VERSION, artifactVersion);

        supportingFiles.add(new SupportingFile("index.mustache", "", "index.html"));
        reservedWords = new HashSet<String>();

        languageSpecificPrimitives = new HashSet<String>();
        importMapping = new HashMap<String, String>();
    }

    /**
     * Convert Markdown (CommonMark) to HTML. This class also disables normal HTML
     * escaping in the Mustache engine (see processCompiler(Compiler) above.)
     */
    @Override
    public String escapeText(String input) {
        // newline escaping disabled for HTML documentation for markdown to work correctly
        return toHtml(input);
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.DOCUMENTATION;
    }

    @Override
    public String getArgumentsLocation() {
        return "";
    }

    @Override
    public String getName() {
        return "html";
    }

    @Override
    public String getHelp() {
        return "Generates a static HTML file.";
    }

    @Override
    public String getTypeDeclaration(Schema propertySchema) {
        if (propertySchema instanceof ArraySchema) {
            Schema inner = ((ArraySchema) propertySchema).getItems();
            return String.format("%s[%s]", getSchemaType(propertySchema), getTypeDeclaration(inner));
        }
        else if (propertySchema instanceof MapSchema) {
            Schema inner = (Schema) propertySchema.getAdditionalProperties();
            return String.format("%s[String, %s]", getSchemaType(propertySchema), getTypeDeclaration(inner));
        }
        return super.getTypeDeclaration(propertySchema);
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
        for (CodegenOperation op : operationList) {
            op.httpMethod = op.httpMethod.toLowerCase();
            for (CodegenResponse response : op.responses) {
                if ("0".equals(response.code)) {
                    response.code = "default";
                }
            }
        }
        return objs;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        String templateVersion = getTemplateVersion();
        if (StringUtils.isNotBlank(templateVersion)) {
            embeddedTemplateDir = templateDir = String.format("%s/htmlDocs", templateVersion);
        }
        else {
            embeddedTemplateDir = templateDir = String.format("%s/htmlDocs", DEFAULT_TEMPLATE_VERSION);
        }
    }

    @Override
    public String escapeQuotationMark(String input) {
        // just return the original string
        return input;
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        // just return the original string
        return input;
    }

    private Markdown markdownConverter = new Markdown();

    /**
     * Convert Markdown text to HTML
     * 
     * @param input
     *            text in Markdown; may be null.
     * @return the text, converted to Markdown. For null input, "" is returned.
     */
    public String toHtml(String input) {
        if (input == null)
            return "";
        return markdownConverter.toHtml(input);
    }

    // DefaultCodegen converts model names to UpperCamelCase
    // but for static HTML, we want the names to be preserved as coded in the OpenApi
    // so HTML links work
    @Override
    public String toModelName(final String name) {
        return name;
    }

    public void preprocessOpenAPI(OpenAPI openAPI) {
        Info info = openAPI.getInfo();
        info.setDescription(toHtml(info.getDescription()));
        info.setTitle(toHtml(info.getTitle()));
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        for (Schema schema : schemas.values()) {
            schema.setDescription(toHtml(schema.getDescription()));
            schema.setTitle(toHtml(schema.getTitle()));
        }
    }

    // override to post-process any parameters
    public void postProcessParameter(CodegenParameter parameter) {
        parameter.description = toHtml(parameter.description);
        parameter.unescapedDescription = toHtml(parameter.unescapedDescription);
    }

    // override to post-process any model properties
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        property.description = toHtml(property.description);
        property.unescapedDescription = toHtml(property.unescapedDescription);
    }

}
