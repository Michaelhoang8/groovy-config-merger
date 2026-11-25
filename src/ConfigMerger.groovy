import groovy.json.JsonOutput

class MergeConflictException extends IllegalArgumentException {
    final List<Map> conflicts
    MergeConflictException(List<Map> conflicts) {
        super("Conflicting values at: " + conflicts.collect { it.path ?: '/' }.join(', '))
        this.conflicts = conflicts
    }
}

class ConfigMerger {
    static String pointer(String parent, Object key) {
        parent + '/' + key.toString().replace('~', '~0').replace('/', '~1')
    }
    static Object copyValue(Object value) {
        if (value instanceof Map) return value.collectEntries { k, v -> [(k): copyValue(v)] }
        if (value instanceof List) return value.collect { copyValue(it) }
        value
    }
    static void stamp(Object value, String path, String source, Map provenance) {
        if (value instanceof Map && !value.isEmpty()) {
            value.each { k, v -> stamp(v, pointer(path, k), source, provenance) }
        } else provenance[path] = source
    }
    static void clearBranch(String path, Map provenance) {
        provenance.keySet().findAll { it == path || it.startsWith(path + '/') }.each { provenance.remove(it) }
    }
    static Object combine(Object current, Object incoming, String path, String source,
                          String policy, Map provenance, List<Map> conflicts) {
        if (current instanceof Map && incoming instanceof Map) {
            // Empty objects have their own provenance until they acquire children.
            if (!incoming.isEmpty()) provenance.remove(path)
            incoming.each { key, value ->
                String child = pointer(path, key)
                if (current.containsKey(key)) current[key] = combine(current[key], value, child, source, policy, provenance, conflicts)
                else { current[key] = copyValue(value); stamp(value, child, source, provenance) }
            }
            return current
        }
        if (current == incoming) return current
        Map previousSources = provenance.findAll { k, v -> k == path || k.startsWith(path + '/') }
        conflicts << [path: path, previousSources: previousSources, incomingSource: source,
                      previousValue: copyValue(current), incomingValue: copyValue(incoming), resolution: policy]
        if (policy == 'override') {
            clearBranch(path, provenance)
            stamp(incoming, path, source, provenance)
            return copyValue(incoming)
        }
        current
    }
    static Map merge(List<Map> layers, String policy = 'reject') {
        if (!(policy in ['reject', 'keep', 'override'])) throw new IllegalArgumentException('Policy must be reject, keep or override')
        if (!layers || layers.any { !(it.name instanceof String) || !it.name || !(it.value instanceof Map) })
            throw new IllegalArgumentException('Each layer needs a nonempty name and a JSON object')
        if (layers.collect { it.name }.unique().size() != layers.size()) throw new IllegalArgumentException('Layer names must be unique')
        Map result = copyValue(layers.first().value) as Map
        Map provenance = new TreeMap()
        List<Map> conflicts = []
        stamp(result, '', layers.first().name, provenance)
        layers.drop(1).each { layer -> combine(result, layer.value, '', layer.name, policy, provenance, conflicts) }
        if (policy == 'reject' && conflicts) throw new MergeConflictException(conflicts)
        [config: result, provenance: provenance, conflicts: conflicts]
    }
}
