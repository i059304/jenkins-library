import hudson.AbortException
import org.jenkinsci.plugins.pipeline.utility.steps.shaded.org.yaml.snakeyaml.Yaml
import org.jenkinsci.plugins.pipeline.utility.steps.shaded.org.yaml.snakeyaml.parser.ParserException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder

import com.lesfurets.jenkins.unit.BasePipelineTest

import util.JenkinsConfigRule
import util.JenkinsLoggingRule
import util.JenkinsSetupRule
import util.JenkinsShellCallRule

public class MTABuildTest extends BasePipelineTest {

    private ExpectedException thrown = new ExpectedException()
    private TemporaryFolder tmp = new TemporaryFolder()
    private JenkinsLoggingRule jlr = new JenkinsLoggingRule(this)
    private JenkinsShellCallRule jscr = new JenkinsShellCallRule(this)

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(thrown)
            .around(tmp)
            .around(new JenkinsSetupRule(this))
            .around(jlr)
            .around(jscr)
            .around(new JenkinsConfigRule(this))

    def currentDir
    def otherDir
    def mtaBuildShEnv

    def mtaBuildScript
    def cpe

    @Before
    void init() {

        currentDir = tmp.newFolder().toURI().getPath()[0..-2] //omit final '/'
        otherDir = tmp.newFolder().toURI().getPath()[0..-2] //omit final '/'

        helper.registerAllowedMethod('readYaml', [Map], {
            m ->
                return new Yaml().load((m.file as File).text)
        })
        helper.registerAllowedMethod("dir", [String, Closure], {
            s, c ->
                currentDir = "${currentDir}/${s}"
                c()
        })
        helper.registerAllowedMethod('pwd', [], { currentDir } )
        helper.registerAllowedMethod("withEnv", [List.class, Closure.class],
                { l, c ->
                    mtaBuildShEnv = l
                    c()
                })
        helper.registerAllowedMethod('error', [String], { s -> throw new hudson.AbortException(s) })

        binding.setVariable('PATH', '/usr/bin')
        binding.setVariable('JAVA_HOME', '/opt/java')
        binding.setVariable('env', [:])

        mtaBuildScript = loadScript("mtaBuild.groovy").mtaBuild
        cpe = loadScript('commonPipelineEnvironment.groovy').commonPipelineEnvironment
    }


    @Test
    public void straightForwardTest(){

        binding.getVariable('env')['MTA_JAR_LOCATION'] = '/opt/mta'

        new File("${currentDir}/mta.yaml") << defaultMtaYaml()

        def mtarFilePath = mtaBuildScript.call(script: [commonPipelineEnvironment: cpe],
                                         buildTarget: 'NEO')

        assert jscr.shell[0] =~ /sed -ie "s\/\\\$\{timestamp\}\/`date \+%Y%m%d%H%M%S`\/g" ".*\/mta.yaml"$/

        assert jscr.shell[1].contains("PATH=./node_modules/.bin:/usr/bin")

        assert jscr.shell[1].contains(' -jar /opt/mta/mta.jar --mtar ')

        assert mtarFilePath == "${currentDir}/com.mycompany.northwind.mtar"

        assert jlr.log.contains( "[mtaBuild] MTA JAR \"/opt/mta/mta.jar\" retrieved from environment.")
    }


    @Test
    public void mtarFilePathFromCommonPipelineEnviromentTest(){

        binding.getVariable('env')['MTA_JAR_LOCATION'] = '/opt/mta'

        new File("${currentDir}/mta.yaml") << defaultMtaYaml()

        mtaBuildScript.call(script: [commonPipelineEnvironment: cpe],
                      buildTarget: 'NEO')

        def mtarFilePath = cpe.getMtarFilePath()

        assert jscr.shell[0] =~ /sed -ie "s\/\\\$\{timestamp\}\/`date \+%Y%m%d%H%M%S`\/g" ".*\/mta.yaml"$/

        assert jscr.shell[1].contains("PATH=./node_modules/.bin:/usr/bin")

        assert jscr.shell[1].contains(' -jar /opt/mta/mta.jar --mtar ')

        assert mtarFilePath == "${currentDir}/com.mycompany.northwind.mtar"

        assert jlr.log.contains("[mtaBuild] MTA JAR \"/opt/mta/mta.jar\" retrieved from environment.")
    }


    @Test
    public void mtaBuildWithSurroundingDirTest(){

        binding.getVariable('env')['MTA_JAR_LOCATION'] = '/opt/mta'

        def newDirName = 'newDir'
        def newDir = new File("${currentDir}/${newDirName}")

        newDir.mkdirs()
        new File(newDir, 'mta.yaml') << defaultMtaYaml()

        helper.registerAllowedMethod('pwd', [], { newDir } )

        def mtarFilePath = mtaBuildScript.call(script: [commonPipelineEnvironment: cpe], buildTarget: 'NEO')

        assert jscr.shell[0] =~ /sed -ie "s\/\\\$\{timestamp\}\/`date \+%Y%m%d%H%M%S`\/g" ".*\/newDir\/mta.yaml"$/

        assert jscr.shell[1].contains("PATH=./node_modules/.bin:/usr/bin")

        assert jscr.shell[1].contains(' -jar /opt/mta/mta.jar --mtar ')

        assert mtarFilePath == "${currentDir}/${newDirName}/com.mycompany.northwind.mtar"

        assert jlr.log.contains("[mtaBuild] MTA JAR \"/opt/mta/mta.jar\" retrieved from environment.")
    }

    @Test
    void mtaHomeNotSetTest() {

        new File("${currentDir}/mta.yaml") << defaultMtaYaml()

        def mtarFilePath = mtaBuildScript.call(script: [commonPipelineEnvironment: cpe], buildTarget: 'NEO')

        assert jscr.shell[0] =~ /sed -ie "s\/\\\$\{timestamp\}\/`date \+%Y%m%d%H%M%S`\/g" ".*\/mta.yaml"$/

        assert jscr.shell[1].contains("PATH=./node_modules/.bin:/usr/bin")

        assert jscr.shell[1].contains(' -jar mta.jar --mtar ')

        assert mtarFilePath == "${currentDir}/com.mycompany.northwind.mtar"

        assert jlr.log.contains( "[mtaBuild] Using MTA JAR from current working directory." )
    }


    @Test
    void mtaHomeAsParameterTest() {

        new File("${currentDir}/mta.yaml") << defaultMtaYaml()

        def mtarFilePath = mtaBuildScript.call(mtaJarLocation: '/mylocation/mta', buildTarget: 'NEO')

        assert jscr.shell[0] =~ /sed -ie "s\/\\\$\{timestamp\}\/`date \+%Y%m%d%H%M%S`\/g" ".*\/mta.yaml"$/

        assert jscr.shell[1].contains("PATH=./node_modules/.bin:/usr/bin")

        assert jscr.shell[1].contains(' -jar /mylocation/mta/mta.jar --mtar ')

        assert mtarFilePath == "${currentDir}/com.mycompany.northwind.mtar"

        assert jlr.log.contains("[mtaBuild] MTA JAR \"/mylocation/mta/mta.jar\" retrieved from parameters.".toString())
    }


    @Test
    public void noMtaPresentTest(){
        thrown.expect(FileNotFoundException)

        mtaBuildScript.call(script: [commonPipelineEnvironment: cpe],
                      buildTarget: 'NEO')
    }


    @Test
    public void badMtaTest(){
        thrown.expect(ParserException)
        thrown.expectMessage('while parsing a block mapping')

        new File("${currentDir}/mta.yaml") << badMtaYaml()

        mtaBuildScript.call(script: [commonPipelineEnvironment: cpe],
                      buildTarget: 'NEO')
    }


    @Test
    public void noIdInMtaTest(){
        thrown.expect(AbortException)
        thrown.expectMessage("Property 'ID' not found in mta.yaml file at: '")

        new File("${currentDir}/mta.yaml") << noIdMtaYaml()

        mtaBuildScript.call(script: [commonPipelineEnvironment: cpe],
                      buildTarget: 'NEO')
    }


    @Test
    public void noBuildTargetTest(){
        thrown.expect(Exception)
        thrown.expectMessage("ERROR - NO VALUE AVAILABLE FOR buildTarget")

        new File("${currentDir}/mta.yaml") << defaultMtaYaml()

        mtaBuildScript.call(script: [commonPipelineEnvironment: cpe])
    }

    private defaultMtaYaml(){
        return  '''
                _schema-version: "2.0.0"
                ID: "com.mycompany.northwind"
                version: 1.0.0
                
                parameters:
                  hcp-deployer-version: "1.0.0"
                
                modules:
                  - name: "fiorinorthwind"
                    type: html5
                    path: .
                    parameters:
                       version: 1.0.0-${timestamp}
                    build-parameters:
                      builder: grunt
                build-result: dist
                '''
    }

    private badMtaYaml(){
        return  '''
                _schema-version: "2.0.0
                ID: "com.mycompany.northwind"
                version: 1.0.0
                
                parameters:
                  hcp-deployer-version: "1.0.0"
                
                modules:
                  - name: "fiorinorthwind"
                    type: html5
                    path: .
                    parameters:
                       version: 1.0.0-${timestamp}
                    build-parameters:
                      builder: grunt
                build-result: dist
                '''
    }

    private noIdMtaYaml(){
        return  '''
                _schema-version: "2.0.0"
                version: 1.0.0
                
                parameters:
                  hcp-deployer-version: "1.0.0"
                
                modules:
                  - name: "fiorinorthwind"
                    type: html5
                    path: .
                    parameters:
                       version: 1.0.0-${timestamp}
                    build-parameters:
                      builder: grunt
                build-result: dist
                '''
    }
}
