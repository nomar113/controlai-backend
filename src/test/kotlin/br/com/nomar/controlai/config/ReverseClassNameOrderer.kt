package br.com.nomar.controlai.config

import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.ClassOrdererContext

// Runs test classes in reverse fully-qualified-name order, to expose tests that only pass
// because another class ran before them. Not used by default; select it with
// ./gradlew test -PtestClassOrder=br.com.nomar.controlai.config.ReverseClassNameOrderer
class ReverseClassNameOrderer : ClassOrderer {

    override fun orderClasses(context: ClassOrdererContext) {
        context.classDescriptors.sortByDescending { it.testClass.name }
    }
}
