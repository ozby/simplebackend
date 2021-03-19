package com.prettybyte.simplebackend.lib

enum class AuthorizationRuleResult {
    noOpinion,
    deny,
    allow,
}


/* TODO: To avoid returning allow by mistake (when noOpinion should be returned), we should probably have more sets with different return values
positiveReadModelRules: Set<(UserIdentity, Model<out ModelProperties>) -> AllowOrNoOpinion>
negativeReadModelRules: Set<(UserIdentity, Model<out ModelProperties>) -> DenyOrNoOpinion>

 */