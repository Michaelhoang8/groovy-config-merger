import groovy.json.JsonSlurper
import groovy.json.JsonOutput

if (args.length < 3) {
    System.err.println('Usage: groovy -cp src bin/merge.groovy reject|keep|override base.json overlay.json [...]')
    System.exit(2)
}
try {
    def layers = args.drop(1).collect { filename -> [name: filename, value: new JsonSlurper().parse(new File(filename), 'UTF-8')] }
    println JsonOutput.prettyPrint(JsonOutput.toJson(ConfigMerger.merge(layers, args[0])))
} catch (Exception error) {
    System.err.println('Error: ' + error.message)
    System.exit(2)
}
