import groovy.json.JsonSlurper

def checkReject = { Closure action ->
    boolean rejected = false
    try { action.call() } catch (IllegalArgumentException expected) { rejected = true }
    assert rejected
}
def base = [service: [port: 8080, tls: false], tags: ['a'], explicitNull: null, 'a/b': ['~key': 1]]
def overlay = [service: [tls: true, workers: 4], tags: ['b'], explicitNull: 'set', 'a/b': ['~key': 2]]
def layers = [[name: 'base', value: base], [name: 'prod', value: overlay]]
def result = ConfigMerger.merge(layers, 'override')
assert result.config.service == [port: 8080, tls: true, workers: 4]
assert result.config.tags == ['b']
assert result.provenance['/service/port'] == 'base'
assert result.provenance['/service/tls'] == 'prod'
assert result.provenance['/a~1b/~0key'] == 'prod'
assert result.conflicts.size() == 4
assert base.service.tls == false && overlay.service.tls == true : 'Inputs must not mutate'
assert ConfigMerger.merge(layers, 'keep').config.service.tls == false
checkReject { ConfigMerger.merge(layers, 'reject') }
checkReject { ConfigMerger.merge(layers, 'unknown') }
checkReject { ConfigMerger.merge([]) }
checkReject { ConfigMerger.merge([[name: 'a', value: [:]], [name: 'a', value: [:]]]) }
assert ConfigMerger.merge([[name: 'a', value: [x: 1]], [name: 'b', value: [x: 1]]]).conflicts.isEmpty()
def changedType = ConfigMerger.merge([[name: 'a', value: [x: [nested: 1]]], [name: 'b', value: [x: false]]], 'override')
assert changedType.provenance == ['/x': 'b'] : 'Stale child provenance must be removed'
def empty = ConfigMerger.merge([[name: 'a', value: [x: [:]]], [name: 'b', value: [x: [child: 1]]]], 'override')
assert empty.provenance == ['/x/child': 'b']
def fixture = ['base', 'production'].collect { [name: it, value: new JsonSlurper().parse(new File("fixtures/${it}.json"))] }
assert ConfigMerger.merge(fixture, 'override').config.service.timeoutSeconds == 15
println 'ConfigMerger: recursive merge, conflict policies, nulls, arrays and provenance tests passed'
