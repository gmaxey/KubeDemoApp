/*

	CloudBees Flow DSL: Application Model illustrating Kubernetes deployments
	
	Creates
	- Environment models with attached kubectl context names
	- Resources pointing to the same machine as 'local'
	- Application models with a single image to be deployed
	
	Actions supported
	- Kubernetes deploy
	- Kubernetes undeploy
	- Flow environment inventory updated with image version

	Dependencies
	- kubectl installed
	- kubectl config context with the same name as the Flow environments
	- Github configuration
	- Yaml file stored in github
	
	Notes on running the Model
	- Artifact staging must be unselected for Deployment


*/


def proj = 'Kubernetes Example'
def app = 'Kubernetes App'
def image_name = 'nginx'
def image_version = '1.7.9'
def yaml_file = 'demoNginx.yaml'
def yaml_repo = 'https://github.com/gmaxey/KubeDemoApp.git'
def github_config = "Greg's RO github"
def kubectl_path = "/opt/google-cloud-sdk/bin/kubectl"

def envs = ["dev","qa","preprod"]
def tier = 'App Tier'
def hostName = getResource(resourceName: "local").hostName
/*
	The above may return 127.0.0.1 which might fail because localhost is already set to REGISTERED use
	hostName = "localhost"
	as a workaround
*/

project proj,{

	envs.each { env ->
		environment env, {
			environmentTier tier, {
				resource "${app}_${env}_${tier}", hostName: hostName
			}

			// Custom properties
			property "context", value: env
		}
	}
	
	application app, {

		applicationTier tier, {

			component image_name, pluginName: null, {
				pluginKey = 'EC-Artifact'

				process 'Install', {
					applicationName = null
					processType = 'DEPLOY'

					processStep 'Create Artifact Placeholder', {
						actualParameter = [
							'commandToRun': 'artifact artifactKey: "$[/myComponent/ec_content_details/artifactName]", groupId: "group"',
							'shellToUse': 'ectool evalDsl --dslFile',
						]
						applicationTierName = null
						processStepType = 'command'
						subprocedure = 'RunCommand'
						subproject = '/plugins/EC-Core/project'
					}

					processStep 'Get yaml deploy file(s)', {
						actualParameter = [
							'clone': '1',
							'config': github_config,
							dest: '$[/myComponent/image_name]',
							'GitRepo': '$[/myComponent/yaml_repo]',
							'overwrite': '0',
							'tag': '',
						]
						processStepType = 'plugin'
						subprocedure = 'CheckoutCode'
						subproject = '/plugins/ECSCM-Git/project'
					}

					processStep 'Undeploy any existing', {
						actualParameter = [
							commandToRun: """\
								$kubectl_path config use-context \$[/myEnvironment/context]
								sed 's/image: .*/image: \$[/myComponent/image_name]/' \$[/myComponent/image_name]/\$[/myComponent/yaml_file] | $kubectl_path delete -f -  || echo ok
							""".stripIndent(),
						]
						applicationTierName = null
						processStepType = 'command'
						subprocedure = 'RunCommand'
						subproject = '/plugins/EC-Core/project'
					}

					processStep 'Deploy', {
						actualParameter = [
							commandToRun: """\
								$kubectl_path config use-context \$[/myEnvironment/context]
								sed 's/image: .*/image: \$[/myComponent/image_name]:\$[\$[/myComponent/componentName]_version]/' \$[/myComponent/image_name]/\$[/myComponent/yaml_file] | $kubectl_path create -f -
							""".stripIndent(),
						]
						applicationTierName = null
						processStepType = 'command'
						subprocedure = 'RunCommand'
						subproject = '/plugins/EC-Core/project'
					}

					processDependency 'Create Artifact Placeholder', targetProcessStepName: 'Get yaml deploy file(s)'

					processDependency 'Undeploy any existing', targetProcessStepName: 'Deploy'

					processDependency 'Get yaml deploy file(s)', targetProcessStepName: 'Undeploy any existing'
				}

				process 'Uninstall', {
					applicationName = null
					processType = 'UNDEPLOY'

					processStep 'Get yaml deploy file(s)', {
						actualParameter = [
							'clone': '1',
							'config': github_config,
							dest: '$[/myComponent/image_name]',
							'GitRepo': '$[/myComponent/yaml_repo]',
							'overwrite': '0',
							'tag': '',
						]
						processStepType = 'plugin'
						subprocedure = 'CheckoutCode'
						subproject = '/plugins/ECSCM-Git/project'
					}
					
					processStep 'Uninstall', {
						actualParameter = [
							commandToRun: """\
								$kubectl_path config use-context \$[/myEnvironment/context]
								sed 's/image: .*/image: \$[/myComponent/image_name]/' \$[/myComponent/image_name]/\$[/myComponent/yaml_file] | $kubectl_path delete -f -  || echo ok
							""".stripIndent(),
						]
						applicationTierName = null
						processStepType = 'command'
						subprocedure = 'RunCommand'
						subproject = '/plugins/EC-Core/project'
					}
				
					processDependency 'Get yaml deploy file(s)', targetProcessStepName: 'Uninstall'
					
					
				}

				// Custom properties

				property 'ec_content_details', {

					property 'artifactName', value: image_name, {
					}
					artifactVersionLocationProperty = '/myJob/retrievedArtifactVersions/$[assignedResourceName]'

					property 'overwrite', value: 'update'
					
					pluginProcedure = 'Retrieve'

					property 'pluginProjectName', value: 'EC-Artifact'
					
					retrieveToDirectory = ''

					property 'versionRange', value: "\$[${image_name}_version]"
				}
				property "image_name", value: image_name
				property "yaml_file", value: yaml_file
				property "yaml_repo", value: yaml_repo
			}
		}

		process 'Deploy', {
			processType = "OTHER"
			formalParameter "ec_enforceDependencies", defaultValue: '0'
			formalParameter "ec_${image_name}-run", defaultValue: '1'
			formalParameter "ec_${image_name}-version", defaultValue: "\$[/projects/${projectName}/applications/${applicationName}/components/${image_name}/ec_content_details/versionRange]", expansionDeferred: '1'
			formalParameter 'ec_smartDeployOption', defaultValue: '0'
			formalParameter 'ec_stageArtifacts', defaultValue: '0'

			formalParameter "${image_name}_version", defaultValue: image_version

			processStep image_name, {
				applicationTierName = tier
				processStepType = 'process'
				subcomponent = image_name
				subcomponentApplicationName = applicationName
				subcomponentProcess = 'Install'

			}

		}

		process 'Undeploy', {
			processType = 'OTHER'

			formalParameter "ec_enforceDependencies", defaultValue: '0'
			formalParameter "ec_${image_name}-run", defaultValue: '1'
			formalParameter "ec_${image_name}-version", defaultValue: "\$[/projects/${projectName}/applications/${applicationName}/components/${image_name}/ec_content_details/versionRange]", expansionDeferred: '1'
			formalParameter 'ec_smartDeployOption', defaultValue: '1'
			formalParameter 'ec_stageArtifacts', defaultValue: '0'

			processStep image_name, {
				applicationTierName = tier
				processStepType = 'process'
				subcomponent = image_name
				subcomponentApplicationName = applicationName
				subcomponentProcess = 'Uninstall'
			}
		}

		envs.each { env ->
			tierMap env, {
				environmentName = env
				environmentProjectName = projectName

				tierMapping "${tier}_${env}", {
					applicationTierName = tier
					environmentTierName = tier
				}
			}
		}
	
	}
}
