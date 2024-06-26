@Grab('org.yaml:snakeyaml:1.33')
import org.yaml.snakeyaml.*
import groovy.yaml.*
import java.util.Map
import java.time.LocalDate

def file = new File(this.args[0])

def fileReader = new FileReader(file)
def yaml = new Yaml().load(fileReader)

yaml.metadata.annotations.support = "https://github.com/trustification/trustify-operator/issues"
yaml.metadata.annotations.description = "Trustify is makes Software Suppand ultimately easier to create, manage, consume."
yaml.metadata.annotations.createdAt = LocalDate.now().toString()
yaml.metadata.annotations.containerImage = yaml.spec.install.spec.deployments[0].spec.template.spec.containers[0].image

yaml.spec.customresourcedefinitions.owned[0].description = "Trustify"

// Adding cluster permissions to be able to fetch host domain
yaml.spec.install.spec.clusterPermissions.rules[0][1] = [:]
yaml.spec.install.spec.clusterPermissions.rules[0][1].apiGroups = ['config.openshift.io']
yaml.spec.install.spec.clusterPermissions.rules[0][1].resources = ['ingresses']
yaml.spec.install.spec.clusterPermissions.rules[0][1].verbs = ['get', 'list']

DumperOptions options = new DumperOptions();
options.indent = 2
options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
options.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
options.prettyFlow = true

new Yaml(options).dump(yaml, new FileWriter(file))
