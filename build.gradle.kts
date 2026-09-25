plugins {
    alias(libs.plugins.sas.deployable)
}

sasDeployable {
    mainClass = "no.nav.helse.spammer.AppKt"
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.tbdLibs.spurteduClient)
    implementation(libs.tbdLibs.retry)

    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.kotliquery)

    implementation(libs.rison)

    testImplementation(libs.mockk)
    testImplementation(libs.tbdLibs.rapidsAndRiversTest)
    testImplementation(libs.tbdLibs.postgresTestdatabaser)
}
