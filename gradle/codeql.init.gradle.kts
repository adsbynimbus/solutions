allprojects {
    tasks.withType<JavaCompile>().configureEach {
        outputs.upToDateWhen { false }
    }
    tasks.withType<GroovyCompile>().configureEach {
        outputs.upToDateWhen { false }
    }
    tasks.withType<ScalaCompile>().configureEach {
        outputs.upToDateWhen { false }
    }
    // All kotlin compilation tasks including compileAndroidMain from com.android.kotlin.multiplatform.library
    with(Regex("compile.*[Android|Kotlin]")) {
        tasks.named { it.contains(this) }.configureEach {
            outputs.upToDateWhen { false }
        }
    }
}
