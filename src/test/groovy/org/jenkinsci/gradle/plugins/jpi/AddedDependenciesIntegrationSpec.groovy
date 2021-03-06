package org.jenkinsci.gradle.plugins.jpi

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.TaskOutcome

class AddedDependenciesIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build

    def setup() {
        settings = projectDir.newFile('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = projectDir.newFile('build.gradle')
        build << '''\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            '''.stripIndent()
    }

    def 'resolves test dependencies'() {
        given:
        build << """\
           jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
           }
           java {
                registerFeature('configFile') {
                    usingSourceSet(sourceSets.main)
                }
            }
            dependencies {
                implementation 'org.jenkins-ci.plugins:structs:1.1'
                configFileApi 'org.jenkins-ci.plugins:config-file-provider:2.8.1'
                testImplementation 'org.jenkins-ci.plugins:cloudbees-folder:4.4'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('processTestResources', '--stacktrace')
                .build()

        then:
        result.task(':resolveTestDependencies').outcome == TaskOutcome.SUCCESS
        result.task(':processTestResources').outcome == TaskOutcome.NO_SOURCE
        File dir = new File(projectDir.root, 'build/resources/test/test-dependencies')
        dir.directory
        new File(dir, 'index').text == [
                'config-file-provider', 'structs', 'cloudbees-folder',
                'ui-samples-plugin', 'token-macro', 'credentials',
        ].join('\n')
        new File(dir, 'structs.hpi').exists()
        new File(dir, 'config-file-provider.hpi').exists()
        new File(dir, 'cloudbees-folder.hpi').exists()
        new File(dir, 'token-macro.hpi').exists()
        new File(dir, 'credentials.hpi').exists()
    }

    def 'testCompileClasspath configuration contains plugin JAR dependencies'() {
        given:
        projectDir.newFolder('build')
        build << '''\
            jenkinsPlugin {
                jenkinsVersion = '1.554.2'
            }
            tasks.register('writeAllResolvedDependencies') {
                def output = new File(project.buildDir, 'resolved-dependencies.json')
                outputs.file(output)
                doLast {
                    output.createNewFile()
                    def deprecatedConfigs = [
                        'archives',
                        'compile',
                        'compileOnly',
                        'default',
                        'runtime',
                        'testCompile',
                        'testCompileOnly',
                        'testRuntime',
                    ]
                    def artifactsByConfiguration = configurations.findAll { it.canBeResolved && !deprecatedConfigs.contains(it.name) }.collectEntries { c ->
                        def artifacts = c.incoming.artifactView { it.lenient(true) }.artifacts.collect {
                            it.id.componentIdentifier.toString() + '@' + it.file.name.substring(it.file.name.lastIndexOf('.') + 1)
                        }
                        [(c.name): artifacts]
                    }
                    output.text = groovy.json.JsonOutput.toJson(artifactsByConfiguration)
                }
            }
            '''.stripIndent()

        when:
        gradleRunner()
                .withArguments('writeAllResolvedDependencies', '-s')
                .build()
        def resolutionJson = new File(projectDir.root, 'build/resolved-dependencies.json')
        def resolvedDependencies = new JsonSlurper().parse(resolutionJson)

        then:
        def testCompileClasspath = resolvedDependencies['testCompileClasspath']
        'org.jenkins-ci.plugins:ant:1.2@jar' in testCompileClasspath
        'org.jenkins-ci.main:maven-plugin:2.1@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:antisamy-markup-formatter:1.0@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:javadoc:1.0@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:mailer:1.8@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:matrix-auth:1.0.2@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:subversion:1.45@jar' in testCompileClasspath
    }
}
