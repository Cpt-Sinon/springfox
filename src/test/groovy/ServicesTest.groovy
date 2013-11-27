import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.mangofactory.swagger.spring.controller.DocumentationController
import com.mangofactory.swagger.spring.test.configuration.ServicesTestConfiguration
import groovy.json.JsonSlurper
import org.ho.yaml.Yaml
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.server.test.context.WebContextLoader
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.ByteBuffer

import static com.google.common.collect.Maps.newHashMap
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
// Spring imports omitted for brevity

@ContextConfiguration(loader = WebContextLoader.class, classes = ServicesTestConfiguration.class)
class ServicesTest extends Specification {
  @Autowired DocumentationController controller
  @Shared def apis
  @Shared def mockMvc
  @Shared def testCases

  def setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    apis = response(mockMvc, "/api-docs").apis
    testCases = fromYaml()
  }

  def "Number of services to be documented is 8"() {
    ResultActions actions

    given:
      MockHttpServletRequestBuilder requestBuilder =
        MockMvcRequestBuilders.get("/api-docs").accept(MediaType.APPLICATION_JSON);

    when:
      actions = mockMvc.perform(requestBuilder)

    then:
      actions.andExpect(status().isOk())
      def bytes = ByteBuffer.wrap(actions.andReturn().response.contentAsByteArray)
      def decoded = Charsets.UTF_8.decode(bytes)
      def response = new JsonSlurper().parseText(decoded.toString())
      response.apis.size == 8
  }

  @Unroll("#uri has #operations operations")
  def "Services are documented with the correct number of operations"() {

    expect:
    operations == response(mockMvc, uri).apis.size

    where:
      entry << countsByOperation(testCases)
      uri = apis[entry.getKey()].path
      operations = entry.getValue()
  }

  @Unroll("##index #expectedUri - #testDescription")
  def "Operations are documented correctly"() {
    expect:
      def json = response(mockMvc, documentationUri)
      def api = findApi(json, expectedUri, httpMethod, expectedParams, returnType)
      assert api != null
      def operation = api.operations[0]
      def actualParams = operation.parameters

      operation.responseClass == returnType
      if (actualParams != null && expectedParams != null) {
        expectedParams.size == actualParams.size
        expectedParams.eachWithIndex { entry, i ->
          def parameter = entry
          assert actualParams[i].dataType == parameter.get("type") && actualParams[i].name == parameter.get("name") }
      } else {
        expectedParams == null && (actualParams == null || actualParams.size == 0)
      }

    where:
      record << fromYaml()
      index = record.get("index")
      expectedUri = record.get("expectedUri")
      documentationUri = apis[record.get("uriIndex")].path
      returnType = record.get("returnType")
      expectedParams = record.get("parameters")
      testDescription = record.get("testDescription")
      httpMethod = record.get("httpMethod")
  }

  def findApi(Map<String, Object> json, def expectedUri, def httpMethod, def expectedParams, def returnType) {
    def operationUri = expectedUri
    def method = httpMethod == null ? null : httpMethod
    def params = expectedParams
    def returnClass = returnType
    json.apis.find {
      it.path == operationUri &&
              (method == null || it.operations[0].get("httpMethod") == method) &&
              paramSize(params) == paramSize(it.operations[0].parameters) &&
              returnClass == it.operations[0].responseClass
    }
  }

  def paramSize(def params) {
    return params == null ? 0 : params.size()
  }

  def expectedParamSize(def params) {
    return params == null ? 0 : params.get("parameter").size()
  }

  def response(MockMvc mockMvc, String path) {
    def requestBuilder = MockMvcRequestBuilders.get(path).accept(MediaType.APPLICATION_JSON);
    def actions = mockMvc.perform(requestBuilder)
    def reader = new InputStreamReader(new ByteArrayInputStream(actions.andReturn().response.getContentAsByteArray()))
    def yaml = new Yaml()
    yaml.load(reader) as Map<String, Object>
  }

  def fromYaml() {
    def stream = Resources.getClassLoader().getResourceAsStream("service-tests-cases.yaml")
    def reader = new InputStreamReader(stream)
    def yaml = new Yaml()
    Map<String, Object> recordMap = yaml.load(reader) as Map<String, Object>
    recordMap.testcases
  }

  def countsByOperation(testCases) {
    Map<Integer, Integer> counts = newHashMap()
    testCases.each { record ->
      Integer index = record.get("uriIndex")
      if (counts.containsKey(index)) {
        counts.put(index, counts.get(index) + 1)
      } else {
        counts.put(index, 1)
      }
    }
    counts.entrySet()
  }

}