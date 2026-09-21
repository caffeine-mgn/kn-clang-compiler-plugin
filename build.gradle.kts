plugins {
    base
}

allprojects {
    group = "pw.binom"
    version = (findProperty("version") as String?) ?: "0.0.1-SNAPSHOT"
}
