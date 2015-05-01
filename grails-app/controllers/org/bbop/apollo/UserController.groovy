package org.bbop.apollo

import org.bbop.apollo.gwt.shared.FeatureStringEnum

import grails.converters.JSON
import org.apache.shiro.crypto.hash.Sha256Hash
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject
import org.bbop.apollo.gwt.shared.PermissionEnum;

class UserController {

    def permissionService
    def userService

    def loadUsers() {
        JSONArray returnArray = new JSONArray()

        def allowableOrganisms = permissionService.getOrganisms(permissionService.currentUser)
        log.debug "allowable organisms ${allowableOrganisms.size()}"

        List<String> allUserGroups = UserGroup.all.name
        Map<String, List<UserOrganismPermission>> userOrganismPermissionMap = new HashMap<>()
        List<UserOrganismPermission> userOrganismPermissionList = UserOrganismPermission.findAllByOrganismInList(allowableOrganisms as List)
        log.debug "total permission list ${userOrganismPermissionList.size()}"
        for (UserOrganismPermission userOrganismPermission in userOrganismPermissionList) {
            List<UserOrganismPermission> userOrganismPermissionListTemp = userOrganismPermissionMap.get(userOrganismPermission.user.username)
            if (userOrganismPermissionListTemp == null) {
                userOrganismPermissionListTemp = new ArrayList<>()
            }
            userOrganismPermissionListTemp.add(userOrganismPermission)
            userOrganismPermissionMap.put(userOrganismPermission.user.username, userOrganismPermissionListTemp)
        }
        log.debug "org permission map ${userOrganismPermissionMap.size()}"
        for (v in userOrganismPermissionMap) {
            log.debug "${v.key} ${v.value}"
        }

        User.all.each {
            def userObject = new JSONObject()

            userObject.userId = it.id
            userObject.username = it.username
            userObject.firstName = it.firstName
            userObject.lastName = it.lastName
            Role role = userService.getHighestRole(it)
            userObject.role = role?.name

            log.debug "groups ${it.userGroups} for ${it.username}"


            JSONArray groupsArray = new JSONArray()
            List<String> groupsForUser = new ArrayList<>()
            for (group in it.userGroups) {
                JSONObject groupJson = new JSONObject()
                groupsForUser.add(group.name)
                groupJson.put("name", group.name)
                groupsArray.add(groupJson)
            }
            userObject.groups = groupsArray


            JSONArray availableGroupsArray = new JSONArray()
            List<String> availableGroups = allUserGroups - groupsForUser
            for (group in availableGroups) {
                JSONObject groupJson = new JSONObject()
                groupJson.put("name", group)
                availableGroupsArray.add(groupJson)
            }
            userObject.availableGroups = availableGroupsArray

            // organism permissions
            JSONArray organismPermissionsArray = new JSONArray()
            def userOrganismPermissionList3 = userOrganismPermissionMap.get(it.username)
            List<Long> organismsWithPermissions = new ArrayList<>()
            log.debug "lsit retrieved? : ${userOrganismPermissionList3?.size()} for ${it.username}"
            for (UserOrganismPermission userOrganismPermission in userOrganismPermissionList3) {
                if (userOrganismPermission.organism in allowableOrganisms) {
                    JSONObject organismJSON = new JSONObject()
                    organismJSON.put("organism", userOrganismPermission.organism.commonName)
                    organismJSON.put("permissions", userOrganismPermission.permissions)
                    organismJSON.put("userId", userOrganismPermission.userId)
                    organismJSON.put("id", userOrganismPermission.id)
                    organismPermissionsArray.add(organismJSON)
                    organismsWithPermissions.add(userOrganismPermission.organism.id)
                }
            }

            // if an organism has permissions
            Set<Organism> organismList = allowableOrganisms.findAll() {
                !organismsWithPermissions.contains(it.id)
            }
            log.debug "organisms with permissions ${organismsWithPermissions.size()}"
            log.debug "organisms list ${organismList.size()}"

            for (Organism organism in organismList) {
                JSONObject organismJSON = new JSONObject()
                organismJSON.put("organism", organism.commonName)
                organismJSON.put("permissions", "[]")
                organismJSON.put("userId", it.id)
                organismPermissionsArray.add(organismJSON)
            }


            userObject.organismPermissions = organismPermissionsArray

            returnArray.put(userObject)
        }

        render returnArray as JSON
    }

    def checkLogin() {
        def currentUser = permissionService.currentUser
        if (currentUser) {
            def userObject = new JSONObject()
            userObject.userId = currentUser.id
            userObject.username = currentUser.username
            userObject.firstName = currentUser.firstName
            userObject.lastName = currentUser.lastName
            Role role = userService.getHighestRole(currentUser)
            userObject.role = role?.name


            Map<String, JSONObject> organismMap = new HashMap<>()
            for (UserOrganismPermission userOrganismPermission in UserOrganismPermission.findAllByUser(currentUser)) {
                JSONObject organismJSON = new JSONObject()
                organismJSON.put("organism", userOrganismPermission.organism.commonName)
                organismJSON.put("permissions", userOrganismPermission.permissions)
                organismJSON.put("userId", userOrganismPermission.userId)
                organismJSON.put("id", userOrganismPermission.id)

                organismMap.put(userOrganismPermission.organism.commonName, organismJSON)
            }

            // TODO: not sure if these are automatically integrated or not
            for (GroupOrganismPermission groupOrganismPermission in GroupOrganismPermission.findAllByGroupInList(currentUser.userGroups as List)) {

                JSONObject organismJSON = organismMap.get(groupOrganismPermission.organism.commonName)
                if (!organismJSON) {
                    organismJSON = new JSONObject()
                    organismJSON.put("organism", groupOrganismPermission.organism.commonName)
                    organismJSON.put("permissions", groupOrganismPermission.permissions)
                    organismJSON.put("id", groupOrganismPermission.id)
                } else {
                    String permissions = mergePermissions(organismJSON.getString("permissions"), groupOrganismPermission.permissions)
                    organismJSON.put("permissions", permissions)
                }
                organismJSON.put("groupId", groupOrganismPermission.groupId)


                organismMap.put(groupOrganismPermission.organism.commonName, organismJSON)
            }

            JSONArray organismPermissionsArray = new JSONArray()
            organismPermissionsArray.addAll(organismMap.values())

            userObject.organismPermissions = organismPermissionsArray
            userObject.put(FeatureStringEnum.HAS_USERS.value,true)

            render userObject as JSON
        } else {
            def userObject = new JSONObject()
            userObject.put(FeatureStringEnum.HAS_USERS.value,User.count >0)
            render userObject as JSON
        }
    }

    String mergePermissions(String permissions1, String permissions2) {
        log.debug "permissions1: ${permissions1}"
        log.debug "permissions2: ${permissions2}"
        JSONArray permissions1Array = JSON.parse(permissions1) as JSONArray
        JSONArray permissions2Array = JSON.parse(permissions2) as JSONArray

        Set<String> finalPermissions = new HashSet<>()
        for(int i =0 ; i < permissions1Array.size() ; i++){
            finalPermissions.add(permissions1Array.getString(i))
        }
        for(int i =0 ; i < permissions2Array.size() ; i++){
            finalPermissions.add(permissions2Array.getString(i))
        }

        JSONArray returnArray = new JSONArray()
        for(String permission in finalPermissions){
            returnArray.add(permission)
        }

        String finalPermissionsString = returnArray.toString()

        log.debug "final permission string ${finalPermissionsString}"

        return finalPermissionsString
    }

    def addUserToGroup() {
        log.debug "adding user to group ${request.JSON} -> ${params}"
        JSONObject dataObject = JSON.parse(params.data)
        UserGroup userGroup = UserGroup.findByName(dataObject.group)
        User user = User.findById(dataObject.userId)
        user.addToUserGroups(userGroup)
        user.save(flush: true)
        render new JSONObject() as JSON
    }

    def removeUserFromGroup() {
        log.debug "removing user from group ${request.JSON} -> ${params}"
        JSONObject dataObject = JSON.parse(params.data)
        UserGroup userGroup = UserGroup.findByName(dataObject.group)
        User user = User.findById(dataObject.userId)
        user.removeFromUserGroups(userGroup)
        user.save(flush: true)
        render new JSONObject() as JSON
    }

    def createUser() {
        log.debug "creating user ${request.JSON} -> ${params}"
        JSONObject dataObject = JSON.parse(params.data)
        if(User.findByUsername(dataObject.email)!=null) {
            JSONObject error = new JSONObject()
            error.put("error","User already exists. Please enter a new username")
            render error.toString()

            return
        }

        User user = new User(
                firstName: dataObject.firstName
                , lastName: dataObject.lastName
                , username: dataObject.email
                , passwordHash: new Sha256Hash(dataObject.password).toHex()
        )
        user.save(insert: true)

        String roleString = dataObject.role
        Role role = Role.findByName(roleString.toUpperCase())
        log.debug "adding role: ${role}"
        user.addToRoles(role)
        role.addToUsers(user)
        role.save()
        user.save(flush: true)

        render new JSONObject() as JSON
    }

    def deleteUser() {
        log.debug "deleting user ${request.JSON} -> ${params}"
        JSONObject dataObject = JSON.parse(params.data)
        User user = User.findById(dataObject.userId)
        user.userGroups.each { it ->
            it.removeFromUsers(user)
        }
        UserTrackPermission.deleteAll(UserTrackPermission.findAllByUser(user))
        UserOrganismPermission.deleteAll(UserOrganismPermission.findAllByUser(user))
        user.delete(flush: true)
    }

    def updateUser() {
        JSONObject dataObject = JSON.parse(params.data)
        User user = User.findById(dataObject.userId)
        user.firstName = dataObject.firstName
        user.lastName = dataObject.lastName
        user.username = dataObject.email

        if (dataObject.password) {
            user.passwordHash = new Sha256Hash(dataObject.password).toHex()
        }

        String roleString = dataObject.role
        Role currentRole = userService.getHighestRole(user)

        if (!currentRole || !roleString.equalsIgnoreCase(currentRole.name)) {
            if (currentRole) {
                user.removeFromRoles(currentRole)
            }
            Role role = Role.findByName(roleString.toUpperCase())
            user.addToRoles(role)
        }

        user.save(flush: true)

    }

    def getOrganismPermissionsForUser() {
        JSONObject dataObject = JSON.parse(params.data)
        User user = User.findById(dataObject.userId)

        List<UserOrganismPermission> userOrganismPermissionList = UserOrganismPermission.findAllByUser(user)

        render userOrganismPermissionList as JSON
    }

    /**
     * Only changing one of the boolean permissions
     * @return
     */
    def updateOrganismPermission() {
        JSONObject dataObject = JSON.parse(params.data)
        log.debug "json data ${dataObject}"
        UserOrganismPermission userOrganismPermission = UserOrganismPermission.findById(dataObject.id)


        User user = User.findById(dataObject.userId)
        Organism organism = Organism.findByCommonName(dataObject.organism)
        log.debug "found ${userOrganismPermission}"
        if (!userOrganismPermission) {
            userOrganismPermission = UserOrganismPermission.findByUserAndOrganism(user, organism)
        }

        if (!userOrganismPermission) {
            log.debug "creating new permissions! "
            userOrganismPermission = new UserOrganismPermission(
                    user: User.findById(dataObject.userId)
                    , organism: Organism.findByCommonName(dataObject.organism)
                    , permissions: "[]"
            ).save(insert: true)
            log.debug "created new permissions! "
        }



        JSONArray permissionsArray = new JSONArray()
        if (dataObject.getBoolean(PermissionEnum.ADMINISTRATE.name())) {
            permissionsArray.add(PermissionEnum.ADMINISTRATE.name())
        }
        if (dataObject.getBoolean(PermissionEnum.WRITE.name())) {
            permissionsArray.add(PermissionEnum.WRITE.name())
        }
        if (dataObject.getBoolean(PermissionEnum.EXPORT.name())) {
            permissionsArray.add(PermissionEnum.EXPORT.name())
        }
        if (dataObject.getBoolean(PermissionEnum.READ.name())) {
            permissionsArray.add(PermissionEnum.READ.name())
        }


        userOrganismPermission.permissions = permissionsArray.toString()
        userOrganismPermission.save(flush: true)

        render userOrganismPermission as JSON

    }

}