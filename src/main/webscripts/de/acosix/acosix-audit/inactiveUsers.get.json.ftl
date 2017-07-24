<#-- 
 * Copyright 2017 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
  -->

<#escape x as jsonUtils.encodeJSONString(x)><#compress>
{
    "count" : "${users?size?c}",
    "users": [<#list users as inactiveUser>
        {
            "userName": "${inactiveUser.node.properties.userName}",
            "firstName": "${inactiveUser.node.properties.firstName!""}",
            "lastName": "${inactiveUser.node.properties.lastName!""}",
            "email": "${inactiveUser.node.properties.email!""}",
            "authorisation": "${inactiveUser.info.authorisedState?string}"
        }<#if inactiveUser_has_next>,</#if>
    </#list>]
}
</#compress></#escape>