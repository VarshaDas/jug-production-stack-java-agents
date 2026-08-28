plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    implementation(platform("org.springaicommunity:spring-ai-agentcore-bom:2.1.0"))
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse")
    implementation("org.springaicommunity:spring-ai-agentcore-runtime-starter")
}
