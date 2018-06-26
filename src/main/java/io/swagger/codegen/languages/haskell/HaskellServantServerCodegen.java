package io.swagger.codegen.languages.haskell;

import io.swagger.codegen.*;
import io.swagger.codegen.languages.DefaultCodegenConfig;
import io.swagger.codegen.CodegenParameter;
import io.swagger.codegen.utils.ModelUtils;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import static io.swagger.codegen.handlebars.helpers.ExtensionHelper.getBooleanValue;

import com.github.jknack.handlebars.Handlebars;

import java.util.*;
import java.util.regex.Pattern;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaskellServantServerCodegen extends DefaultCodegenConfig implements CodegenConfig {

    static Logger LOGGER = LoggerFactory.getLogger(HaskellServantServerCodegen.class);

    // source folder where to write the files
    protected String sourceFolder = "src";
    protected String apiVersion = "0.0.1";
    private static final Pattern LEADING_UNDERSCORE = Pattern.compile("^_+");

    @Override
    public String getArgumentsLocation() {
        return "";
    }
    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     * @see io.swagger.codegen.CodegenType
     */
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    /**
     * Configures a friendly name for the generator.  This will be used by the generator
     * to select the library with the -l flag.
     *
     * @return the friendly name for the generator
     */
    public String getName() {
        return "haskell";
    }

    /**
     * Returns human-friendly help for the generator.  Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    public String getHelp() {
        return "Generates a Haskell server.";
    }

    public HaskellServantServerCodegen() {
        super();

        // override the mapping to keep the original mapping in Haskell
        specialCharReplacements.put("-", "Dash");
        specialCharReplacements.put(">", "GreaterThan");
        specialCharReplacements.put("<", "LessThan");

        // backslash and double quote need double the escapement for both Java and Haskell
        specialCharReplacements.remove("\\");
        specialCharReplacements.remove("\"");
        specialCharReplacements.put("\\\\", "Back_Slash");
        specialCharReplacements.put("\\\"", "Double_Quote");

        // set the output folder here
        outputFolder = "generated-code/haskell-servant";

    /*
     * Template Location.  This is the location which templates will be read from.  The generator
     * will use the resource stream to attempt to read the templates.
     */
        embeddedTemplateDir = templateDir = "v2/haskell-servant";

    /*
     * Api Package.  Optional, if needed, this can be used in templates
     */
        apiPackage = "API";

    /*
     * Model Package.  Optional, if needed, this can be used in templates
     */
        modelPackage = "Types";

        // Haskell keywords and reserved function names, taken mostly from https://wiki.haskell.org/Keywords
        setReservedWordsLowerCase(
                Arrays.asList(
                        // Keywords
                        "as", "case", "of",
                        "class", "data", "family",
                        "default", "deriving",
                        "do", "forall", "foreign", "hiding",
                        "if", "then", "else",
                        "import", "infix", "infixl", "infixr",
                        "instance", "let", "in",
                        "mdo", "module", "newtype",
                        "proc", "qualified", "rec",
                        "type", "where"
                )
        );

    /*
     * Additional Properties.  These values can be passed to the templates and
     * are available in models, apis, and supporting files
     */
        additionalProperties.put("apiVersion", apiVersion);


    /*
     * Language Specific Primitives.  These types will not trigger imports by
     * the client generator
     */
        languageSpecificPrimitives = new HashSet<String>(
                Arrays.asList(
                        "Bool",
                        "String",
                        "Int",
                        "Integer",
                        "Float",
                        "Char",
                        "Double",
                        "List",
                        "FilePath",
			"Day",
			"POSIXTime"
                )
        );

        typeMapping.clear();
        typeMapping.put("array", "List");
        typeMapping.put("set", "Set");
        typeMapping.put("boolean", "Bool");
        typeMapping.put("string", "Text");
        typeMapping.put("int", "Int");
        typeMapping.put("long", "Integer");
        typeMapping.put("short", "Int");
        typeMapping.put("char", "Char");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Double");
        typeMapping.put("Date", "Day");
        typeMapping.put("DateTime", "POSIXTime");
        typeMapping.put("file", "FilePath");
        typeMapping.put("number", "Double");
        typeMapping.put("integer", "Int");
        typeMapping.put("any", "Value");
        typeMapping.put("UUID", "Text");
        typeMapping.put("ByteArray", "Text");

        importMapping.clear();
        importMapping.put("Map", "qualified Data.Map as Map");

        cliOptions.add(new CliOption(CodegenConstants.MODEL_PACKAGE, CodegenConstants.MODEL_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));
    }

    /**
     * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
     * those terms here.  This logic is only called if a variable matches the reserved words
     *
     * @return the escaped term
     */
    @Override
    public String escapeReservedWord(String name) {
        if(this.reservedWordsMappings().containsKey(name)) {
            return this.reservedWordsMappings().get(name);
        }
        return "_" + name;
    }

    public String firstLetterToUpper(String word) {
        if (word.length() == 0) {
            return word;
        } else if (word.length() == 1) {
            return word.substring(0, 1).toUpperCase();
        } else {
            return word.substring(0, 1).toUpperCase() + word.substring(1);
        }
    }

    public String firstLetterToLower(String word) {
        if (word.length() == 0) {
            return word;
        } else if (word.length() == 1) {
            return word.substring(0, 1).toLowerCase();
        } else {
            return word.substring(0, 1).toLowerCase() + word.substring(1);
        }
    }


    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        // From the title, compute a reasonable name for the package and the API
        String title = openAPI.getInfo().getTitle();

        // Drop any API suffix
        if(title == null) {
            title = "OpenApi";
        } else {
            title = title.trim();
            if (title.toUpperCase().endsWith("API")) {
                title = title.substring(0, title.length() - 3);
            }
        }

        String[] words = title.split(" ");

        // The package name is made by appending the lowercased words of the title interspersed with dashes
        List<String> wordsLower = new ArrayList<String>();
        for (String word : words) {
            wordsLower.add(word.toLowerCase());
        }
        String cabalName = joinStrings("-", wordsLower);

        // The API name is made by appending the capitalized words of the title
        List<String> wordsCaps = new ArrayList<String>();
        for (String word : words) {
            wordsCaps.add(firstLetterToUpper(word));
        }
        String apiName = joinStrings("", wordsCaps);

       /*
        * Supporting Files.  You can write single files for the generator with the
        * entire object tree available.  If the input file has a suffix of `.mustache
        * it will be processed by the template engine.  Otherwise, it will be copied
        */
        // Set the filenames to write for the API
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("stack.mustache", "", "stack.yaml"));
        supportingFiles.add(new SupportingFile("Setup.mustache", "", "Setup.hs"));
        supportingFiles.add(new SupportingFile("haskell-servant-codegen.mustache", "", cabalName + ".cabal"));
        supportingFiles.add(new SupportingFile("API.mustache", "src/" + apiName, "API.hs"));
        supportingFiles.add(new SupportingFile("Types.mustache", "src/" + apiName, "Types.hs"));
        supportingFiles.add(new SupportingFile("Main.mustache", "src/", "Main.hs"));


        additionalProperties.put("title", apiName);
        additionalProperties.put("titleLower", firstLetterToLower(apiName));
        additionalProperties.put("package", cabalName);

        // Due to the way servant resolves types, we need a high context stack limit
        additionalProperties.put("contextStackLimit", openAPI.getPaths().size() * 2 + 300);

        List<Map<String, Object>> replacements = new ArrayList<>();
        Object[] replacementChars = specialCharReplacements.keySet().toArray();
        for(int i = 0; i < replacementChars.length; i++) {
            String c = (String) replacementChars[i];
            Map<String, Object> o = new HashMap<>();
            o.put("char", c);
            o.put("replacement", "'" + specialCharReplacements.get(c));
            o.put("hasMore", i != replacementChars.length - 1);
            replacements.add(o);
        }
        additionalProperties.put("specialCharReplacements", replacements);

        Map<String, Schema> schemas = new HashMap<String, Schema>();
	if(openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null){
	    schemas = openAPI.getComponents().getSchemas();
	}
        if (openAPI.getPaths() != null) {
            for (String pathname : openAPI.getPaths().keySet()) {
                PathItem pathItem = openAPI.getPaths().get(pathname);
                final Operation[] operations = ModelUtils.createOperationArray(pathItem);
                for (Operation operation : operations) {
                    if (operation != null){
			if(operation.getTags() != null) {
                            operation.addExtension("x-tags", operation.getTags());
			}
		        if(operation.getOperationId()!=null){
			    // generating type annotation of responses in its description
		            String opId = firstLetterToUpper(operation.getOperationId());
	                    Map<String, ApiResponse> resps = operation.getResponses();
			    Set<String> resKeys = resps.keySet();
			    for(String key : resKeys){
			        ApiResponse resp = resps.get(key);
				String type = "";
                                if(Integer.parseInt(key)/100 == 2){
		                    type = "-TypeName ";
				}else{
		                    type = "-ErrType ";
				}
			        if(resp.getContent() != null
			            && resp.getContent().get("application/json") != null
			            && resp.getContent().get("application/json").getSchema() != null
			            && resp.getContent().get("application/json").getSchema().get$ref() != null){

			            String ref = resp.getContent().get("application/json").getSchema().get$ref();
		                    ArrayList<String> refls = new ArrayList(Arrays.asList(ref.split("/")));
		                    ref = refls.get(refls.size() - 1).toString();
                                    Schema s = schemas.get(ref);
			            if(s.getDescription() == null){
					if(type == "-TypeName "){
			                    s.setDescription(type + "Res" + opId);
					}else{
			                    s.setDescription(type + "Err" + key + opId + " -StatusCode " + key);
					}
				   }else if(!s.getDescription().contains(type)){
					if(type == "-TypeName "){
			                    s.setDescription(type + "Res" + opId + " " + s.getDescription());
					}else{
			                    s.setDescription(type + "Err" + key + opId + " -StatusCode " + key + " " + s.getDescription());
					}
			            }
				}
			    }
			    if(operation.getRequestBody() != null &&
			        operation.getRequestBody().getContent() != null &&
                                operation.getRequestBody().getContent().get("application/json") != null &&
                                operation.getRequestBody().getContent().get("application/json").getSchema() != null &&
                                operation.getRequestBody().getContent().get("application/json").getSchema().get$ref() != null){

				String ref = operation.getRequestBody().getContent().get("application/json").getSchema().get$ref();
		                ArrayList<String> refls = new ArrayList(Arrays.asList(ref.split("/")));
		                ref = refls.get(refls.size() - 1).toString();
		                Schema s = schemas.get(ref);

			       	if(s.getDescription() == null || !s.getDescription().contains("-TypeName ")){
                                    s.setDescription("-TypeName ReqBody" + opId + " " + s.getDescription());
				}
                            }
		        }
                    }
                }
            }
        }
	if (openAPI.getComponents() != null && openAPI.getComponents().getResponses() != null) {
	    Map<String, ApiResponse> apiResponses = openAPI.getComponents().getResponses();
	    List<Map<String, Object>> status = new ArrayList<>();

            for (String key : apiResponses.keySet()) {
                Map<String, Object> o = errResp2status(apiResponses.get(key), openAPI.getComponents().getSchemas());
		status.add(o);
	    }
	    additionalProperties.put("status", status);
	}

        super.preprocessOpenAPI(openAPI);
    }


    /**
     * Optional - type declaration.  This is a String which is used by the templates to instantiate your
     * types.  There is typically special handling for different property types
     *
     * @return a string value used as the `dataType` field for model templates, `returnType` for api templates
     */
    @Override
    public String getTypeDeclaration(Schema s) {
        if (s instanceof ArraySchema) {
            ArraySchema sp = (ArraySchema) s;
            Schema inner = sp.getItems();
            // return String.format("%s<%s>", getSchemaType(s), getTypeDeclaration(inner));
            return "[" + getTypeDeclaration(inner) + "]";
        } else if (s instanceof MapSchema || s.getAdditionalProperties() != null) {
            Schema inner = (Schema) s.getAdditionalProperties();
            // return getSchemaType(s) + "<String, " + getTypeDeclaration(inner) + ">";
            return "Map.Map String " + getTypeDeclaration(inner);
        }
        return fixModelChars(super.getTypeDeclaration(s));
    }


    /**
     * Optional - swagger type conversion.  This is used to map swagger types in a `Property` into
     * either language specific types via `typeMapping` or into complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     * @see io.swagger.models.properties.Property
     */
    @Override
    public String getSchemaType(Schema s) {
        String schemaType = super.getSchemaType(s);
        String type = null;
        if (typeMapping.containsKey(schemaType)) {
            type = typeMapping.get(schemaType);
            if (languageSpecificPrimitives.contains(type))
                return toModelName(type);
        } else if(schemaType == "object") {
            type = "Value";
        } else if(typeMapping.containsValue(schemaType)) {
            type = schemaType + "_";
        } else {
            type = schemaType;
        }
        return toModelName(type);
    }

    @Override
    public String toInstantiationType(Schema s) {
        if (s instanceof MapSchema) {
            MapSchema ms = (MapSchema) s;
	    String inner = getSchemaType((Schema) ms.getAdditionalProperties());
            return inner;
        } else if (s instanceof ArraySchema) {
            ArraySchema as = (ArraySchema) s;
            String inner = getSchemaType(as.getItems());
            // Return only the inner type; the wrapping with QueryList is done
            // somewhere else, where we have access to the collection format.
	    return "[" + inner + "]";
        } else {
            return null;
        }
    }


    // Intersperse a separator string between a list of strings, like String.join.
    private String joinStrings(String sep, List<String> ss) {
        StringBuilder sb = new StringBuilder();
        for (String s : ss) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(s);
        }
        return sb.toString();
    }

    // Convert an HTTP path to a Servant route, including captured parameters.
    // For example, the path /api/jobs/info/{id}/last would become:
    //      "api" :> "jobs" :> "info" :> Capture "id" IdType :> "last"
    // IdType is provided by the capture params.
    private List<String> pathToServantRoute(String path, List<CodegenParameter> params) {
        // Map the capture params by their names.
        HashMap<String, String> captureTypes = new HashMap<String, String>();
        for (CodegenParameter param : params) {
            captureTypes.put(param.baseName, param.dataType);
        }

        // Cut off the leading slash, if it is present.
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Convert the path into a list of servant route components.
        List<String> pathComponents = new ArrayList<String>();
        for (String piece : path.split("/")) {
            if (piece.startsWith("{") && piece.endsWith("}")) {
                String name = piece.substring(1, piece.length() - 1);
		String capType = captureTypes.get(name);
                pathComponents.add("Capture \"" + name + "\" " + capType);
            } else {
                pathComponents.add("\"" + piece + "\"");
            }
        }

        // Intersperse the servant route pieces with :> to construct the final API type
        return pathComponents;
    }
    private List<String> pathToFuncType(String path, List<CodegenParameter> params) {
        // Map the capture params by their names.
        HashMap<String, String> captureTypes = new HashMap<String, String>();
        for (CodegenParameter param : params) {
            captureTypes.put(param.baseName, param.dataType);
        }

        // Cut off the leading slash, if it is present.
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Convert the path into a list of servant route components.
        List<String> capType = new ArrayList<String>();
        for (String piece : path.split("/")) {
            if (piece.startsWith("{") && piece.endsWith("}")) {
                String name = piece.substring(1, piece.length() - 1);
		String captureType = captureTypes.get(name);
                capType.add(captureType);
            }
        }

        // Intersperse the servant route pieces with :> to construct the final API type
        return capType;
    }
    
    private String descriptionToErrType(String desc){
        List<String> ss = new ArrayList<>(Arrays.asList(desc.split(" ")));
        Integer size = ss.size();
	String errType = "";
        for(Integer i=0; i<size; i++){
            if(ss.get(i).equals("-ErrType") && ss.get(i+1) != null){
                errType = ss.get(i+1);
        	    if(errType.equals("ad-hoc") && ss.get(i+2) != null){
        	        errType = ss.get(i+2);
        	    }
        	break;
            }
        }
	return firstLetterToUpper(errType);
    }
    private Map<String, Object> errResp2status(ApiResponse apiResponse,Map<String, Schema> schemas ){
        Map<String, Object> o = new HashMap<>();
        Schema schema = new Schema();
        if( apiResponse.getContent()!=null
         && apiResponse.getContent().get("application/json")!=null
         && apiResponse.getContent().get("application/json").getSchema()!=null){
            Schema json = apiResponse.getContent().get("application/json").getSchema();
	    if(apiResponse.getContent().get("application/json").getSchema().get$ref()!=null){

                String ref = json.get$ref();
                ArrayList<String> refls = new ArrayList(Arrays.asList(ref.split("/")));
                ref = refls.get(refls.size() - 1).toString();
		json = schemas.get(ref);
	    }
            List<String> sd = new ArrayList<>(Arrays.asList(json.getDescription().split(" ")));
            Integer size = sd.size();
	    String errType = "";
	    String statusCode = "";
	    if(json.getDescription() != null){
                errType = descriptionToErrType(json.getDescription()); 
	    }
            for(Integer i=0; i<size; i++){
                if(sd.get(i).equals("-StatusCode") && sd.get(i+1) != null){
                    statusCode = sd.get(i+1);
                    try
                    {
                       Integer.parseInt(statusCode);
                    }
                    catch (NumberFormatException ex)
                    {
                       LOGGER.error(statusCode + ": description should be status code number.");
                    }
                    break;
                }
            }
            o.put("name", firstLetterToUpper(errType));
            o.put("statusCode", statusCode);


            final Map<String, Schema> propertyMap = json.getProperties();
            ArrayList<Schema> ss = new ArrayList<>(propertyMap.values());
            //get schema from description in #/components/responses/{keyName}/content/schema/properties/{firstItem}
            schema = ss.get(0);
        }
	if(schema != null && schema.getExample() != null){
            o.put("errMessage", schema.getExample().toString().replace("\n", "\\n"));
	}
        return o;
    }

    @Override
    public CodegenOperation fromOperation(String resourcePath, String httpMethod, Operation operation, Map<String, Schema> definitions, OpenAPI openAPI) {
        CodegenOperation op = super.fromOperation(resourcePath, httpMethod, operation, definitions, openAPI);

        List<String> path = pathToServantRoute(op.path, op.allParams);
        List<String> func = pathToFuncType(op.path, op.allParams);
	List<Boolean> args = new ArrayList<Boolean>();

        for(Integer i = 0; i < func.size(); i++){
	    args.add(true);
	}

        // Query parameters appended to routes
        for (CodegenParameter param : op.queryParams) {
            String paramType = param.dataType;
            path.add("QueryParam \"" + param.baseName + "\" " + paramType);
	    func.add("Maybe " + paramType);
	    args.add(true);
        }

        // Either body or form data parameters appended to route
        // As far as I know, you cannot have two ReqBody routes.
        // Is it possible to have body params AND have form params?
        String bodyType = null;
        if (op.getHasBodyParam()) {
            for (CodegenParameter param : op.bodyParams) {
                bodyType = param.dataType;
	        func.add(bodyType);
	        args.add(true);
                path.add("ReqBody '[JSON] " + bodyType);
            }
        } else if(op.getHasFormParams()) {
            // Use the FormX data type, where X is the conglomerate of all things being passed
            bodyType = "Form" + camelize(op.operationId);
	    func.add(bodyType);
	    args.add(true);
            path.add("ReqBody '[FormUrlEncoded] " + bodyType);
        }

        // Special headers appended to route
        for (CodegenParameter param : op.headerParams) {
            path.add("Header \"" + param.baseName + "\" " + param.dataType);
            String paramType = param.dataType;
	    func.add("Maybe " + paramType);
	    args.add(true);
        }
        
        // Add the HTTP method and return type
        String returnType = op.returnType;
        if (returnType == null || returnType.equals("null")) {
            returnType = "()";
        }
        if (returnType.indexOf(" ") >= 0) {
            returnType = "(" + returnType + ")";
        }

	List<String> errStatus = new ArrayList<>();
	List<Map<String, Object>> status = new ArrayList<>();
	boolean not2xx = false;
	ApiResponses resps = operation.getResponses();

        Map<String, Schema> schemas = new HashMap<String, Schema>();
	if(openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null){
	    schemas = openAPI.getComponents().getSchemas();
	}
	for(String key : resps.keySet()){
	    ApiResponse resp = resps.get(key);
            if(resp.getContent()!=null
            && resp.getContent().get("application/json")!=null
            && resp.getContent().get("application/json").getSchema()!=null
            && resp.getContent().get("application/json").getSchema().get$ref()!=null){

		String ref = resp.getContent().get("application/json").getSchema().get$ref();
		ArrayList<String> refls = new ArrayList(Arrays.asList(ref.split("/")));
		ref = refls.get(refls.size() - 1).toString();
                Schema s = schemas.get(ref);
                if(Integer.parseInt(key)/100 != 2){
	            not2xx = true;
                   List<String> ss = new ArrayList<>(Arrays.asList(s.getDescription().split(" ")));
                   Integer size = ss.size();
                   String errType = "";
                   for(Integer i=0; i<size; i++){
                       if(ss.get(i).equals("-ErrType") && ss.get(i+1) != null){
                           errType = ss.get(i+1);
                   	    if(errType.equals("ad-hoc") && ss.get(i+2) != null){
                   	        errType = ss.get(i+2);
	                       status.add(errResp2status(resp, schemas));
                   	    }
                   	break;
                       }
                   }
	           path.add("Throws " + camelize(fixModelChars(errType)));
	           errStatus.add(camelize(fixModelChars(errType)));
	        }else{
                    if (s instanceof ArraySchema) {
	                ArraySchema as = (ArraySchema) s;
	                String inner = getSchemaType(as.getItems());
                        returnType = "[" + camelize(fixModelChars(inner)) + "]";
                    }else if(s.getDescription()!=null && s.getDescription().contains("-TypeName ")){
                        List<String> ss = new ArrayList<>(Arrays.asList(s.getDescription().split(" ")));
	                Integer size = ss.size();
	                String typeName = null;
	                for(Integer i = 0; i < size; i++){
	                    if(ss.get(i).equals("-TypeName")){
                                returnType = camelize(fixModelChars(ss.get(i+1)));
	                        break;
	                    }
	                }
	            }
	        }
	    }
	}
        op.vendorExtensions.put("x-ad-hocStatus", status);

	if(!not2xx){
	    path.add("NoThrow");
	}

        path.add("Verb '" + op.httpMethod.toUpperCase() + " " + op.responses.get(0).code + " '[JSON] " + returnType);
        func.add("Handler (Envelope '" + errStatus.toString() + " " + returnType + ")");

        op.vendorExtensions.put("x-funcs", joinStrings(" -> ", func));
        op.vendorExtensions.put("x-errStatus", errStatus);
        op.vendorExtensions.put("x-args", args);
        op.vendorExtensions.put("x-routeType", joinStrings(" :> ", path));
        op.vendorExtensions.put("x-formName", "Form" + camelize(op.operationId));
        for(CodegenParameter param : op.formParams) {
            param.vendorExtensions.put("x-formPrefix", camelize(op.operationId, true));
        }
        return op;
    }

    private String fixOperatorChars(String string) {
        StringBuilder sb = new StringBuilder();
        String name = string;
        //Check if it is a reserved word, in which case the underscore is added when property name is generated.
        if (string.startsWith("_")) {
            if (reservedWords.contains(string.substring(1, string.length()))) {
                name = string.substring(1, string.length());
            } else if (reservedWordsMappings.containsValue(string)) {
                name = LEADING_UNDERSCORE.matcher(string).replaceFirst("");
            }
        }
        for (char c : name.toCharArray()) {
            String cString = String.valueOf(c);
            if (specialCharReplacements.containsKey(cString)) {
                sb.append("'");
                sb.append(specialCharReplacements.get(cString));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Remove characters from a string that do not belong in a model classname
    private String fixModelChars(String string) {
	if(string==null){return null;}
        return string.replace(".", "").replace("-", "");
    }

    // Override fromModel to create the appropriate model namings
    @Override
    public CodegenModel fromModel(String name, Schema schema, Map<String, Schema> allSchemas) {
        CodegenModel model = super.fromModel(name, schema, allSchemas);
	String type = "";
	if(schema.getDescription()!=null && schema.getDescription().contains("-TypeName ")){
            List<String> ss = new ArrayList<>(Arrays.asList(schema.getDescription().split(" ")));
	    Integer size = ss.size();
	    for(Integer i = 0; i < size; i++){
	        if(ss.get(i).equals("-TypeName")){
                    type = ss.get(i+1);
		    break;
		}
	    }
	    if(type != null){
		model.classname = type;
	    }
	}else if(schema.getDescription()!=null && schema.getDescription().contains("-ErrType ")){
	    type = descriptionToErrType(schema.getDescription());
            model.classname = type;
            model.vendorExtensions.put("x-errType", true);
	}
            
	if(schema instanceof ArraySchema){
	    model.vendorExtensions.put("x-arr", true);
	}
        // Clean up the class name to remove invalid characters
        model.classname = firstLetterToUpper(camelize(fixModelChars(model.classname),true));
        if(typeMapping.containsValue(model.classname)) {
            model.classname += "_";
        }

        // From the model name, compute the prefix for the fields.
        String prefix = camelize(model.classname, true);
        for(CodegenProperty prop : model.vars) {
            prop.name = toVarName(prefix + camelize(fixOperatorChars(prop.name)));
        }

        // Create newtypes for things with non-object types
        // check if it's a ModelImpl before casting
        //if (!(schema instanceof ModelImpl)) {
        //    return model;
        //}

        //String modelType = ((ModelImpl)  schema).getType();
        String modelType = schema.getType();
        if(modelType != "object" && typeMapping.containsKey(modelType)) {
            String newtype = typeMapping.get(modelType);
            model.vendorExtensions.put("x-customNewtype", newtype);
        }
       
        // Provide the prefix as a vendor extension, so that it can be used in the ToJSON and FromJSON instances.
        model.vendorExtensions.put("x-prefix", prefix);
        model.vendorExtensions.put("x-data", "data");
       
        return model;
    }

    @Override
    public CodegenParameter fromParameter(Parameter param, Set<String> imports) {
        CodegenParameter p = super.fromParameter(param, imports);
        p.vendorExtensions.put("x-formParamName", camelize(p.baseName));
        p.dataType = fixModelChars(p.dataType);
        return p;
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("{-", "{_-").replace("-}", "-_}");
    }
}
